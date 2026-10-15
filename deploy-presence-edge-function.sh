#!/usr/bin/env bash
# Deploys the Convo presence Edge Function to Supabase.
# Usage: ./deploy-presence-edge-function.sh <project-ref> /path/to/service-account.json
set -euo pipefail
REF="${1:?project ref, e.g. abcdefghijklmnop}"
SA_FILE="${2:?path to the Firebase service-account JSON}"
command -v supabase >/dev/null || { echo "Install the Supabase CLI first: https://supabase.com/docs/guides/cli"; exit 1; }
[ -f "$SA_FILE" ] || { echo "Service-account file not found"; exit 1; }

supabase link --project-ref "$REF"
CRON_SECRET="$(openssl rand -hex 32)"
# Values are passed as arguments inside this script, so they never reach shell history.
supabase secrets set "FIREBASE_SERVICE_ACCOUNT=$(tr -d '\n' < "$SA_FILE")" "PRESENCE_CRON_SECRET=$CRON_SECRET"
supabase functions deploy presence-monitor --no-verify-jwt

echo
echo "Deployed. Health check:"
curl -s "https://$REF.supabase.co/functions/v1/presence-monitor/health"; echo
echo
echo "NEXT: open supabase/sql/presence_cron.sql in the SQL Editor and use:"
echo "  project URL : https://$REF.supabase.co"
echo "  cron secret : $CRON_SECRET"
echo "(The cron secret is shown once here; keep it private.)"
