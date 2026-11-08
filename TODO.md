# Convo Chat — Update Plan & Todo List

Repo: https://github.com/mr44253990-del/firebase-adminsdk-fbsvc-chat-4e1d0.iam.gserviceaccount.com2.git
Branch: main

## DONE
- [x] Version code: app/build.gradle.kts → versionCode 52, versionName 4.4.6
- [x] Web: fixed message scrolling/jump (chat.js updateStream — preserve scrollTop + focus)
- [x] Web: keyboard always focuses on chat open/send (mobile + desktop)
- [x] Web: file sharing — native file picker, upload via /media/upload, preview (image/video/audio), download; no artificial frontend cap
- [x] Worker: upload limit raised 95 MB → 2 GB (cloudflare-worker.js)
- [x] Web: QR code login screen — QRious QR, auto-generates, rotates every 30s, two login modes (QR + code) side by side on desktop
- [x] Android: deep link `convochat://pair?code=XXXX` handled (MainActivity.kt)
- [x] Android: QrDeviceLoginScreen composable + NavHost route `device_login` (QrContactScreen.kt, MainActivity.kt)
- [x] Android: ChatViewModel.pendingQrPairCode + setter (ChatViewModel.kt)
- [x] Android: Settings → Devices & sessions → "Device login to browser" button (HomeScreen.kt)
- [x] Android: top bar menu icons smaller (ChatCallPill 44→36dp, ChatThemeOrb 40→32dp)

## IN PROGRESS / NEXT
- [ ] Push all changes to GitHub
- [ ] Deploy web-companion to Cloudflare Pages (worker + site)
- [ ] AI help page update
- [ ] Premium promotion + web version promotion
- [ ] 14-day first-login trial redesign + vibration
- [ ] Smart multi-account (FCM per account, top-banner account switch)
- [ ] Friend list isolation per account
- [ ] Notification box redesign
- [ ] Settings long-press menu (instead of mute)
- [ ] Location share smarter update
- [ ] Admin post publishing

## Notes
- GitHub token: provided in GitHub.txt
- Cloudflare token + account id: provided in cloudflear.txt
- Avoid unnecessary terminal usage; edit files precisely.
