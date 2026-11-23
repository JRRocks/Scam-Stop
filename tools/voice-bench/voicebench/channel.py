"""Simulated path from a greeting file to the caller's ear.

    file -> loudspeaker -> room -> ambient noise -> phone microphone
         -> uplink codec -> voicemail storage (G.711 A-law, 300-3400 Hz)
         -> downlink codec -> caller

Every parameter below is an assumption, not a measurement of a real phone or of a
real operator platform: see README.md for what the simulation leaves out.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass, field
from fractions import Fraction

import numpy as np
from scipy import signal

FFMPEG = os.environ.get("VOICEBENCH_FFMPEG") or shutil.which("ffmpeg") or "ffmpeg"

_AMR_NB_MODES = {4.75, 5.15, 5.9, 6.7, 7.4, 7.95, 10.2, 12.2}
_AMR_WB_MODES = {6.6, 8.85, 12.65, 14.25, 15.85, 18.25, 19.85, 23.05, 23.85}


def resample(x: np.ndarray, sr_in: int, sr_out: int) -> np.ndarray:
    if sr_in == sr_out:
        return x.astype(np.float32)
    ratio = Fraction(sr_out, sr_in)
    return signal.resample_poly(x, ratio.numerator, ratio.denominator).astype(np.float32)


def active_level_db(x: np.ndarray, sr: int) -> float:
    """Level of the speech itself, pauses left out (rough stand-in for ITU-T P.56)."""
    frame = max(1, int(0.02 * sr))
    n = len(x) // frame
    if n == 0:
        return -120.0
    power = (x[: n * frame].reshape(n, frame).astype(np.float64) ** 2).mean(axis=1)
    loud = power[power > power.max() * 10 ** (-35 / 10)]
    return 10 * np.log10(max(loud.mean(), 1e-12))


def set_active_level(x: np.ndarray, sr: int, target_db: float) -> np.ndarray:
    gain = 10 ** ((target_db - active_level_db(x, sr)) / 20)
    return np.clip(x * gain, -1.0, 1.0).astype(np.float32)


def _peaking(f0: float, gain_db: float, q: float, sr: int) -> np.ndarray:
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / sr
    alpha = np.sin(w0) / (2 * q)
    b = [1 + alpha * a, -2 * np.cos(w0), 1 - alpha * a]
    den = [1 + alpha / a, -2 * np.cos(w0), 1 - alpha / a]
    return np.array([[b[0] / den[0], b[1] / den[0], b[2] / den[0], 1.0, den[1] / den[0], den[2] / den[0]]])


def loudspeaker(x: np.ndarray, sr: int, low_cut: float, drive: float) -> np.ndarray:
    """Small speaker played as loud as it goes: no bass, resonances, soft clipping."""
    y = x / max(np.abs(x).max(), 1e-9)
    y = signal.sosfilt(signal.butter(2, low_cut, "highpass", fs=sr, output="sos"), y)
    y = signal.sosfilt(_peaking(1100.0, 4.0, 1.5, sr), y)
    y = signal.sosfilt(_peaking(3200.0, 3.0, 2.0, sr), y)
    y = y / max(np.abs(y).max(), 1e-9)
    if drive > 0:
        y = np.tanh(drive * y) / np.tanh(drive)
    return y.astype(np.float32)


def room(x: np.ndarray, sr: int, rt60: float, distance: float) -> np.ndarray:
    """Shoebox room (image-source model): the microphone sits `distance` m from the speaker."""
    import pyroomacoustics as pra

    dims = [4.2, 3.4, 2.5]
    absorption, max_order = pra.inverse_sabine(rt60, dims)
    shoebox = pra.ShoeBox(dims, fs=sr, materials=pra.Material(absorption), max_order=min(max_order, 30))
    source = np.array([1.9, 1.4, 0.9])
    shoebox.add_source(source.tolist())
    shoebox.add_microphone((source + np.array([distance, 0.0, 0.05])).tolist())
    shoebox.compute_rir()
    rir = np.asarray(shoebox.rir[0][0])
    y = signal.fftconvolve(x, rir)[: len(x) + int(0.3 * sr)]
    # Keep the loudspeaker's level reference: the direct sound stays at its input level.
    return (y / max(np.abs(rir).max(), 1e-9)).astype(np.float32)


def hoth_noise(n: int, sr: int, rng: np.random.Generator) -> np.ndarray:
    """Room noise with the Hoth spectrum (about -5 dB per octave above 100 Hz)."""
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / sr)
    shape = np.where(freqs < 100, 1.0, (np.maximum(freqs, 100) / 100) ** (-0.83))
    noise = np.fft.irfft(spectrum * shape, n)
    return (noise / np.sqrt((noise**2).mean())).astype(np.float32)


def add_noise(x: np.ndarray, sr: int, noise_db: float, rng: np.random.Generator) -> np.ndarray:
    """Noise at a fixed level below the loudspeaker's peak, so a louder file gets a better ratio."""
    return (x + hoth_noise(len(x), sr, rng) * 10 ** (noise_db / 20)).astype(np.float32)


def microphone(x: np.ndarray, sr: int) -> np.ndarray:
    """Phone microphone and gain control: low cut, then the standard -26 dBov speech level."""
    y = signal.sosfilt(signal.butter(2, 120.0, "highpass", fs=sr, output="sos"), x)
    return set_active_level(y, sr, -26.0)


