#!/usr/bin/env python3
"""Reads test sentences with candidate voices, degrades them like a phone line, and
lets an automatic listener say how much it understood.

    python run_bench.py --models .cache/models --out out --voices siwis-medium,tom-medium

Appends one JSON line per (voice, variant, text, condition, seed, listener) to
<out>/rows.jsonl and saves the sounds of the first text under <out>/samples/.
A sentence already fully measured in <out>/rows.jsonl is skipped, so an interrupted
run picks up where it stopped.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

import numpy as np
import soundfile as sf

from voicebench import channel, dsp, listen, synth

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# id -> (label, constructor). Labels are what the preview page shows.
VOICES = {
    "siwis-medium": ("Piper « siwis » (femme), qualité moyenne", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-siwis-medium")),
    "siwis-medium-fp16": ("Piper « siwis », poids divisé par 2 (fp16)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-siwis-medium-fp16")),
    "siwis-medium-int8": ("Piper « siwis », poids divisé par 3 (int8)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-siwis-medium-int8")),
    "siwis-low": ("Piper « siwis » (femme), qualité basse", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-siwis-low")),
    "upmc-jessica": ("Piper « upmc » Jessica (femme)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-upmc-medium", speaker=0)),
    "upmc-pierre": ("Piper « upmc » Pierre (homme)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-upmc-medium", speaker=1)),
    "tom-medium": ("Piper « tom » (homme)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-tom-medium")),
    "tom-medium-fp16": ("Piper « tom », poids divisé par 2 (fp16)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-tom-medium-fp16")),
    "tom-medium-int8": ("Piper « tom », poids divisé par 3 (int8)", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-tom-medium-int8")),
    "gilles-low": ("Piper « gilles » (homme), qualité basse", lambda m: synth.PiperVoice(f"{m}/vits-piper-fr_FR-gilles-low")),
    "kokoro-siwis": ("Kokoro « ff_siwis » (femme)", lambda m: synth.KokoroVoice(f"{m}/kokoro-multi-lang-v1_0", speaker=30)),
    "kokoro-siwis-int8": ("Kokoro « ff_siwis », allégé (int8)", lambda m: synth.KokoroVoice(f"{m}/kokoro-int8-multi-lang-v1_0", speaker=30)),
    "espeak": ("eSpeak NG (voix robot de référence)", lambda m: synth.CommandVoice("espeak")),
    "pico": ("SVOX Pico (ancienne voix Android)", lambda m: synth.CommandVoice("pico")),
}

# How the sound is prepared before the line. "brut" is the voice as it comes out.
VARIANTS = {
    "brut": dict(speed=1.0, pauses=False, master=None),
    "pauses": dict(speed=1.0, pauses=True, master=None),
    "traite": dict(speed=1.0, pauses=True, master=dsp.Master()),
    "traite-lent": dict(speed=0.9, pauses=True, master=dsp.Master()),
    "traite-tres-lent": dict(speed=0.8, pauses=True, master=dsp.Master()),
    "lent": dict(speed=0.9, pauses=True, master=None),
    "traite-sans-compression": dict(speed=1.0, pauses=True, master=dsp.Master(compress=False)),
    "traite-sans-aigus": dict(speed=1.0, pauses=True, master=dsp.Master(presence_db=0.0)),
}


def default_greeting() -> str:
    """The app's own default text, so the bench follows a rewording."""
    xml = open(os.path.join(REPO, "app/src/main/res/values/strings.xml"), encoding="utf-8").read()
    value = re.search(r'<string name="default_greeting">(.*?)</string>', xml, re.S).group(1)
    return value.replace("\\'", "'").replace("\\n", " ").strip()


def load_texts(path: str) -> list[str]:
    lines = [line.strip() for line in open(path, encoding="utf-8") if line.strip()]
    return [default_greeting() if line == "@default_greeting" else line for line in lines]


