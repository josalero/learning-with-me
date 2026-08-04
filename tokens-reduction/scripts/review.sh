#!/usr/bin/env bash
# Deterministic scan plus an optional OpenRouter semantic review pass.
# Requires OPENROUTER_API_KEY in the environment. Not part of `make check`.
# Usage: scripts/review.sh [PROJECT_PATH] [FRAMEWORK]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

: "${OPENROUTER_API_KEY:?set OPENROUTER_API_KEY to run the semantic review}"

PROJECT="${1:-${EXAMPLE}}"
FRAMEWORK="${2:-spring-ai}"

gradlew -q :token-audit-cli:run --console=plain \
	--args="scan ${PROJECT} --framework ${FRAMEWORK} --llm-review"
