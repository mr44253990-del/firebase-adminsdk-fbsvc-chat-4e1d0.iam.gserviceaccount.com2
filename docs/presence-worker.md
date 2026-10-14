# Cloudflare presence monitor

Presence scheduling is handled by the separate Cloudflare Worker `firechat-presence-worker.js` and its five-minute Cron Trigger. This feature does **not** use Firebase Cloud Functions. Keep exactly one presence Cron active; saving the Worker URL in the Android app does not create or schedule a Cron Trigger.

The Worker reads each profile's `users/{uid}.fcmToken` from Firestore and sends a silent, normal-priority, data-only FCM probe to each distinct device token. FCM accepting a send is **not** proof that a device received it. Only a device acknowledgement written to `presence/{uid}` counts as evidence of reachability. The Worker reconciles the persisted `online`, `reachable`, and `offline` state from the acknowledgement and lease.

## Configure and deploy

1. Enable the Firebase Cloud Messaging API for the Firebase project and create a service account with only the Firestore read/write and Firebase Cloud Messaging permissions this Worker needs.
2. From the repository root, add the service-account JSON as an encrypted Cloudflare Worker secret:

   ```bash
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT --config wrangler-presence.toml
   ```

   Paste the JSON only into Wrangler's secret prompt. Never put it in Android, Firestore, Git, a checked-in `.env` file, or public logs. Do not commit or share the service-account JSON.
3. Optionally set the Worker variable `ADMIN_EMAIL` to the administrator's Firebase Auth email. The Worker has the app's existing admin address as a fallback.
4. Deploy the Worker:

   ```bash
   npx wrangler deploy --config wrangler-presence.toml
   ```

   `wrangler-presence.toml` configures one Cron Trigger at `*/5 * * * *`. After deploy, confirm the trigger is present and enabled in Cloudflare's Worker settings. If an older Firebase scheduled presence job was ever deployed, disable/delete that existing Cloud Scheduler job manually—removing source code does not remove an already-deployed schedule. Verify that exactly one presence schedule remains active.
5. Check `GET https://<worker-host>/health` returns `ok: true`. This checks that the Worker can parse its secret; a real Cron/manual probe is needed to validate Firebase and FCM permissions end to end.
6. In the app's Admin Control Center → **Presence / 5-min probe**, save the Worker HTTPS base URL (any valid HTTPS hostname and path is supported; omit `/health`). Tap **Test** and **Refresh Firebase user list**.

## API

- `GET /health` — public readiness check; never returns the service-account secret.
- `GET /admin/users?limit=50&cursor=0&q=` — Firebase ID token required; only the configured admin email can read the paginated profile and presence list.
- `POST /admin/probe` — Firebase ID token required; admin-only immediate test probe. This does not create another schedule.
- `POST /presence/heartbeat` — Firebase ID token required; refreshes the caller's short presence lease through the Worker.

## Presence and delivery caveats

The dashboard reports **Online** for a foreground user with a valid lease, **Reachable** for a recent device acknowledgement or signal while backgrounded, and **Offline** when there is no recent signal. FCM send acceptance is recorded only as a send result, never as device reachability. Android/FCM delivery is best-effort: Doze, battery/network restrictions, permissions, and OEM behavior can delay or suppress delivery. A five-minute Cron schedules probes; it cannot guarantee that every phone wakes or responds exactly on time.

The app's existing FCM gateway remains responsible for chat and call messages. Presence probes use per-device tokens from Firestore, not an FCM topic. FCM tokens are never returned by the Worker API or written to probe logs. The service-account key stays only in Cloudflare's encrypted Worker secret.
