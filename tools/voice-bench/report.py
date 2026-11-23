#!/usr/bin/env python3
"""Turns the rows written by run_bench.py into a table: share of words understood,
per voice and variant (lines) and per condition (columns), one table per listener.

    python report.py out/rows.jsonl [--json summary.json]
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict

ORDER = ["tel-quel", "ligne", "proche", "courant", "difficile"]


def summarize(path: str) -> dict:
    cells: dict = defaultdict(list)
    meta: dict = {}
    for line in open(path, encoding="utf-8"):
        row = json.loads(line)
        key = (row["listener"], row["voice"], row["variant"])
        cells[key + (row["condition"],)].append(row["understood"])
        info = meta.setdefault(key[1:], {"seconds": [], "synth": [], "model_bytes": row["model_bytes"]})
        if row["condition"] == "tel-quel" and row["listener"] == key[0]:
            info["seconds"].append(row["seconds"])
            info["synth"].append(row["synth_seconds"])
    summary: dict = {}
    for (listener, voice, variant, condition), values in cells.items():
        entry = summary.setdefault(listener, {}).setdefault(f"{voice}|{variant}", {})
        entry[condition] = {"mean": round(sum(values) / len(values), 3), "min": min(values), "n": len(values)}
    for (voice, variant), info in meta.items():
        seconds, synth = sum(info["seconds"]), sum(info["synth"])
        for listener in summary:
            entry = summary[listener].get(f"{voice}|{variant}")
            if entry is not None:
                entry["_meta"] = {
                    "model_megabytes": round(info["model_bytes"] / 1e6, 1),
                    "real_time_factor": round(synth / seconds, 3) if seconds else None,
                }
    return summary


def markdown(summary: dict) -> str:
    out = []
    for listener, voices in summary.items():
        out.append(f"### Auditeur automatique : Whisper {listener}\n")
        out.append("| Voix · préparation | " + " | ".join(ORDER) + " | Mo | temps de calcul / durée |")
        out.append("|---|" + "---:|" * (len(ORDER) + 2))
        ranked = sorted(voices.items(), key=lambda item: -sum(item[1].get(c, {}).get("mean", 0) for c in ORDER[1:]))
        for name, entry in ranked:
            cols = [f"{entry[c]['mean'] * 100:.0f} %" if c in entry else "–" for c in ORDER]
            meta = entry.get("_meta", {})
            out.append(f"| {name.replace('|', ' · ')} | " + " | ".join(cols) + f" | {meta.get('model_megabytes', '–')} | {meta.get('real_time_factor', '–')} |")
        out.append("")
    return "\n".join(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("rows")
    parser.add_argument("--json")
    args = parser.parse_args()
    summary = summarize(args.rows)
    if args.json:
        with open(args.json, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, ensure_ascii=False, indent=1)
    print(markdown(summary))


if __name__ == "__main__":
    main()
