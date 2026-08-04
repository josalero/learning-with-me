#!/usr/bin/env bash
# Run the deterministic token-audit scan on a Java project.
# Prints findings to the console AND writes Markdown + JSON reports to reports/.
# Usage: scripts/scan.sh [PROJECT_PATH] [FRAMEWORK]
#   PROJECT_PATH  defaults to the canonical example
#   FRAMEWORK     defaults to spring-ai
#   REPORT_DIR    env override for the output dir (default: <repo>/reports)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PROJECT="${1:-${EXAMPLE}}"
FRAMEWORK="${2:-spring-ai}"
REPORT_DIR="${REPORT_DIR:-${ROOT}/reports}"
NAME="$(basename "${PROJECT}")"

mkdir -p "${REPORT_DIR}"

gradlew -q :token-audit-cli:run --console=plain \
	--args="scan ${PROJECT} --framework ${FRAMEWORK} \
--out ${REPORT_DIR}/token-audit-${NAME}.md \
--out ${REPORT_DIR}/token-audit-${NAME}.json"
