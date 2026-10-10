# Separate Firebase presence monitor Worker

`firechat-presence-worker.js` is an independent Cloudflare Worker. It is not the FCM/media gateway and uses its own Worker URL and deployment settings. Run the dependency-free local route smoke tests with `node test-firechat-presence-worker.mjs` before deployment.

## Cloudflare configuration

1. Authenticate Wrangler for the Cloudflare account that should own this separate Worker.
2. Add the Firebase service-account JSON as a Cloudflare secret (the command prompts for its value): `npx wrangler secret put FIREBASE_SERVICE_ACCOUNT --config wrangler-presence.toml`. Do not commit the JSON or paste it into the Android app. The account needs Firestore read/write access for `users`, `presence`, and `presence_monitor`, plus Firebase Cloud Messaging send permission. Enable the Firebase Cloud Messaging API for the project.
3. Optionally configure the Worker variable `ADMIN_EMAIL` as the app administrator's Firebase Auth email. The Worker has the existing app admin address as a fallback.
4. Deploy with `npx wrangler deploy --config wrangler-presence.toml`. This independent config names the Worker `firechat-presence-worker` and provisions the five-minute Cron Trigger (`*/5 * * * *`); its scheduled handler sends one normal-priority, data-only FCM topic message.
5. Confirm `GET https://<worker-host>/health` returns `ok: true`.
6. Publish the updated Firebase rules (`firebase deploy --only database,firestore:rules`). The RTDB rule now restricts global Worker URLs to the admin, while Firestore permits each signed-in device to write only its own server-timestamped push acknowledgement.
7. In the app's Admin Control Center, save this HTTPS Worker URL under **Presence monitoring Worker**. Use **Test** and **Refresh users** to verify the health check and admin-only user list.

The Android app subscribes signed-in installations to the `convo_presence_v1` FCM topic and unsubscribes on sign-out. Topic fan-out sends one message to all subscribed devices without making one HTTP request per user. A device that receives the data-only `presence_probe` writes its acknowledgement to its own Firestore `presence/{uid}` record. The admin endpoint lists app profiles in Firestore's `users` collection and combines them with those acknowledgements and presence leases. FCM tokens are never returned by the endpoint.

## Endpoints

- `GET /health` — public readiness check; does not reveal the service-account JSON.
- `GET /admin/users?limit=50&cursor=0&q=` — Firebase ID token required; only the configured admin email can read the paginated profile/presence list.
- `POST /admin/probe` — Firebase ID token required; admin-only immediate test signal.

## Presence semantics and delivery caveats

The dashboard distinguishes **Online** (recent foreground/valid presence lease), **Reachable** (a recent probe acknowledgement or presence signal while backgrounded), and **Offline** (no recent signal). This is an estimate, not a guaranteed push receipt or a continuous device-health monitor. FCM delivery can be delayed or suppressed by Doze, battery optimization, network loss, notification/background restrictions, and manufacturer-specific Android behavior. A five-minute cron means a signal is scheduled every five minutes; it cannot guarantee that every phone wakes or responds exactly on time.

The existing app FCM gateway keeps its separate responsibility for chat and call messages. The old gateway no longer sends its own scheduled presence probes, avoiding duplicate probes when both Workers are deployed.
