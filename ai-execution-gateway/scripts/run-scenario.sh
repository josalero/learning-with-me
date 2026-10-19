#!/usr/bin/env bash
# Runs one seeded scenario against a local gateway.
# Requires docker compose (postgres) and the app on $GATEWAY_URL.
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
  1|2) key="${DEMO_RECRUITER_API_KEY:?Set DEMO_RECRUITER_API_KEY}" ;;
  3) key="${DEMO_ATS_API_KEY:?Set DEMO_ATS_API_KEY}" ;;
  *)
    echo "usage: scripts/run-scenario.sh 1|2|3" >&2
    exit 1
    ;;
esac

case "$scenario" in
  1) candidate="C-101" ;;
  2) candidate="C-102" ;;
  3) candidate="C-103" ;;
esac

create=$(curl -sS -D - -o /tmp/aigateway-create.json \
  -X POST "$base/api/v1/executions" \
  -H "X-API-Key: $key" \
  -H "Content-Type: application/json" \
  -d "{\"agentId\":\"screening-agent\",\"taskType\":\"CANDIDATE_EVALUATION\",\"candidateId\":\"$candidate\",\"requisitionId\":\"R-7\"}")
echo "$create" | head -n 1
execution_id=$(python3 -c 'import json; print(json.load(open("/tmp/aigateway-create.json"))["executionId"])')
echo "executionId=$execution_id"

status=""
for _ in $(seq 1 30); do
  body=$(curl -sS "$base/api/v1/executions/$execution_id" -H "X-API-Key: $key")
  status=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["status"])' "$body")
  case "$status" in
    COMPLETED|BUDGET_EXHAUSTED|FAILED) break ;;
  esac
  sleep 1
done

echo "$body" | python3 -m json.tool
curl -sS "$base/api/v1/executions/$execution_id/reconstruction" -H "X-API-Key: $key" | python3 -m json.tool

if [[ "$scenario" == "1" && "$status" == "COMPLETED" ]]; then
  request_id=$(docker compose exec -T postgres psql -U aigateway -d aigateway -tAc \
    "select id from transition_request where execution_id = '$execution_id' and status = 'PENDING' limit 1")
  echo "approving $request_id"
  curl -sS -X POST "$base/api/v1/transition-requests/$request_id/approve" -H "X-API-Key: $key" | python3 -m json.tool
fi
