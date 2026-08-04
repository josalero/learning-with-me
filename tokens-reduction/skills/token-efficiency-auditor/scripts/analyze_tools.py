#!/usr/bin/env python3
"""Heuristic scan for tool registration patterns in Java AI projects."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

PATTERNS = [
    (re.compile(r"\.tools\s*\("), ".tools("),
    (re.compile(r"@Tool\b"), "@Tool"),
    (re.compile(r"ToolCallback"), "ToolCallback"),
    (re.compile(r"FunctionCallback"), "FunctionCallback"),
    (re.compile(r"toolCallbacks?\s*\("), "toolCallbacks("),
    (re.compile(r"MethodToolCallbackProvider"), "MethodToolCallbackProvider"),
    (re.compile(r"\.defaultTools\s*\("), ".defaultTools("),
]


def scan(root: Path) -> list[str]:
    findings: list[str] = []
    for path in sorted(root.rglob("*.java")):
        if any(part in {".git", "build", "target", ".gradle"} for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        lines = text.splitlines()
        for lineno, line in enumerate(lines, start=1):
            for regex, label in PATTERNS:
                if regex.search(line):
                    findings.append(f"{path}:{lineno}: [{label}] {line.strip()}")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project_root", type=Path, nargs="?", default=Path("."))
    args = parser.parse_args()
    if not args.project_root.exists():
        print(f"error: path not found: {args.project_root}", file=sys.stderr)
        return 1

    findings = scan(args.project_root)
    if not findings:
        print("No Java tool-registration patterns found.")
        return 0

    print(f"Found {len(findings)} tool-related hit(s):\n")
    for f in findings:
        print(f)
    print(
        "\nReview whether every call site needs the full tool set. "
        "Prefer path-scoped / dynamic tool selection."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
