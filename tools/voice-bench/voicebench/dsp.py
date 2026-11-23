"""Treatment applied to the greeting before it is played into a phone line.

Prototype of what the app does in Kotlin (GreetingAudio): same steps, same constants.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

import numpy as np
from scipy import signal

from .synth import Take


@dataclass(frozen=True)
class Master:
    """Settings of the treatment. The defaults are the ones the bench retained."""

    low_cut_hz: float = 200.0  # small speakers and phone lines carry nothing below
    presence_db: float = 4.0  # consonants live above 2 kHz and are the first thing a line dulls
    presence_hz: float = 2000.0
    compress: bool = True  # even out loud and quiet syllables so the quiet ones clear the noise
    threshold_db: float = -12.0  # below the level of the speech itself
    ratio: float = 3.0
    attack_ms: float = 5.0
    release_ms: float = 120.0
    peak_db: float = -1.0
    lead_s: float = 0.4  # recording starts a moment after the beep
    tail_s: float = 0.5


def _high_shelf(f0: float, gain_db: float, sr: int) -> np.ndarray:
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / sr
    alpha = np.sin(w0) / 2 * np.sqrt(2.0)
    cos = np.cos(w0)
    b0 = a * ((a + 1) + (a - 1) * cos + 2 * np.sqrt(a) * alpha)
    b1 = -2 * a * ((a - 1) + (a + 1) * cos)
    b2 = a * ((a + 1) + (a - 1) * cos - 2 * np.sqrt(a) * alpha)
    a0 = (a + 1) - (a - 1) * cos + 2 * np.sqrt(a) * alpha
    a1 = 2 * ((a - 1) - (a + 1) * cos)
    a2 = (a + 1) - (a - 1) * cos - 2 * np.sqrt(a) * alpha
    return np.array([[b0 / a0, b1 / a0, b2 / a0, 1.0, a1 / a0, a2 / a0]])


def _speech_level_db(x: np.ndarray, sr: int) -> float:
    frame = max(1, int(0.02 * sr))
    n = len(x) // frame
    if n == 0:
        return -120.0
    power = (x[: n * frame].reshape(n, frame).astype(np.float64) ** 2).mean(axis=1)
    loud = power[power > power.max() * 10 ** (-35 / 10)]
    return 10 * np.log10(max(loud.mean(), 1e-12))


def _compress(x: np.ndarray, sr: int, m: Master) -> np.ndarray:
    """Feed-forward compressor; the threshold follows the level of the speech."""
    threshold = _speech_level_db(x, sr) + m.threshold_db
    attack = np.exp(-1.0 / (m.attack_ms * 1e-3 * sr))
    release = np.exp(-1.0 / (m.release_ms * 1e-3 * sr))
    out = np.empty_like(x)
    env = 0.0
    for i, sample in enumerate(x):
        level = abs(float(sample))
        coeff = attack if level > env else release
        env = coeff * env + (1 - coeff) * level
        env_db = 20 * np.log10(max(env, 1e-9))
        over = env_db - threshold
        gain_db = -over * (1 - 1 / m.ratio) if over > 0 else 0.0
        out[i] = sample * 10 ** (gain_db / 20)
    return out


def master(x: np.ndarray, sr: int, m: Master = Master()) -> np.ndarray:
    y = signal.sosfilt(signal.butter(2, m.low_cut_hz, "highpass", fs=sr, output="sos"), x.astype(np.float64))
    if m.presence_db:
        y = signal.sosfilt(_high_shelf(m.presence_hz, m.presence_db, sr), y)
    if m.compress:
        y = _compress(y, sr, m)
    peak = np.abs(y).max()
    if peak > 0:
        y = y * (10 ** (m.peak_db / 20) / peak)
    lead, tail = np.zeros(int(m.lead_s * sr)), np.zeros(int(m.tail_s * sr))
    return np.concatenate([lead, y, tail]).astype(np.float32)


_SENTENCE_END = re.compile(r"(?<=[.!?…])\s+")


def sentences(text: str) -> list[str]:
    return [part.strip() for part in _SENTENCE_END.split(text.strip()) if part.strip()]


def say_with_pauses(voice, text: str, speed: float = 1.0, pause_s: float = 0.45) -> Take:
    """One synthesis per sentence, with a fixed breath between them."""
    takes = [voice.say(sentence, speed) for sentence in sentences(text)]
    rate = takes[0].sample_rate
    gap = np.zeros(int(pause_s * rate), np.float32)
    parts: list[np.ndarray] = []
    for index, take in enumerate(takes):
        if index:
            parts.append(gap)
        parts.append(_trim(take.samples, rate))
    return Take(np.concatenate(parts), rate, sum(t.seconds_to_synthesize for t in takes))


def _trim(x: np.ndarray, sr: int, floor_db: float = -45.0) -> np.ndarray:
    """Drops the silence a voice leaves around a sentence, so pauses are the ones we choose."""
    limit = np.abs(x).max() * 10 ** (floor_db / 20)
    loud = np.flatnonzero(np.abs(x) > limit)
    if len(loud) == 0:
        return x
    margin = int(0.02 * sr)
    return x[max(0, loud[0] - margin) : loud[-1] + margin]
