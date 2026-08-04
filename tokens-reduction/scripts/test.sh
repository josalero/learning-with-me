#!/usr/bin/env bash
# Force-rerun the example scan regression test against the current example source.
# Usage: scripts/test.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

gradlew :token-audit-core:test \
	--tests '*SpringAiSupportAssistantScanTest*' \
	--rerun-tasks --console=plain
