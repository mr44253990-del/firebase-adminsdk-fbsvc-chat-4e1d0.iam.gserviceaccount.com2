#!/usr/bin/env bash
# Deploy the Convo Chat web app (web-companion/site) through the Worker's admin deploy API.
#
#   ADMIN_TOKEN="<convo admin firebase id token>" \
#   WORKER_URL="https://convo-chat-companion.mr4425390.workers.dev" \
#   ./deploy-web-companion.sh [accountId] [apiToken] [projectName]
#
# If accountId/apiToken are omitted, the Worker reuses the credentials stored by the last
# successful admin deploy (Android → Admin settings → Web Deploy).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SITE="$ROOT/web-companion/site"
WORKER_URL="${WORKER_URL:-https://convo-chat-companion.mr4425390.workers.dev}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
ACCOUNT_ID="${1:-}"
API_TOKEN="${2:-}"
PROJECT="${3:-convo-chat-web}"

[ -d "$SITE" ] || { echo "Missing $SITE" >&2; exit 1; }
[ -n "$ADMIN_TOKEN" ] || { echo "Set ADMIN_TOKEN to a signed-in admin Firebase ID token" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }

echo "Packaging $(find "$SITE" -type f | wc -l | tr -d ' ') files from site/ …"
PAYLOAD="$(python3 - "$SITE" <<'PY'
import base64, json, os, sys
root = sys.argv[1]
files = {}
for base, _dirs, names in os.walk(root):
    for name in names:
        path = os.path.join(base, name)
        rel = os.path.relpath(path, root).replace(os.sep, '/')
        with open(path, 'rb') as fh:
            files[rel] = base64.b64encode(fh.read()).decode('ascii')
print(json.dumps({'files': files}))
PY
)"

call() {
  local route="$1" body="$2"
  curl -sS --fail-with-body -X POST "$WORKER_URL$route" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    --data-binary "$body"
}

echo "Uploading to the Worker staging bucket …"
call /admin/web-deploy/upload-site "$PAYLOAD" | tee /tmp/convo-upload.json | python3 -c \
  'import json,sys; d=json.load(sys.stdin); print("staged:", d.get("files"), "files")'

DEPLOY_BODY="$(python3 - "$ACCOUNT_ID" "$API_TOKEN" "$PROJECT" <<'PY'
import json, sys
account, token, project = sys.argv[1], sys.argv[2], sys.argv[3]
body = {"projectName": project}
if account: body["accountId"] = account
if token: body["apiToken"] = token
print(json.dumps(body))
PY
)"

echo "Triggering a production deployment …"
RESULT="$(call /admin/web-deploy/deploy "$DEPLOY_BODY")"
echo "$RESULT" | python3 -c '
import json, sys
d = json.load(sys.stdin)
if not d.get("success"):
    print("Deploy failed:", json.dumps(d, indent=2)); raise SystemExit(1)
print("Live:", d.get("url"))
print("Production:", d.get("productionUrl"))
print("Files:", d.get("files"), "| uploaded:", d.get("uploaded"))
'
echo
echo "The Worker now accepts web pairing from this origin automatically."
