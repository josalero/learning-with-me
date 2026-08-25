#!/usr/bin/env bash
# Regenerates the article diagrams as PNGs from the .mmd sources in this folder.
set -euo pipefail

cd "$(dirname "$0")"

for source in *.mmd; do
  npx -y @mermaid-js/mermaid-cli \
    -i "$source" \
    -o "${source%.mmd}.png" \
    -c mermaid-theme.json \
    -b "#fffef9" \
    -s 3
done
