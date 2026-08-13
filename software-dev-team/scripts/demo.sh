#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FEATURE='Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create.'
BASE="${SDLC_URL:-http://localhost:8095}"

"$ROOT/scripts/seed-workspace.sh" users-service-java
"$ROOT/scripts/seed-workspace.sh" users-service-node

if ! curl -fsS "$BASE/actuator/health" >/dev/null; then
  echo "App is not running at $BASE." >&2
  echo "Start it with: SDLC_OFFLINE=true ./gradlew :sdlc-app:bootRun" >&2
  echo "Or: SDLC_OFFLINE=true docker compose up --build" >&2
  exit 1
fi

run_project() {
  local project="$1"
  local payload
  payload="$(printf '{"teamId":"default-scrum-team","projectId":"%s","featureRequest":%s}' "$project" "$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$FEATURE")")"
  local response
  response="$(curl -sS -X POST "$BASE/api/v1/runs" -H 'Content-Type: application/json' -d "$payload")"
  local run_id
  run_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["runId"])' <<<"$response")"
  echo "Started $project run $run_id"
  local deadline=$((SECONDS + 600))
  while (( SECONDS < deadline )); do
    local status
    status="$(curl -sS "$BASE/api/v1/runs/$run_id" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')"
    echo "  $project status=$status"
    case "$status" in
      COMPLETED|ESCALATED|FAILED) echo "Artifacts: $ROOT/runs/$run_id"; return 0 ;;
    esac
    sleep 5
  done
  echo "Timed out waiting for $project run $run_id" >&2
  return 1
}

run_project users-service-java
run_project users-service-node
echo "Demo finished. Same feature, two stacks — technology is YAML."
