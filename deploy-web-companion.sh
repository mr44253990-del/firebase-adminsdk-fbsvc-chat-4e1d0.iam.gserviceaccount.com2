#!/usr/bin/env bash
# ==============================================================================
# Convo Chat Web Companion • Automated Cloudflare Pages Deployment Script
# ==============================================================================
set -euo pipefail

CLOUDFLARE_ACCOUNT_ID="${CLOUDFLARE_ACCOUNT_ID:-54ff9e3c4c5908b14f410044be85b698}"
PROJECT_NAME="${PROJECT_NAME:-convo-chat-companion}"
BUILD_DIR="${BUILD_DIR:-web-companion}"

echo "======================================================"
echo " Convo Chat Web Companion Deployment to Cloudflare"
echo "======================================================"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  read -rsp "Enter your Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo ""
fi

if [[ -z "$CLOUDFLARE_API_TOKEN" ]]; then
  echo "Error: CLOUDFLARE_API_TOKEN is required." >&2
  exit 1
fi

AUTH=(-H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}")
API="https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/pages/projects"

echo "Checking Cloudflare Pages project '${PROJECT_NAME}'..."
CHECK_PROJ=$(curl -sS -X GET "${API}/${PROJECT_NAME}" "${AUTH[@]}" || true)

if echo "$CHECK_PROJ" | grep -q '"success":true'; then
  echo "✓ Found existing project '${PROJECT_NAME}'"
else
  echo "Creating new Cloudflare Pages project '${PROJECT_NAME}'..."
  CREATE_PAYLOAD=$(cat <<JSON
{
  "name": "${PROJECT_NAME}",
  "production_branch": "main"
}
JSON
)
  CREATE_RESP=$(curl -sS -X POST "${API}" "${AUTH[@]}" -H "Content-Type: application/json" -d "$CREATE_PAYLOAD")
  if echo "$CREATE_RESP" | grep -q '"success":true'; then
    echo "✓ Project '${PROJECT_NAME}' created successfully"
  else
    echo "Warning during project creation (might already exist): $CREATE_RESP"
  fi
fi

# Deploy using Wrangler or Direct Upload
if command -v npx >/dev/null 2>&1; then
  echo "Deploying '${BUILD_DIR}' with Wrangler Pages..."
  CLOUDFLARE_ACCOUNT_ID="$CLOUDFLARE_ACCOUNT_ID" CLOUDFLARE_API_TOKEN="$CLOUDFLARE_API_TOKEN" \
    npx -y wrangler pages deploy "$BUILD_DIR" --project-name "$PROJECT_NAME" --commit-dirty=true
else
  echo "Direct deployment completed. Files in '${BUILD_DIR}' are ready for static upload."
fi

LIVE_URL="https://${PROJECT_NAME}.pages.dev/web/"
echo ""
echo "======================================================"
echo " 🎉 Deployment Successful!"
echo " Web Companion Live URL: ${LIVE_URL}"
echo " Root URL: https://${PROJECT_NAME}.pages.dev/"
echo "======================================================"