def narrowband(x: np.ndarray, sr: int = 8000) -> np.ndarray:
    """Telephone band, 300-3400 Hz."""
    y = signal.sosfilt(signal.butter(4, 300.0, "highpass", fs=sr, output="sos"), x)
    y = signal.sosfilt(signal.butter(6, 3400.0, "lowpass", fs=sr, output="sos"), y)
    return y.astype(np.float32)


def g711_alaw(x: np.ndarray) -> np.ndarray:
    """8-bit A-law companding, as stored by classic voicemail platforms."""
    a = 87.6
    mag = np.clip(np.abs(x), 0.0, 1.0)
    comp = np.where(mag < 1 / a, a * mag / (1 + np.log(a)), (1 + np.log(np.maximum(a * mag, 1e-12))) / (1 + np.log(a)))
    quant = (np.floor(comp * 127.0) + 0.5) / 127.0
    expanded = np.where(quant < 1 / (1 + np.log(a)), quant * (1 + np.log(a)) / a, np.exp(quant * (1 + np.log(a)) - 1) / a)
    return (np.sign(x) * expanded).astype(np.float32)


def _codec(x: np.ndarray, sr: int, encoder: str, kbps: float) -> np.ndarray:
    with tempfile.TemporaryDirectory() as tmp:
        raw, coded, out = (os.path.join(tmp, name) for name in ("in.raw", "coded.amr", "out.raw"))
        (np.clip(x, -1.0, 1.0) * 32767.0).astype("<i2").tofile(raw)
        common = [FFMPEG, "-hide_banner", "-loglevel", "error", "-y"]
        subprocess.run(
            common + ["-f", "s16le", "-ar", str(sr), "-ac", "1", "-i", raw, "-c:a", encoder, "-b:a", f"{kbps}k", "-f", "amr", coded],
            check=True,
        )
        subprocess.run(common + ["-i", coded, "-f", "s16le", "-ar", str(sr), "-ac", "1", out], check=True)
        return np.fromfile(out, dtype="<i2").astype(np.float32) / 32768.0


def amr_nb(x: np.ndarray, kbps: float = 12.2) -> np.ndarray:
    """Narrowband call codec (2G/3G, and 4G calls to a narrowband platform). Input at 8 kHz."""
    assert kbps in _AMR_NB_MODES, kbps
    return _codec(x, 8000, "libopencore_amrnb", kbps)


def amr_wb(x: np.ndarray, kbps: float = 12.65) -> np.ndarray:
    """Wideband call codec (4G "HD voice"). Input at 16 kHz."""
    assert kbps in _AMR_WB_MODES, kbps
    return _codec(x, 16000, "libvo_amrwbenc", kbps)


def codecs_available() -> dict[str, bool]:
    listing = subprocess.run([FFMPEG, "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
    return {"amr_nb": "libopencore_amrnb" in listing, "amr_wb": "libvo_amrwbenc" in listing}


@dataclass(frozen=True)
class Condition:
    """One named path. `acoustic=False` is the ideal case: the file reaches the platform untouched."""

    key: str
    label: str
    acoustic: bool = True
    speaker_low_cut: float = 350.0
    speaker_drive: float = 1.0
    rt60: float = 0.45
    distance: float = 0.25
    noise_db: float = -40.0
    uplink: str = "amr_wb"  # amr_wb (4G call) or amr_nb (3G call)
    downlink_kbps: float = 12.2
    notes: str = field(default="", compare=False)


CONDITIONS = {
    c.key: c
    for c in [
        Condition("ligne", "Ligne seule (dépôt numérique idéal)", acoustic=False),
        Condition("proche", "Haut-parleur collé au micro, pièce calme", speaker_drive=0.8, rt60=0.35, distance=0.06, noise_db=-48.0),
        Condition("courant", "Haut-parleur à 25 cm, pièce ordinaire"),
        Condition(
            "difficile",
            "Petit haut-parleur poussé, à 60 cm, pièce bruyante, mauvais réseau",
            speaker_low_cut=500.0,
            speaker_drive=2.5,
            rt60=0.7,
            distance=0.6,
            noise_db=-30.0,
            uplink="amr_nb",
            downlink_kbps=4.75,
        ),
    ]
}


def simulate(x: np.ndarray, sr: int, condition: Condition, seed: int = 0) -> tuple[np.ndarray, int]:
    """Returns what the caller's phone receives, at 8 kHz."""
    rng = np.random.default_rng(seed)
    y = resample(x, sr, 16000)
    if condition.acoustic:
        y = loudspeaker(y, 16000, condition.speaker_low_cut, condition.speaker_drive)
        y = room(y, 16000, condition.rt60, condition.distance)
        y = add_noise(y, 16000, condition.noise_db, rng)
        y = microphone(y, 16000)
        if condition.uplink == "amr_wb":
            y = resample(amr_wb(y), 16000, 8000)
        else:
            y = amr_nb(resample(y, 16000, 8000))
    else:
        y = set_active_level(resample(y, 16000, 8000), 8000, -26.0)
    y = g711_alaw(narrowband(y))
    return amr_nb(y, condition.downlink_kbps), 8000
