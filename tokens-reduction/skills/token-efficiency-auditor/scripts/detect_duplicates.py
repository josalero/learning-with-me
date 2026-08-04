#!/usr/bin/env python3
"""Find near-duplicate lines and repeated blocks in prompt-like text files."""

from __future__ import annotations

import argparse
import hashlib
import sys
from collections import defaultdict
from pathlib import Path

PROMPTISH = {".md", ".txt", ".prompt", ".st", ".ftl", ".yml", ".yaml", ".java", ".json"}


def normalize_line(line: str) -> str:
    return " ".join(line.strip().lower().split())


def block_hash(lines: list[str]) -> str:
    payload = "\n".join(normalize_line(l) for l in lines if normalize_line(l))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


def scan_file(path: Path, min_line_len: int, block_size: int) -> list[str]:
    findings: list[str] = []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as exc:
        return [f"skip {path}: {exc}"]

    line_map: dict[str, list[int]] = defaultdict(list)
    for idx, line in enumerate(lines, start=1):
        norm = normalize_line(line)
        if len(norm) >= min_line_len:
            line_map[norm].append(idx)

    for norm, idxs in line_map.items():
        if len(idxs) >= 2:
            preview = norm[:80]
            findings.append(f"{path}: duplicate line x{len(idxs)} at {idxs[:8]} — {preview!r}")

    block_map: dict[str, list[int]] = defaultdict(list)
    if len(lines) >= block_size:
        for start in range(0, len(lines) - block_size + 1):
            chunk = lines[start : start + block_size]
            if sum(1 for l in chunk if normalize_line(l)) < block_size // 2:
                continue
            block_map[block_hash(chunk)].append(start + 1)

    for h, starts in block_map.items():
        if len(starts) >= 2:
            findings.append(
                f"{path}: duplicate {block_size}-line block x{len(starts)} starting at {starts[:8]} (hash={h})"
            )
    return findings


def iter_files(root: Path) -> list[Path]:
    if root.is_file():
        return [root]
    out: list[Path] = []
    for p in root.rglob("*"):
        if not p.is_file() or p.suffix.lower() not in PROMPTISH:
            continue
        if any(part in {".git", "build", "node_modules", "target", ".gradle"} for part in p.parts):
            continue
        out.append(p)
    return sorted(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("--min-line-len", type=int, default=40)
    parser.add_argument("--block-size", type=int, default=5)
    args = parser.parse_args()
    if not args.path.exists():
        print(f"error: path not found: {args.path}", file=sys.stderr)
        return 1

    all_findings: list[str] = []
    for file in iter_files(args.path):
        all_findings.extend(scan_file(file, args.min_line_len, args.block_size))

    if not all_findings:
        print("No duplicate lines/blocks found (heuristic).")
        return 0
    for f in all_findings:
        print(f)
    print(f"\n{len(all_findings)} finding(s). Review prompts for duplicated instructions.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
