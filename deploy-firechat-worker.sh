#!/usr/bin/env bash
set -euo pipefail

: "${CLOUDFLARE_API_TOKEN:?Set CLOUDFLARE_API_TOKEN in your terminal; never put it in this file}"
CLOUDFLARE_ACCOUNT_ID="${CLOUDFLARE_ACCOUNT_ID:-54ff9e3c4c5908b14f410044be85b698}"
WORKER_NAME="${WORKER_NAME:-solitary-hill-dcdc}"
SERVICE_ACCOUNT_FILE="${FIREBASE_SERVICE_ACCOUNT_FILE:-chat-4e1d0-service-account.json}"
WORKER_FILE="${WORKER_FILE:-firechat-fcm-worker.js}"
WORKER_URL="https://${WORKER_NAME}.mr44253990.workers.dev/"

[[ -f "$SERVICE_ACCOUNT_FILE" ]] || { echo "Missing service-account file: $SERVICE_ACCOUNT_FILE" >&2; exit 1; }
[[ -f "$WORKER_FILE" ]] || { echo "Missing Worker file: $WORKER_FILE" >&2; exit 1; }
python3 - "$SERVICE_ACCOUNT_FILE" <<'PY'
import json,sys
p=sys.argv[1]; d=json.load(open(p))
required=("project_id","client_email","private_key")
if any(not d.get(k) for k in required): raise SystemExit("Invalid Firebase service-account JSON")
print("Validated Firebase service account for", d["project_id"])
PY

API="https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/workers/scripts/${WORKER_NAME}"
AUTH=(-H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}")

echo "Uploading Worker module ${WORKER_NAME}..."
UPLOAD_RESULT=$(curl -fsS -X PUT "${API}" "${AUTH[@]}" \
  -F 'metadata={"main_module":"firechat-fcm-worker.js"};type=application/json' \
  -F "firechat-fcm-worker.js=@${WORKER_FILE};type=application/javascript+module")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("success"), d.get("errors"); print("Worker uploaded")' <<<"$UPLOAD_RESULT"

SECRET_PAYLOAD=$(python3 - "$SERVICE_ACCOUNT_FILE" <<'PY'
import json,sys
content=json.dumps(json.load(open(sys.argv[1])),separators=(",",":"))
print(json.dumps({"name":"FIREBASE_SERVICE_ACCOUNT","text":content,"type":"secret_text"}))
PY
)
echo "Writing encrypted FIREBASE_SERVICE_ACCOUNT secret..."
SECRET_RESULT=$(curl -fsS -X PUT "${API}/secrets" "${AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary "$SECRET_PAYLOAD")
unset SECRET_PAYLOAD
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("success"), d.get("errors"); print("Encrypted secret saved")' <<<"$SECRET_RESULT"

echo "Testing ${WORKER_URL}..."
curl -fsS "${WORKER_URL}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps(d,indent=2)); assert d.get("ok") is True, "Worker health check failed"'
echo "FireChat Direct FCM Gateway is ready: ${WORKER_URL}"
