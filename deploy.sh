#!/usr/bin/env bash
# One-command deploy / update on the VPS: builds images, starts the stack, runs a smoke test.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { echo "Missing .env. Run: cp .env.example .env  and fill it in."; exit 1; }
docker compose build
docker compose up -d
echo "Waiting for services to become healthy…"
for i in $(seq 1 60); do
  if curl -fs "http://localhost:${HTTP_PORT:-80}/api/system/status" >/dev/null 2>&1; then break; fi
  sleep 5
done
./scripts/smoke-test.sh "http://localhost:${HTTP_PORT:-80}"
