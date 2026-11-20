#!/usr/bin/env bash
# API smoke test: creates a demo-mode audit against the bundled sample site and waits for the report.
# Usage: scripts/smoke-test.sh http://localhost   [target-url]
set -euo pipefail
BASE=${1:-http://localhost}
PUBLIC_HOST=$(curl -fs https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
TARGET=${2:-http://$PUBLIC_HOST/sample-site/index.html}
j() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }
echo "status: $(curl -fs $BASE/api/system/status)"
BID=$(curl -fs -XPOST $BASE/api/businesses -H 'content-type: application/json' \
  -d "{\"name\":\"Smoke Test Clinic\",\"websiteUrl\":\"$TARGET\",\"category\":\"Dental clinic\"}" | j "['id']")
AID=$(curl -fs -XPOST $BASE/api/audits -H 'content-type: application/json' -d "{\"businessId\":\"$BID\",\"mode\":\"DEMO\"}" | j "['id']")
curl -fs -XPOST $BASE/api/audits/$AID/generate-personas >/dev/null
for i in $(seq 1 30); do S=$(curl -fs $BASE/api/audits/$AID | j "['audit']['status']"); [ "$S" = PERSONAS_READY ] && break; sleep 2; done
curl -fs -XPOST $BASE/api/audits/$AID/start-testing >/dev/null
for i in $(seq 1 120); do
  S=$(curl -fs $BASE/api/audits/$AID/progress | j "['status']"); echo "  $S"
  case $S in REPORT_READY) break;; FAILED) curl -fs $BASE/api/audits/$AID/progress | j "['errorMessage']"; exit 1;; esac; sleep 5
done
curl -fs $BASE/api/audits/$AID/report | python3 -c "import json,sys;d=json.load(sys.stdin);print('score',d['audit']['overallScore'],'findings',len(d['findings']),d['stats'])"
echo "SMOKE TEST PASSED — open $BASE/audits/$AID"
