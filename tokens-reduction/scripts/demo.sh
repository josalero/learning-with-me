#!/usr/bin/env bash
# Run the offline example app, then audit it with the deterministic scan.
# Usage: scripts/demo.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "== run offline example app =="
gradlew -q :spring-ai-support-assistant:run --console=plain
echo
echo "== audit the example =="
"${ROOT}/scripts/scan.sh"