def render(voice, text: str, variant: dict) -> synth.Take:
    take = dsp.say_with_pauses(voice, text, variant["speed"]) if variant["pauses"] else voice.say(text, variant["speed"])
    if variant["master"] is None:
        return take
    return synth.Take(dsp.master(take.samples, take.sample_rate, variant["master"]), take.sample_rate, take.seconds_to_synthesize)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--models", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--voices", default="siwis-medium")
    parser.add_argument("--variants", default="brut")
    parser.add_argument("--conditions", default="ligne,proche,courant,difficile")
    parser.add_argument("--listeners", default="turbo,small")
    parser.add_argument("--seeds", type=int, default=2)
    parser.add_argument("--texts", default=os.path.join(os.path.dirname(__file__), "texts.txt"))
    parser.add_argument("--threads", type=int, default=2)
    args = parser.parse_args()

    missing = [name for name, ok in channel.codecs_available().items() if not ok]
    if missing:
        print(f"ffmpeg lacks the encoders {missing}: install libavcodec-extra (see README.md).", file=sys.stderr)
        return 2

    texts = load_texts(args.texts)
    listeners = [listen.Listener(args.models, size, args.threads) for size in args.listeners.split(",")]
    conditions = [channel.CONDITIONS[key] for key in args.conditions.split(",") if key]
    samples_dir = os.path.join(args.out, "samples")
    os.makedirs(samples_dir, exist_ok=True)
    rows_path = os.path.join(args.out, "rows.jsonl")
    expected = len(listeners) * (1 + sum(args.seeds if c.acoustic else 1 for c in conditions))
    measured: dict = {}
    if os.path.exists(rows_path):
        for line in open(rows_path, encoding="utf-8"):
            row = json.loads(line)
            key = (row["voice"], row["variant"], row["text"])
            measured[key] = measured.get(key, 0) + 1

    with open(rows_path, "a", encoding="utf-8") as rows:

        def record(**row) -> None:
            rows.write(json.dumps(row, ensure_ascii=False) + "\n")
            rows.flush()

        for voice_id in args.voices.split(","):
            label, build = VOICES[voice_id]
            voice = build(args.models)
            for variant_id in args.variants.split(","):
                variant = VARIANTS[variant_id]
                for text_index, text in enumerate(texts):
                    if measured.get((voice_id, variant_id, text_index), 0) >= expected:
                        continue
                    take = render(voice, text, variant)
                    seconds = len(take.samples) / take.sample_rate
                    base = dict(
                        voice=voice_id, variant=variant_id, text=text_index, seconds=round(seconds, 2),
                        synth_seconds=round(take.seconds_to_synthesize, 2), model_bytes=voice.model_bytes,
                    )  # fmt: skip
                    stem = f"{voice_id}.{variant_id}"
                    if text_index == 0:
                        sf.write(os.path.join(samples_dir, f"{stem}.tel-quel.wav"), take.samples, take.sample_rate, subtype="PCM_16")
                    for listener in listeners:
                        heard = listener.transcribe(take.samples, take.sample_rate)
                        record(**base, condition="tel-quel", seed=0, listener=listener.size, heard=heard,
                               understood=round(listen.words_understood(text, heard), 3))  # fmt: skip
                    for condition in conditions:
                        for seed in range(args.seeds if condition.acoustic else 1):
                            received, rate = channel.simulate(take.samples, take.sample_rate, condition, seed)
                            if text_index == 0 and seed == 0:
                                sf.write(os.path.join(samples_dir, f"{stem}.{condition.key}.wav"), received, rate, subtype="PCM_16")
                            for listener in listeners:
                                heard = listener.transcribe(received, rate)
                                record(**base, condition=condition.key, seed=seed, listener=listener.size, heard=heard,
                                       understood=round(listen.words_understood(text, heard), 3))  # fmt: skip
                    print(f"{voice_id} {variant_id} text {text_index} done", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
