#!/usr/bin/env bash
# Deploys the Convo "Auto AI posts" Edge Function to Supabase.
# Usage: ./deploy-auto-ai-post.sh <project-ref>
# Needs FIREBASE_SERVICE_ACCOUNT and PRESENCE_CRON_SECRET already set (deploy-presence-edge-function.sh does that).
set -euo pipefail
REF="${1:?project ref, e.g. abcdefghijklmnop}"
command -v supabase >/dev/null || { echo "Install the Supabase CLI first: https://supabase.com/docs/guides/cli"; exit 1; }

supabase link --project-ref "$REF"
# Read the Mistral key without echoing it or saving it in shell history.
# Optional: the provider/model saved in the app (Admin -> AI providers & keys) is used first.
read -r -s -p "Fallback Mistral API key (optional, Enter to skip): " MISTRAL_KEY; echo
[ -n "$MISTRAL_KEY" ] && supabase secrets set "MISTRAL_API_KEY=$MISTRAL_KEY"
supabase functions deploy auto-ai-post --no-verify-jwt

echo
echo "Deployed. Health check:"
curl -s "https://$REF.supabase.co/functions/v1/auto-ai-post/health"; echo
echo
echo "NEXT: run supabase/sql/auto_post_cron.sql in the SQL Editor (after presence_cron.sql)."
echo "Then open the app: Admin -> Auto AI posts, turn it on and press Save."
