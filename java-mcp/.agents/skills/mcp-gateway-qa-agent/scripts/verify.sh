#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../../../.." && pwd)"
verification_mode="${1:-fast}"

if [[ "$verification_mode" != "fast" && "$verification_mode" != "full" ]]; then
  echo "usage: $0 [fast|full]" >&2
  exit 2
fi

cd "$repo_dir"

echo "Running Gradle tests and local publication checks"
./gradlew test publishToMavenLocal

if [[ "$verification_mode" == "fast" ]]; then
  echo "PASS: fast gateway verification completed"
  exit 0
fi

echo "Validating and starting the packaged stack"
docker compose config >/dev/null
docker compose up --build --force-recreate -d

gateway_container="$(docker compose ps -q gateway)"
if [[ -z "$gateway_container" ]]; then
  echo "FAIL: gateway container was not created" >&2
  exit 1
fi

gateway_health=""
for _ in $(seq 1 45); do
  gateway_health="$(docker inspect --format='{{.State.Health.Status}}' "$gateway_container" 2>/dev/null || true)"
  if [[ "$gateway_health" == "healthy" ]]; then
    break
  fi
  sleep 2
done

if [[ "$gateway_health" != "healthy" ]]; then
  docker compose ps
  docker compose logs --tail=120 gateway
  echo "FAIL: gateway did not become healthy" >&2
  exit 1
fi

evidence_dir="$(mktemp -d "${TMPDIR:-/tmp}/mcp-gateway-qa.XXXXXX")"
trap 'rm -f "$evidence_dir/health.json" "$evidence_dir/catalog.json" "$evidence_dir/client.log"; rmdir "$evidence_dir" 2>/dev/null || true' EXIT

curl -fsS http://127.0.0.1:8080/actuator/health >"$evidence_dir/health.json"
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog >"$evidence_dir/catalog.json"
grep -q '"status":"UP"' "$evidence_dir/health.json"
grep -q '"connector":"openapi"' "$evidence_dir/catalog.json"
grep -q '"connector":"sql"' "$evidence_dir/catalog.json"

docker compose --profile client run --rm --no-deps mcp-client 2>&1 | tee "$evidence_dir/client.log"

required_checks=(
  "output has no PII field names"
  "quota exceeded after"
  "write approved and executed"
  "write declined without downstream mutation"
  "outbound bearer protected call succeeded"
  "SQL tool returned projected inventory rows"
)

for required_check in "${required_checks[@]}"; do
  if ! grep -q "$required_check" "$evidence_dir/client.log"; then
    echo "FAIL: missing client evidence: $required_check" >&2
    exit 1
  fi
done

echo "PASS: full gateway verification completed"
