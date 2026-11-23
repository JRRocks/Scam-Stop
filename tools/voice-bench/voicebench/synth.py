"""Candidate voices. Each one turns a text into mono samples and a sample rate."""

from __future__ import annotations

import glob
import os
import subprocess
import tempfile
import time
from dataclasses import dataclass

import numpy as np
import soundfile as sf


@dataclass(frozen=True)
class Take:
    samples: np.ndarray
    sample_rate: int
    seconds_to_synthesize: float


class PiperVoice:
    """Piper (VITS) voice run by sherpa-onnx 1.x, the last line that embeds the espeak-ng phonemizer."""

    def __init__(self, folder: str, speaker: int = 0, threads: int = 2):
        import sherpa_onnx

        model = sorted(glob.glob(os.path.join(folder, "*.onnx")))[0]
        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=model,
                    tokens=os.path.join(folder, "tokens.txt"),
                    data_dir=os.path.join(folder, "espeak-ng-data"),
                ),
                num_threads=threads,
            ),
            max_num_sentences=1,
        )
        self._tts = sherpa_onnx.OfflineTts(config)
        self._speaker = speaker
        self.model_bytes = os.path.getsize(model)

    def say(self, text: str, speed: float = 1.0) -> Take:
        start = time.perf_counter()
        audio = self._tts.generate(text, sid=self._speaker, speed=speed)
        return Take(np.asarray(audio.samples, dtype=np.float32), audio.sample_rate, time.perf_counter() - start)


class KokoroVoice:
    """Kokoro-82M run by sherpa-onnx; French goes through espeak-ng too."""

    def __init__(self, folder: str, speaker: int, threads: int = 2):
        import sherpa_onnx

        model = sorted(glob.glob(os.path.join(folder, "model*.onnx")))[0]
        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                    model=model,
                    voices=os.path.join(folder, "voices.bin"),
                    tokens=os.path.join(folder, "tokens.txt"),
                    data_dir=os.path.join(folder, "espeak-ng-data"),
                    lang="fr",
                ),
                num_threads=threads,
            ),
            max_num_sentences=1,
        )
        self._tts = sherpa_onnx.OfflineTts(config)
        self._speaker = speaker
        self.model_bytes = os.path.getsize(model) + os.path.getsize(os.path.join(folder, "voices.bin"))

    def say(self, text: str, speed: float = 1.0) -> Take:
        start = time.perf_counter()
        audio = self._tts.generate(text, sid=self._speaker, speed=speed)
        return Take(np.asarray(audio.samples, dtype=np.float32), audio.sample_rate, time.perf_counter() - start)


class CommandVoice:
    """Old-style synthesizers installed on the machine, kept as the "robot" reference."""

    def __init__(self, kind: str):
        assert kind in ("espeak", "pico"), kind
        self._kind = kind
        self.model_bytes = 0

    def say(self, text: str, speed: float = 1.0) -> Take:
        start = time.perf_counter()
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, "out.wav")
            if self._kind == "espeak":
                subprocess.run(["espeak-ng", "-v", "fr", "-s", str(int(160 * speed)), "-w", wav, text], check=True)
            else:
                subprocess.run(["pico2wave", "-l", "fr-FR", "-w", wav, text], check=True)
            samples, rate = sf.read(wav, dtype="float32")
        return Take(samples, rate, time.perf_counter() - start)
