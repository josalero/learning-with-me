#!/usr/bin/env bash
# Builds and starts Postgres, Redis, telemetry, and the gateway.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  cp .env.example .env
fi
docker compose up --build -d
