# Convo Chat — Update Summary (In Progress)

Repo: https://github.com/mr44253990-del/firebase-adminsdk-fbsvc-chat-4e1d0.iam.gserviceaccount.com2.git
Branch: main

## ✅ COMPLETED v4.9.4 (mega update — see docs/v4.9.4-mega-update-bn.md)
- Splash: session-gate screen removed; precheck runs inside the loading screen; live checklist
- Group calls: host joins instantly, live Join banner for everyone, host-end ends all,
  in-call invite, audio→video upgrade, host/participant end confirmation
- Group smart functions: message reactions, AI summary, reply shortcut
- Calls: incoming screen redesign, speaker-freeze fix, system-wide floating call bar,
  speaking indicator, 7 new filters, front-camera torch attempt, video-mode name chip
- Notifications: tap opens the right chat/group (splash race fix), profile deep-link fix
- Multi-account: switch-account notifications + banner (recipientId push routing)
- Admin: user e-mails + Send forgot password, AI console, Security page with device locate
- AI: AI Toolbox page, keyboard jump fix, full-DB user search commands
- Chat: natural-size image bubbles, group full-screen viewer, Motivation box,
  activity filter chips, biometric login for saved accounts
- Build: KSP version fixed (2.2.10-2.0.2); compileReleaseKotlin + Java verified

## ✅ COMPLETED (earlier, not yet pushed)
- **Version code** — app/build.gradle.kts now at v63 / 4.9.4
- **Web: scroll/keyboard fixes** — chat.js updateStream preserves scrollTop + focus
- **Web: file sharing** — native file picker, /media/upload, preview + download, 2 GB Worker cap
- **Web: QR login** — QRious QR, auto-generates, rotates every 30s, QR+code modes
- **Android: deep link** — convochat://pair?code=.. handled in MainActivity.kt
- **Android: QrDeviceLoginScreen** — QrContactScreen.kt + device_login route
- **Android: ChatViewModel.pendingQrPairCode** — + setter
- **Android: Device login button** — HomeScreen.kt Devices & sessions
- **Android: smaller top menu icons** — ChatTopBarIcons.kt
- **Android: TrialWelcomeDialog wired up** — shows on first login with vibration (HomeScreen.kt)
- **Android: Web version promotion banner** — added below Premium banner (HomeScreen.kt)
- **cloudflare-worker.js** — upload limit 95 MB → 2 GB

## ⏳ REMAINING FROM USER REQUEST
- Friend list isolation per account — already per-user via currentUser?.friends (verify)
- Smarter location sharing — LocationUi.kt exists (verify/improve)
- Admin post publishing — PostComposerScreen + createPost exist (verify)
- Cloudflare Pages deployment — BLOCKED (invalid API token in cloudflear.txt)

## 🚫 BLOCKED
- Cloudflare API token returns "Invalid API Token" — cannot deploy to Pages
- wrangler doesn't support android arm64
- deploy-web-companion.sh needs ADMIN_TOKEN (Convo Firebase admin ID token) — not provided
