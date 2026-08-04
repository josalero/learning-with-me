#!/usr/bin/env python3
"""Rough token estimate for files or directories (heuristic unless tiktoken is installed)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TEXT_SUFFIXES = {
    ".java",
    ".kt",
    ".md",
    ".txt",
    ".yml",
    ".yaml",
    ".json",
    ".xml",
    ".properties",
    ".prompt",
    ".st",
    ".ftl",
}


def estimate_tokens(text: str) -> tuple[int, str]:
    try:
        import tiktoken  # type: ignore

        enc = tiktoken.get_encoding("cl100k_base")
        return len(enc.encode(text)), "tiktoken/cl100k_base"
    except Exception:
        # Common heuristic: ~4 characters per token for English/code mix
        return max(1, (len(text) + 3) // 4) if text else 0, "chars/4"


def iter_files(path: Path) -> list[Path]:
    if path.is_file():
        return [path]
    files: list[Path] = []
    for p in path.rglob("*"):
        if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES:
            if any(part in {".git", "build", "node_modules", "target", ".gradle"} for part in p.parts):
                continue
            files.append(p)
    return sorted(files)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path, help="File or directory to estimate")
    args = parser.parse_args()
    if not args.path.exists():
        print(f"error: path not found: {args.path}", file=sys.stderr)
        return 1

    total = 0
    method = "chars/4"
    for file in iter_files(args.path):
        try:
            text = file.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            print(f"skip {file}: {exc}", file=sys.stderr)
            continue
        tokens, method = estimate_tokens(text)
        total += tokens
        print(f"{tokens:>8}  {file}")

    print(f"{'—' * 40}")
    print(f"{total:>8}  TOTAL ({method})")
    print("Note: estimates are heuristic unless tiktoken is installed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
