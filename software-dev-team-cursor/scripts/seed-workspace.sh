#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ID="${1:-}"
if [[ -z "$ID" ]]; then
  echo "Usage: $0 <seed-id>" >&2
  echo "Example: $0 users-service-java" >&2
  exit 1
fi

SEED="$ROOT/seeds/$ID"
DEST="$ROOT/workspace/$ID"

if [[ ! -d "$SEED" ]]; then
  echo "Seed not found: $SEED" >&2
  echo "Available:" >&2
  ls -1 "$ROOT/seeds" >&2
  exit 1
fi

mkdir -p "$ROOT/workspace"
rm -rf "$DEST"
cp -R "$SEED" "$DEST"
# do not copy nested build or node_modules if they leaked into the seed
rm -rf "$DEST/build" "$DEST/.gradle" "$DEST/node_modules"

cd "$DEST"
git init -q
git config user.email "sdlc-bot@local"
git config user.name "SDLC Bot"
git add -A
git commit -q -m "chore: seed $ID"
echo "Seeded $DEST"
