#!/usr/bin/env bash
# Runs one seeded scenario against the gateway started by scripts/up.sh.
#
#   scripts/run-scenario.sh 1   Alice evaluates C-101, then approves the pending transition
#   scripts/run-scenario.sh 2   prompt injection on C-102; two tool calls are denied
#   scripts/run-scenario.sh 3   nightly ATS batch on C-103; stops at the tool-call budget
#
# Needs curl and python3 (for JSON parsing). Keys come from .env.
# Scenario 1 moves C-101 to INTERVIEW. To run it again from SCREENING, reset the data:
#   docker compose down -v && scripts/up.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

scenario="${1:-}"
base="${GATEWAY_URL:-http://localhost:8080}"

case "$scenario" in
  1) key="${DEMO_RECRUITER_API_KEY:?Set DEMO_RECRUITER_API_KEY}"; candidate="C-101" ;;
  2) key="${DEMO_RECRUITER_API_KEY:?Set DEMO_RECRUITER_API_KEY}"; candidate="C-102" ;;
  3) key="${DEMO_ATS_API_KEY:?Set DEMO_ATS_API_KEY}"; candidate="C-103" ;;
  *)
    echo "usage: scripts/run-scenario.sh 1|2|3" >&2
    exit 1
    ;;
esac

json_field() {
  python3 -c 'import json,sys; print(json.loads(sys.argv[1])'"$2"')' "$1"
}

pretty() {
  python3 -m json.tool
}

echo "== Start evaluation of $candidate"
created=$(curl -sS -w '\n%{http_code}' -X POST "$base/api/v1/executions" \
  -H "X-API-Key: $key" \
  -H "Content-Type: application/json" \
  -d "{\"agentId\":\"screening-agent\",\"taskType\":\"CANDIDATE_EVALUATION\",\"candidateId\":\"$candidate\",\"requisitionId\":\"R-7\"}")
http_code=$(tail -n 1 <<<"$created")
body=$(sed '$d' <<<"$created")
if [[ "$http_code" != "202" ]]; then
  echo "expected 202, got $http_code: $body" >&2
  exit 1
fi
execution_id=$(json_field "$body" '["executionId"]')
echo "executionId=$execution_id"

echo "== Poll until the run finishes"
status=""
for _ in $(seq 1 30); do
  body=$(curl -sS "$base/api/v1/executions/$execution_id" -H "X-API-Key: $key")
  status=$(json_field "$body" '["status"]')
  echo "status=$status"
  case "$status" in
    COMPLETED|BUDGET_EXHAUSTED|FAILED) break ;;
  esac
  sleep 1
done
echo "$body" | pretty

echo "== Reconstruction (who acted, every step, totals, verdict)"
curl -sS "$base/api/v1/executions/$execution_id/reconstruction" -H "X-API-Key: $key" | pretty

if [[ "$scenario" == "1" && "$status" == "COMPLETED" ]]; then
  echo "== Pending transition requests (the agent asked; nothing changed yet)"
  requests=$(curl -sS "$base/api/v1/executions/$execution_id/transition-requests" -H "X-API-Key: $key")
  echo "$requests" | pretty
  request_id=$(json_field "$requests" '[0]["transitionRequestId"]')
  echo "== Alice approves $request_id"
  curl -sS -X POST "$base/api/v1/transition-requests/$request_id/approve" -H "X-API-Key: $key" | pretty
fi
