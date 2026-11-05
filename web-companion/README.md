# Convo Chat Web

The real web app for Convo Chat — same account, same chats, same settings, in a browser.
It is **not** a demo or a companion stub: it signs you in through a one-time pairing code,
then behaves like the Android app (chat list, messenger, media, settings, profile, sessions).

Everything in this folder is a plain static site. There is no build step, no bundler and no
npm dependency, so Cloudflare Pages can serve it exactly as committed.

```
web-companion/
├── site/                 <- the deployable output directory (upload this)
│   ├── index.html        shell + PWA hooks
│   ├── boot.js           startup, splash → app
│   ├── api.js            Worker client (session token, retries, typing)
│   ├── store.js          app state, persistence, settings
│   ├── ui.js             toasts, sheets, menus, popovers, helpers
│   ├── screens.js        login / home / messenger / settings / profile views
│   ├── chat.js           conversation rendering + composer
│   ├── app.js            controller: routing, polls, actions
│   ├── styles.css        design system, mobile + desktop layouts
│   ├── manifest.webmanifest
│   └── icons/            app icons
├── _headers              CSP, no-store, security headers
├── _redirects            /web, /app → /
└── deploy-web-companion.sh  (repo root) one-command deploy
```

## Deploying

### Option A — one tap inside the app (recommended)

Android → **Admin settings → Web Deploy**:

1. Paste a Cloudflare **API token** (template: *Edit Cloudflare Pages* — needs
   `Account → Pages: Edit` and `User → User Details: Read`).
2. Paste the **Account ID** (Cloudflare dashboard → any domain → right sidebar).
3. Tap **Publish**. The Worker stages `site/`, creates the Pages project if needed, uploads
   only changed assets, triggers a production deployment and returns the live URL.

The URL is written back to Firestore (`admin_secrets/web_pages`) and the Worker starts
accepting pairing requests from that origin automatically — no extra secret to configure.

### Option B — from a shell

```bash
ADMIN_TOKEN="<your Convo admin token>" \
WORKER_URL="https://convo-chat-companion.mr4425390.workers.dev" \
./deploy-web-companion.sh
```

The script base64-encodes every file in `site/`, POSTs it to `/admin/web-deploy/upload-site`,
then to `/admin/web-deploy/deploy`, and prints the resulting URL.

## Worker configuration

| Variable | Required | Notes |
| --- | --- | --- |
| `FIREBASE_SERVICE_ACCOUNT` | yes | Service-account JSON, Worker secret. |
| `FIREBASE_DATABASE_URL` | yes | `https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app` |
| `WEB_COMPANION_ORIGIN` | no | Only for a custom domain. The deployed Pages URL is picked up automatically. |
| `MEDIA_BUCKET` (R2) | for Web Deploy | Staging area for `site/` uploads. |

Worker base URL: `https://convo-chat-companion.mr4425390.workers.dev` (compiled into `site/api.js`, every Android caller and the CSP in `_headers` — change it in one `sed` across the repo, not per file).

Worker API version is **49**. Deploy the same source to both `cloudflare-worker.js` and
`firechat-fcm-worker.js` targets before shipping an app build that requires 49.

## How sign-in works

1. Android (or the web app, when already paired) requests a 10-digit single-use code.
2. The browser redeems it once; the Worker atomically burns the code and returns an opaque
   bearer token.
3. The token lives in `localStorage['convo.web.session']`, expires after
   `WEB_SESSION_HOURS`, and is re-validated (hash + account status + session version) on
   every call.
4. Android → **Settings → Web version** lists every active browser session
   (`/web-pair/sessions`) and can revoke one or all of them.

## Profile on the web

The web profile is a real page, not a read-only card:

* cover art, avatar, badges (moderator / premium / profile type), bio, title and pronouns
* friend, follower and following counters
* **Edit profile** writes straight to the account (`/web-pair/profile/save`, name, bio,
  title, pronouns)
* **photo change** crops to a 512 px square in the browser and uploads through the Worker
  (`/web-pair/profile/avatar` → R2 → `users/{uid}.profileImageUrl`)
* a copyable public link (`/web-pair/profile/link`) that resolves to the Worker's
  `/profile?uid=…` page, which now offers **Open in Convo Web** first and the Android app second

The Android profile shows the same link in a "Your web profile" card and in the profile menu.

The browser never holds a Firebase ID token, refresh token or service account.

## Firestore indexes / TTL

Enable TTL deletion on `expiresAt` for `web_pair_codes`, `web_pair_sessions` and
`web_pair_rate`. Expiry is enforced in code regardless of TTL timing.

## Smoke test before release

- Pair from Android, confirm the code appears only after the opt-in tap.
- Redeem twice — the second attempt must fail.
- Pair from the deployed Pages URL and confirm it is accepted (origin auto-registered).
- Settings → Web version lists the session; revoke it and confirm the browser's next call fails.
- Send text, react, mark read, upload an attachment, and confirm Android sees all of it.
- Resize from phone width to desktop width; both layouts must stay usable and premium.
- Confirm `_headers` CSP blocks framing and third-party scripts.

Do not commit a Firebase web API key, service-account JSON, Cloudflare token, or a live
session token to this folder. The static site contains only the public Worker base URL.
