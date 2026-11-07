# Convo Chat v4.6.0 — Master Update Plan (Bengali Request → English Execution)

> **Date:** 2026-11-07 (Dhaka)
> **Base commit:** cloned from `firebase-adminsdk-fbsvc-chat-4e1d0.iam.gserviceaccount.com2.git` (versionCode 56, versionName 4.5.0)
> **Target:** versionCode **57**, versionName **4.6.0** — “Web + Device Login Release”
> **Philosophy:** minimal terminal, surgical edits, every fix verified before push.

---

## 1️⃣ Executive Summary (Bengali → English)

| # | Original Request (BN) | Interpretation | Priority |
|---|---|---|---|
| 1 | Chat glitch — messages jump up/down inside chat | `msg-scroll` re-render + `requestAnimationFrame` scroll race; `updateStream` resets scroll even when user is reading history | **P0** |
| 2 | Keyboard sometimes not shown | `visualViewport` tracker existed but focus-steal & `preventScroll` missing; textarea font <16px triggers zoom bugs | **P0** |
| 3 | Unlimited file share, preview & download on web | Add `fileUpload` via R2 Worker → `fileUrl/fileName/fileMimeType/fileSize` already supported in `cloudflare-worker.js` but UI only allowed image/file *link*; need real file input | **P0** |
| 4 | QR Scan to Login on web login screen | Web auto-generates QR (rotating 30s), app scans → authenticates → session auto-login | **P0** |
| 5 | Device Login option in app (scanner) | New screen `QrDeviceLoginScreen.kt` using `mlkit.barcode.scanning` + `zxing` already declared | **P0** |
| 6 | Desktop mode → QR auto-generates + dual login options | Detect `matchMedia("(min-width: 860px)")` → show QR + tabs | **P0** |
| 7 | Web login keyboard / auto-refresh / email login goes to home | `form` default submit → page reload; buttons missing `type="button"`; `handleAction` didn't `preventDefault` | **P0** |
| 8 | Top click menus too big on left side | `.menu-item` padding 13px+ large icons → reduce to compact, max-width 320px | **P1** |
| 9 | App file upload no MB limit | Remove `12MB` guard in `ChatScreen.kt` & Worker `firechat-storage-worker.js` limit; chunked upload already unlimited | **P1** |
| 10 | AI can open any page & help about device | Extend `AssistantScreen.kt` intent handler → `deepLink` router | **P1** |
| 11 | Occasional Premium + Web promotion (admin post) | Reuse `FCM` promo already in `cloudflare-worker.js`; add admin UI scheduling | **P1** |
| 12 | First login 14-day trial — beautiful + vibrate | `PremiumScreen.kt` + `AppVersionWelcomeDialog.kt` + `HapticFeedback` | **P1** |
| 13 | Smart multi-account FCM isolation + switch banner | Store FCM token per `uid` in `data/AppDatabase.kt`; show banner when notification arrives for non-active account | **P0** |
| 14 | Friend list leaks across accounts | `viewModel.usersState` not cleared on `FirebaseAuth.signOut` → add `clearOnSwitch()` | **P0** |
| 15 | Notification box redesign | `NotificationBanner.kt` glassmorphism + actions | **P1** |
| 16 | Settings hold → mute → should show menu | `combinedClickable` long-press currently mutes; add `MuteMenuSheet` | **P1** |
| 17 | Location share upgrade | `LocationUi.kt` + `OsmInteractiveMap.kt` — live, address, radius | **P1** |
| 18 | Show keystore credentials at build time | `build.gradle.kts` `doLast { println ... }` (only `KEYSTORE_PATH`/`ALIAS`, never log password) | **P2** |

---

## 2️⃣ Dependency Graph (what must happen first)

```
Worker endpoints (QR create / verify / rotate)
        ↑
Web: QR render + polling ─────→ Android: Scanner screen
        ↑
Web: file upload UI ─────→ Worker storage (already exists)
        ↑
Web: scroll/keyboard fixes (independent, can ship first)
        ↑
Android: account isolation (must ship with web QR, otherwise token leak persists)
```

---

## 3️⃣ Phase-wise Execution (TODO checklist)

### Phase A — Web Companion (no Android build needed, instantly testable)

- [x] **A1. Fix chat glitch** — `chat.js: updateStream()` preserve scroll position, only autoscroll if `stick` true, use `scrollTop` without RAF jitter; add CSS `contain: layout;` + `will-change: scroll-position`
- [x] **A2. Fix keyboard** — improve `trackKeyboard()` debounce, set `interactive-widget=resizes-content` already present, ensure `composer-input` `preventScroll:false` on focus, font-size >=16px
- [x] **A3. Fix email login refresh** — every `data-action` button gets `type="button"`, `document.addEventListener("submit", e=>e.preventDefault())`, stop propagation in `handleAction`
- [x] **A4. Unlimited file share** — add `<input type="file">` hidden, drag-drop, `Api.uploadFile()` → Worker `/web-pair/upload` → returns `fileUrl`; preview via modal; download via `download` attr already present
- [x] **A5. QR login on web** — new `QRSync` module in `screens.js` + `api.js`: `qrCreate()` / `qrPoll()`; canvas QR via `qrcode.js` (inline, no CDN); rotates every 30s; shows beside email tabs on desktop
- [ ] **A6. Menu size** — `.menu-item` compact 10px padding, `.sheet-card` max-width 360px centered on desktop
- [ ] **A7. Notification banner polish**

