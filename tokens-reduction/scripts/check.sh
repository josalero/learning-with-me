#!/usr/bin/env bash
# Compile every module and run the full test suite (offline, no API key).
# Usage: scripts/check.sh [extra gradle args...]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

gradlew check --console=plain "$@"
