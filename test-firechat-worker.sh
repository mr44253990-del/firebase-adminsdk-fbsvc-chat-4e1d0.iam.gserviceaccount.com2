#!/usr/bin/env bash
set -euo pipefail
WORKER_URL="${WORKER_URL:-https://solitary-hill-dcdc.mr44253990.workers.dev/}"

echo "== Health check =="
curl -fsS "$WORKER_URL" | python3 -m json.tool

if [[ -z "${FIREBASE_ID_TOKEN:-}" || -z "${TARGET_UID:-}" ]]; then
  cat <<'MSG'

Health check complete.
To send a real authenticated test, set FIREBASE_ID_TOKEN and TARGET_UID:
  export FIREBASE_ID_TOKEN='a current signed-in Firebase user's ID token'
  export TARGET_UID='recipient Firebase UID'
  ./test-firechat-worker.sh

The easier test is Admin → Service → Test gateway → Send test notification to this device.
MSG
  exit 0
fi

PAYLOAD=$(python3 - <<'PY'
import json,os,time
print(json.dumps({
  "targetUid": os.environ["TARGET_UID"],
  "title": "FireChat Worker Test",
  "body": "Authenticated direct FCM v1 notification is working",
  "senderId": os.environ.get("SENDER_UID", os.environ["TARGET_UID"]),
  "senderName": "FireChat Diagnostics",
  "senderProfileUrl": "",
  "notificationType": "gateway_test",
  "targetId": f"curl_{int(time.time()*1000)}"
}))
PY
)

echo "== Authenticated FCM test =="
curl -fsS -X POST "$WORKER_URL" \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary "$PAYLOAD" | python3 -m json.tool
