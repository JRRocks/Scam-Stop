"""Automatic listener: transcribes a sound with Whisper and compares it with the text read."""

from __future__ import annotations

import os
import re
import unicodedata

import numpy as np

from .channel import resample

_NUMBERS = {
    "zéro": "0", "un": "1", "deux": "2", "trois": "3", "quatre": "4", "cinq": "5",
    "six": "6", "sept": "7", "huit": "8", "neuf": "9", "dix": "10",
}  # fmt: skip


def normalize(text: str) -> str:
    """Lower case, no punctuation, one spelling for what sounds the same."""
    text = unicodedata.normalize("NFC", text).lower().replace("œ", "oe")
    text = re.sub(r"[’'`´]", "'", text)
    text = re.sub(r"\bs\.?\s?m\.?\s?s\b", "sms", text)
    text = re.sub(r"[^0-9a-zàâäçéèêëîïôöùûüÿ' -]", " ", text)
    text = re.sub(r"[-']", " ", text)
    words = [_NUMBERS.get(word, word) for word in text.split()]
    return " ".join(words)


def word_error_rate(reference: str, heard: str) -> float:
    """Share of the words of `reference` that were missed, changed or added (0 = all understood)."""
    ref, hyp = normalize(reference).split(), normalize(heard).split()
    if not ref:
        return 0.0 if not hyp else 1.0
    previous = list(range(len(hyp) + 1))
    for i, ref_word in enumerate(ref, 1):
        current = [i] + [0] * len(hyp)
        for j, hyp_word in enumerate(hyp, 1):
            current[j] = min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ref_word != hyp_word))
        previous = current
    return previous[-1] / len(ref)


def words_understood(reference: str, heard: str) -> float:
    """1 - word error rate, floored at 0."""
    return max(0.0, 1.0 - word_error_rate(reference, heard))


class Listener:
    """Whisper through sherpa-onnx. Greedy decoding: the same sound always gives the same text."""

    def __init__(self, models_dir: str, size: str, threads: int = 4):
        import sherpa_onnx

        folder = os.path.join(models_dir, f"sherpa-onnx-whisper-{size}")
        self.size = size
        self._recognizer = sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=os.path.join(folder, f"{size}-encoder.int8.onnx"),
            decoder=os.path.join(folder, f"{size}-decoder.int8.onnx"),
            tokens=os.path.join(folder, f"{size}-tokens.txt"),
            language="fr",
            task="transcribe",
            num_threads=threads,
            tail_paddings=300,  # 3 s of padding instead of a full 30 s window: same text, much faster
        )

    def transcribe(self, x: np.ndarray, sr: int) -> str:
        # Whisper drops the last word when the sound stops dead: give it a second of silence.
        y = np.concatenate([np.zeros(4800, np.float32), resample(x, sr, 16000), np.zeros(16000, np.float32)])
        stream = self._recognizer.create_stream()
        stream.accept_waveform(16000, y)
        self._recognizer.decode_stream(stream)
        return stream.result.text.strip()
