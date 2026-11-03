# Convo Chat Web Companion (Cloudflare Pages)

A minimal browser companion for **accepted one-to-one text chats**. The browser does not sign in to Firebase and never receives a Firebase ID token or refresh token. Every browser API request goes through the existing Cloudflare Worker using an opaque session token. Pairing codes are short-lived; browser sessions intentionally have no automatic expiry. Group chats, attachments, voice/video calls and account changes are intentionally out of scope.

## Security flow

1. The user explicitly taps **Open web version** in the Android profile menu, or manually taps **Link my browser** inside Profile & settings → Account security.
2. The Android app sends its Firebase ID token to the Worker and requests a one-use pairing code with `optIn: true`.
3. The Worker stores only a hash-keyed pairing record, valid for 90 seconds. The 10-digit code is one-use and rate-limited at redemption (five attempts per IP-hash per minute).
4. The Pages UI redeems the code from the one configured HTTPS origin. The Worker atomically marks the code consumed with a Firestore `updateTime` precondition, and returns a random opaque bearer token only once.
5. The browser stores only the random opaque session token in `localStorage` so the user remains signed in across browser restarts. It has no automatic expiry; browser logout or Android **Revoke all web sessions** removes access. Every request re-checks the stored hash, account status and account-wide session version. This persistent option is intended for a private device; shared/public devices should log out, and lost devices should be revoked from Android.
6. Android **Revoke all web sessions** increments the account session version, immediately invalidating every paired browser token. Browser **Log out** revokes that one session record.
7. The Worker enforces accepted-friend status in both directions and block lists on every chat read/write. It proxies only text messages and returns a limited recent window.

## Cloudflare Pages setup

1. Create/use the Pages project named `convo-chat-companion` with this directory (`web-companion/`) as the static output directory; there is no build command. The web client is in its `web/` subdirectory, so the app is served at `https://convo-chat-companion.pages.dev/web/` (and `/web` redirects there). The root copy remains for compatibility.
2. In the existing Worker environment, set the non-secret variable `WEB_COMPANION_ORIGIN` to the exact Pages **origin only**, for example `https://convo-chat-companion.pages.dev` (no `/web` path or trailing slash). CORS checks the origin, while Android receives the full `/web/` URL.
3. Ensure the existing Worker has `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_DATABASE_URL` configured. Service-account JSON stays in Worker secrets. The Worker API source is now **48**; deploy the exact same source to `cloudflare-worker.js` and `firechat-fcm-worker.js`'s configured Worker deployment.
4. If the Worker hostname changes, update both copies of `config.js` and the `connect-src` entry in `_headers` to the exact HTTPS Worker origin.
5. In Firestore, enable TTL deletion on the `expiresAt` field for `web_pair_codes` and `web_pair_rate`. Pairing records expire quickly; persistent session documents intentionally have no automatic expiry and are invalidated by `revokedAt` or the account-wide session version.
6. Deploy the Android version that requires Worker API 48 only after the new Worker is live. The current released app still targets the older baseline; this working-tree change is not released.

## Manual smoke test before release

- Pair from an authenticated Android account and confirm the code is visible only after the user taps the opt-in button.
- Confirm an incorrect/expired code fails; redeem a valid code twice and verify the second attempt fails.
- Confirm requests from an unapproved `Origin` fail and that the configured Pages origin can exchange a code.
- Open one accepted friend's chat, send plain text, and confirm it appears in Android. Try a non-friend and a blocked account; both must be rejected.
- Sign out from the browser, then check that the same token fails. Pair again, use Android's revoke-all button, and verify the browser is rejected on its next API call.
- Check persistent auto-login across browser restarts, explicit browser logout, Android revoke-all, HTTPS-only Pages hosting, opaque-token-only localStorage, browser CSP, Firestore TTL settings, and Worker API health version 48.

Do not put a Firebase web API key, Firebase service account, OVH token, session token, or pairing code into source control or public Pages files. The static `config.js` contains only the public Worker base URL.
