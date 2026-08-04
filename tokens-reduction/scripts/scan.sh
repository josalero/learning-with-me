#!/usr/bin/env bash
# Run the deterministic token-audit scan on a Java project.
# Prints findings to the console AND writes Markdown + JSON + HTML reports to reports/.
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

# FORCE_COLOR so make/script runners still show severity colors when stdout is not a TTY.
export FORCE_COLOR="${FORCE_COLOR:-1}"

gradlew -q :token-audit-cli:run --console=plain \
	--args="scan ${PROJECT} --framework ${FRAMEWORK} --color always \
--out ${REPORT_DIR}/token-audit-${NAME}.md \
--out ${REPORT_DIR}/token-audit-${NAME}.json \
--out ${REPORT_DIR}/token-audit-${NAME}.html"
