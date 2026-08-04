#!/usr/bin/env bash
# Remove Gradle build outputs.
# Usage: scripts/clean.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

gradlew clean --console=plain