### Phase B — Android App (requires build)

- [ ] **B1. Version bump** 56→57, 4.5.0→4.6.0 + keystore log helper
- [ ] **B2. Menu compaction** — `HomeScreen.kt` DropdownMenu width `280.dp → 240.dp`, item padding `12.dp → 9.dp`, icon size `24.dp → 20.dp`
- [ ] **B3. Device Login scanner** — new `DeviceLoginScreen.kt` (CameraX + MLKit barcode),Firestore `web_qr_sessions` doc write with `uid + verification`
- [ ] **B4. FCM per-account isolation** — `MyFirebaseMessagingService.kt`: store token under `users/{uid}/fcmTokens/{tokenHash}` + remove on signOut; `HomeScreen.kt` banner `AccountMismatchBanner`
- [ ] **B5. Clear users on account switch** — `ChatViewModel.clearCacheForNewUser()`
- [ ] **B6. Hold-menu upgrade** — `SettingsUi.kt` `combinedClickable` → `showMuteActionSheet()` with Mute/Unmute/Pin/MarkRead/Clear...
- [ ] **B7. Unlimited file upload** — remove `12*1024*1024` check, add progress + cancel
- [ ] **B8. AI open page** — `AssistantScreen.kt` → `handleIntent("open_settings")` etc.
- [ ] **B9. Premium 14-day trial vibrate** — `PremiumScreen.kt` `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` + confetti
- [ ] **B10. Location share** — `LocationUi.kt` live share + expiry picker

### Phase C — Worker & Deploy

- [ ] **C1. Add QR endpoints** in `cloudflare-worker.js` + `firechat-fcm-worker.js`
- [ ] **C2. Deploy** via `deploy-web-companion.sh` + `deploy-firechat-worker.sh`
- [ ] **C3. Push to GitHub**

---

## 4️⃣ Technical Decisions (to minimise terminal)

1. **No new npm deps** — web stays vanilla JS; QR rendered via 12KB inline `qrcode.min.js` vendored into `site/qr.js` (already allowed by CSP — add `script-src 'self'` only)
2. **File upload reuses existing Worker route** — `POST /web-pair/upload` already handles R2; we just need to expose it on web (previously Android-only)
3. **QR polling interval 2s** — balances Firestore read cost vs UX; QR itself rotates every 30s server-side (`expiresAt = now+45s`)
4. **Scroll fix is pure CSS+JS** — no external library, testable in preview instantly
5. **Android scanner reuses existing `mlkit.barcode.scanning`** — already in `build.gradle.kts`, no new AAR needed

---

## 5️⃣ Risk & Rollback

| Risk | Mitigation |
|------|------------|
| Worker QR endpoint breaks existing pair code | Separate collection `web_qr_sessions` vs `web_pair_codes`; keep old `redeem` untouched |
| Unlimited file upload abuse | Worker still enforces per-file 100MB soft cap + R2 lifecycle 10 days (visible in `r2-lifecycle.json`) |
| Scroll fix regresses | Guard: only autoscroll when `stick` true; add `isUserScrolling` flag 1.5s cooldown |
| Keyboard fix on iOS | Test `visualViewport` fallback; keep `interactive-widget=resizes-content` |

---

## 6️⃣ How to Test (smoke, no terminal)

1. Open web preview: chat → send 5 messages → scroll to top → new message arrives → **should NOT jump** (only badge)
2. Tap composer on mobile → keyboard appears, composer stays above it, messages visible
3. Login → type email → press Enter → **stays on page**, error shown inline, no refresh
4. Desktop → login shows **QR + tabs** side-by-side
5. Scan QR from app → web auto-logs in <2s
6. Share file 25MB → progress bar → receiver sees download + preview
7. Switch account in app → friend list of previous account disappears

---

## 7️⃣ Version Bump Detail

```kts
// app/build.gradle.kts
versionCode = 57
versionName = "4.6.0"  // was 4.5.0

// signingConfigs.release will println at build:
doLast { println("🔑 Keystore: $keystorePath | Alias: $keyAlias") }
```

Keystore info will appear at bottom of `./gradlew assembleRelease` log, as requested.

---

*This document is the single source of truth. Every commit message must reference a task ID (A1, B3, …).*
