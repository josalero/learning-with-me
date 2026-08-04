#!/usr/bin/env bash
# Point Cursor and Claude skill dirs at the canonical Codex skills/ bundle via symlinks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKILL_ID="token-efficiency-auditor"
SRC="${ROOT}/skills/${SKILL_ID}"

if [[ ! -f "${SRC}/SKILL.md" ]]; then
  echo "error: missing canonical skill at ${SRC}" >&2
  exit 1
fi

link_one() {
  local dest="$1"
  mkdir -p "$(dirname "${dest}")"
  rm -rf "${dest}"
  ln -s "../../skills/${SKILL_ID}" "${dest}"
  echo "linked → ${dest} -> ../../skills/${SKILL_ID}"
}

link_one "${ROOT}/.cursor/skills/${SKILL_ID}"
link_one "${ROOT}/.claude/skills/${SKILL_ID}"

echo "done. Edit only skills/${SKILL_ID}/ — no copy step needed."
