# Convo Chat Web Companion (Cloudflare Pages)

A minimal browser companion for **accepted one-to-one text chats**. The browser does not sign in to Firebase and never receives a Firebase ID token or refresh token. Every browser API request goes through the existing Cloudflare Worker using an opaque, short-lived session token. Group chats, attachments, voice/video calls and account changes are intentionally out of scope.

## Security flow

1. The user explicitly taps **Link my browser** inside Android → Profile & settings → Account security.
2. The Android app sends its Firebase ID token to the Worker and requests a pairing code with `optIn: true`.
3. The Worker stores only a hash-keyed pairing record, valid for 90 seconds. The 10-digit code is one-use and rate-limited at redemption (five attempts per IP-hash per minute).
4. The Pages UI redeems the code from the one configured HTTPS origin. The Worker atomically marks the code consumed with a Firestore `updateTime` precondition, and returns a random opaque bearer token only once.
5. The browser keeps that token in `sessionStorage` only (not localStorage, cookies, or IndexedDB). It expires after 12 hours. Each request re-checks the stored hash, account status and account-wide session version.
6. Android **Revoke all web sessions** increments the account session version, immediately invalidating every paired browser token. Browser **Log out** revokes that one session record.
7. The Worker enforces accepted-friend status in both directions and block lists on every chat read/write. It proxies only text messages and returns a limited recent window.

## Cloudflare Pages setup

1. Create/use the Pages project named `convo-chat-companion` with this directory (`web-companion/`) as the static output directory; there is no build command. The web client is in its `web/` subdirectory, so the app is served at `https://convo-chat-companion.pages.dev/web/` (and `/web` redirects there). The root copy remains for compatibility.
2. In the existing Worker environment, set the non-secret variable `WEB_COMPANION_ORIGIN` to the exact Pages **origin only**, for example `https://convo-chat-companion.pages.dev` (no `/web` path or trailing slash). CORS checks the origin, while Android receives the full `/web/` URL.
3. Ensure the existing Worker has `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_DATABASE_URL` configured. Service-account JSON stays in Worker secrets. The Worker API source is now **48**; deploy the exact same source to `cloudflare-worker.js` and `firechat-fcm-worker.js`'s configured Worker deployment.
4. If the Worker hostname changes, update both copies of `config.js` and the `connect-src` entry in `_headers` to the exact HTTPS Worker origin.
5. In Firestore, enable TTL deletion on the `expiresAt` field for `web_pair_codes`, `web_pair_sessions`, and `web_pair_rate`. This only cleans expired records; access checks enforce expiry regardless of TTL timing.
6. Deploy the Android version that requires Worker API 48 only after the new Worker is live. The current released app still targets the older baseline; this working-tree change is not released.

## Manual smoke test before release

- Pair from an authenticated Android account and confirm the code is visible only after the user taps the opt-in button.
- Confirm an incorrect/expired code fails; redeem a valid code twice and verify the second attempt fails.
- Confirm requests from an unapproved `Origin` fail and that the configured Pages origin can exchange a code.
- Open one accepted friend's chat, send plain text, and confirm it appears in Android. Try a non-friend and a blocked account; both must be rejected.
- Sign out from the browser, then check that the same token fails. Pair again, use Android's revoke-all button, and verify the browser is rejected on its next API call.
- Check 12-hour expiry, HTTPS-only Pages hosting, no localStorage use, browser CSP, Firestore TTL settings, and Worker API health version 48.

Do not put a Firebase web API key, Firebase service account, OVH token, session token, or pairing code into source control or public Pages files. The static `config.js` contains only the public Worker base URL.
