#!/usr/bin/env bash
# Mint a demo HS256 JWT for gateway profile "jwt".
# Usage: ./scripts/mint-demo-jwt.sh [audience] [tenant_id]
set -euo pipefail

SECRET="${GATEWAY_JWT_HMAC_SECRET:-local-demo-hmac-secret-change-me-32bytes}"
AUD="${1:-mcp-integration-gateway}"
TENANT="${2:-demo-tenant}"
NOW=$(date +%s)
EXP=$((NOW + 3600))

HEADER=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | openssl base64 -e -A | tr '+/' '-_' | tr -d '=')
PAYLOAD=$(printf '%s' "{\"sub\":\"jwt-demo-user\",\"aud\":\"${AUD}\",\"tenant_id\":\"${TENANT}\",\"roles\":[\"RECRUITER\"],\"scope\":\"tools.read tools.write\",\"iat\":${NOW},\"exp\":${EXP}}" \
  | openssl base64 -e -A | tr '+/' '-_' | tr -d '=')
SIG=$(printf '%s' "${HEADER}.${PAYLOAD}" \
  | openssl dgst -binary -sha256 -hmac "${SECRET}" \
  | openssl base64 -e -A | tr '+/' '-_' | tr -d '=')
printf '%s\n' "${HEADER}.${PAYLOAD}.${SIG}"
