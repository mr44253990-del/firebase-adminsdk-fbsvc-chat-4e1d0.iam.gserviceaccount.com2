# Deep Recharge — v6.2 "Fast Lane" ( consistently fast like WhatsApp / imo )

Audit date: 2026-11-24 · Scope: Android app (`app/`), Cloudflare Worker (`cloudflare-worker.js`, API 55), web companion (`web-companion/`), RTDB + Firestore rules.

## 1. What the audit found (problems & gaps)

### A. Server (Cloudflare Worker) — every push was slow
1. **`getGoogleAccessToken()` was called fresh on EVERY `/push`** — a full RS256 JWT sign + a round trip to `oauth2.googleapis.com` per notification (~300–800 ms added to every message/call push). `fsAuth()` already cached its token; the push path bypassed that cache.
2. **`verifyFirebaseIdToken()` fetched Google's JWKS on EVERY authenticated request** — another network round trip before the worker even looked at the payload.
3. The `/push` route awaited steps strictly in series (auth → token → FCM); nothing overlapped.

### B. Android receive path — O(n) work per incoming message
4. **1:1 chat listener had no limit**: `chats/{chatId}/messages.orderByChild("timestamp").addValueEventListener` re-parsed the WHOLE node, re-merged and re-wrote every message to Room on every single incoming message, and re-acked every incoming message (a receipt write + a delete per message per event).
5. **Unread counter was a read-modify-write race**: `unreadRef.get()` then `setValue(count + 1)` — two messages arriving together can lose one count.
6. **Group messages listener had no `orderBy`/`limit` at all** — the entire `groups/{id}/messages` collection was downloaded, re-parsed and re-cached on every snapshot.
7. **Recipient FCM token was re-fetched from Firestore whenever it wasn't passed in** — `withUserFcmToken` had no uid→token cache, so repeated messages to the same person paid a Firestore `get()` each time.
8. `loadAllUsers()` keeps a snapshot listener on the **entire `users` collection** — every user change re-parses all users (read cost + battery).

### C. Web companion — polling, not realtime
9. Chat list polled every **20 s**, open chat every **7 s**, presence every 45 s — feels slow next to WhatsApp Web.

### D. Security / design gaps (planned, not all fixed here)
10. `users/{uid}` is readable by any signed-in user **and stores `fcmToken`** — Firestore cannot hide single fields, so any user can read anyone's push token. Clean fix: worker-side token lookup (worker already receives `recipientUid`); stop writing `fcmToken` into the public doc.
11. RTDB `notifications/{uid}` write rule lets anyone write a node as long as `senderId` is themselves — acceptable for signaling, but `validate` allows arbitrary extra children; tighten later.
12. Call path: push → worker → FCM plus RTDB signal plus retries at 5 s/7 s and a 30 s timeout is reasonable, but the worker-side token mint (item A1) was the dominant added latency; TURN fetch already has STUN fallback.
13. Still missing (need keys/server/real device): SFU for >8-person group calls, GIF picker, voice-to-text, live captions, call recording, noise cancel, message search across chats, home-screen widgets, E2E encrypted backup.

## 2. Fixed in this round (v6.2 "Fast Lane")

- **Worker: OAuth2 access-token cache** (`__oauthTokenCache`, keyed by account+scope+sub, 60 s safety margin, in-flight dedupe) inside `getGoogleAccessToken()` — one change speeds up `/push`, `/admin/push`, autoreply, AI autoreply, RTDB REST auth, everything.
- **Worker: JWKS cache** (`getSecureTokenJwks`, kid-keyed, 6 h TTL, forced refresh on unknown kid) — removes one round trip from every authenticated request.
- **App: atomic unread increment** — `ServerValue.increment(1.0)` instead of get-then-set (no more lost counts).
- **App: bounded live window** — chat listener now `limitToLast(300)`; only *fresh* messages are persisted to Room and only *un-acked* messages get receipt writes (tracked in `ackedMessageIds`, cleared per chat).
- **App: group listener** — `orderBy(timestamp DESC).limit(100)`, merged with the Room-cached history, only fresh docs persisted.
- **App: FCM token cache** — uid→token with 10 min TTL (60 s negative TTL) in `withUserFcmToken`.
- **Web: faster polling** — chats 20 s → 6 s, messages 7 s → 2.5 s (presence stays 45 s).

Verified: `node tools/worker-tests/run.mjs` → 42/42 passed; `node --check` on worker + web JS OK. Android compile runs in CI (no JDK in this sandbox).

## 3. Big plan (next rounds)

1. **Server-side push tokens**: `/push` accepts `recipientUid` and the worker resolves the device token(s) itself from `fcm_tokens/{uid}` (service account) — then remove `fcmToken` from the public `users` doc and tighten `firestore.rules`. Closes gap 10 and enables multi-device fan-out.
2. **Realtime web companion**: replace 2.5 s polling with RTDB REST streaming (SSE through the worker) for the open chat + chat list; keep polling as fallback. Target: sub-second delivery on web.
3. **Pagination UI**: "load older" for 1:1 chats (one-shot `endAt(lastTs).limitToLast(50)`) and groups (`startAfter` cursor) — listeners stay bounded.
4. **Chat list index**: maintain `user_chats/{uid}/{peerUid}` (lastMessage, lastTs, unread) written on send — the home list becomes one small listener instead of per-chat reads; `loadAllUsers()` becomes paged/search-only.
5. **Presence**: keep the 5-min heartbeat + `onDisconnect`, but move "last seen" reads to the indexed path in 4.
6. **Calls**: parallelize caller-side token resolution with the RTDB call write; keep 5 s/7 s push retries; add jitter; measure ring latency on real phones, then tune. SFU route already exists in the worker for >8 people.
7. **Media**: resumable uploads + compression on send (already chunked via worker→R2); progressive image thumbnails in bubbles.
8. **Admin/reliability**: live DAU dashboard, retry indicator for the offline queue, staff audit export.
9. **Rules hardening**: tighten `notifications/{uid}` validate to a fixed key set; review `chats/$chatId` write scope for members.
10. **CI**: the Android workflow compiles + lints; add the worker contract tests (`tools/worker-tests/run.mjs`) to CI so backend regressions fail fast.
