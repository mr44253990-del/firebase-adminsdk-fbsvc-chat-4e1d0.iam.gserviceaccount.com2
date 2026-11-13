# Convo Chat 5.0 "Nova" — plan & status (A → Z)

Branch: `feature/nova-2050-mega-update` · version 5.0.0 (code 63) · Worker API 55

Legend: ✅ done in this branch · 🔧 needs a one-time server step by the admin (see bottom) · 📱 needs a check on a real phone

## A. Launch / loading
- ✅ A1. Removed the separate "Securing your session" screen. The 2FA/session check now runs **inside** the animated launch screen (splash → Home, or splash → 2FA).
- ✅ A2. After sign-in the same launch animation is reused (no second design).
- ✅ A3. Launch screen additions: live stage chips (Connect · Secure · Sync), rotating tips, version label, "Securing your session…" status only while it is really running.

## B. Group calls
- ✅ B1. Pressing audio/video call in a group opens the call screen immediately (no more "join from the notification").
- ✅ B2. Live **Join bar** for every member while a call runs (host name, people count, timer). Becomes "Return" when you are inside.
- ✅ B3. Pressing Call while a call is already running joins that call instead of creating a second room.
- ✅ B4. Host hangs up → the call ends for everyone ("The host ended the call"). Others only leave. Host can also "Leave (keep call running)".
- ✅ B5. Add people during the call (group members first, then other contacts) — they are rung immediately.
- ✅ B6. Audio → video switch during a group call (camera permission, renegotiation with glare handling).
- ✅ B7. Room capacity 8 connected people; every group member (up to 60) may join.
- ✅ B8. Stale call pointers are cleaned automatically (abandoned rooms, ended rooms).
- 🔧 B9. "Add people" by non-hosts needs the updated `database.rules.json` (hosts work with the old rules).

## C. Smart group functions
- ✅ C1. AI catch-up (summary of the last 60 messages).
- ✅ C2. Pinned announcement bar.  ✅ C3. Group description.
- ✅ C4. Admins-only posting.  ✅ C5. Slow mode (10 s – 5 min).  ✅ C6. Co-admins.
- ✅ C7. Member stats (members / online / today / most active).  ✅ C8. @everyone and roll call.
- ✅ C9. Group top bar: two compact call buttons + one menu, so the name is readable.
- ✅ C10. Group photos open full screen and keep their shape.

## D. 1-to-1 calls
- ✅ D1. New incoming-call layout: Decline · Message · Answer on a three-slot grid, swipe track above Answer.
- ✅ D2. Loudspeaker no longer freezes animations (audio routing moved off the main thread).
- ✅ D3. Audio → video switch in the middle of a call.
- ✅ D4. 12 new camera looks (20 total).
- ✅ D5. Light for the front camera: front LED when the phone has one, otherwise a "screen flash" frame + full brightness.
- ✅ D6. Floating call bubble on top of other apps for audio calls (drag, mute, end, tap to return).
- ✅ D7. Status-bar call bar/chip with timer (ongoing-call notification style, Android 12+).

## E. Accounts
- ✅ E1. Several accounts on one phone: pushes carry the intended account; a call/message for another saved account shows a "Switch account" notification; tapping signs in and opens the chat / ringing call.
- ✅ E2. Pushes for accounts that are no longer saved on the phone are ignored.
- ✅ E3. Sign-in screen: constellation background, greeting, typewriter line, feature carousel, tablet width.

## F. Navigation fixes
- ✅ F1. Profile links (`…/profile?uid=` and `convochat://profile`) open the profile (they used to be dropped while the app was still starting).
- ✅ F2. Tapping a message notification opens that chat; group-message notifications open the group; other types open Activity.
- ✅ F3. Post links and QR pairing links wait for the app to be ready as well.

## G. Chats
- ✅ G1. Photos keep their real aspect ratio in bubbles.
- ✅ G2. Chat top bar: only call buttons + menu (search, theme, settings moved into the menu).

## H. AI
- ✅ H1. AI chat keyboard gap fixed (window resize mode + single inset).
- ✅ H2. Database tools: profile search and community facts are fetched from the app database and given to the model; profile cards under the answer.
- ✅ H3. New page: **AI Studio** (rewrite, translate, reply ideas, captions, summary, grammar, bio, ideas, explain).
- ✅ H4. New page: **What's new**.

## I. Admin
- ✅ I1. **Admin AI Copilot**: natural-language commands (Bangla/English) → whitelisted actions: maintenance (with timer), 20 feature switches, notice, broadcast push, ban/unban, Premium grant/remove, profile lookup with e-mail, live report. Works without an AI key for common phrases; the model is used for free-form requests.
- ✅ I2. User management shows sign-in e-mails (+ verified state, provider, last login) — admin only.
- ✅ I3. Smart search: every word must match across name/e-mail/@user/UID/device/bio, operators (`is:new`, `is:inactive`, `is:staff`, `is:unverified`, `is:google`, `is:2fa`, `no:photo`, `v:5.0`, `@gmail.com`), ranking, "find this e-mail in all accounts".
- 🔧 I4. E-mails need the new Worker route `/admin/users/lookup` (Worker API 55).

## J. Notifications
- ✅ J1. Activity box redesigned: animated header with counters and a daily insight, filter chips with counts, time groups, colour-coded cards, unread dots, staggered entrance.

## M. Version 5.1 "Smart" additions
- ✅ M1. **Smart Hub** page: universal search (people, groups, posts, features), shortcuts, online friends.
- ✅ M2. **Group polls** with live results (readable as plain text on older app versions).
- ✅ M3. AI writing tools in group chats.
- ✅ M4. Raise hand in group calls.
- ✅ M5. Offline / back-online connection pill on every page.
- ✅ M6. AI Studio: Tone check, Plan maker, Quiz me.
- ✅ M7. Admin Copilot: Undo, command history, sign a user out of every device.

## O. Version 5.2 additions
- ✅ O1. **Permission Center** page after sign-in (and Settings → Permission manager); Home no longer opens permission pop-ups; the start-up call-readiness dialog is gone.
- ✅ O2. **Chat backup**: one file, chats only; no more duplicate files; optional backup folder (Storage Access Framework) that survives uninstall; Settings → Chat backup (status, back up now, restore).
- ✅ O3. Profile pictures swap silently after the new one has been downloaded.
- ✅ O4. Smart typing indicator (+ "typing…" in the chat top bar).
- ✅ O5. Tour: swipe, dots, "Try it now", pointer, new steps (shown once again after this update).
- ✅ O6. Groups home redesign; group list now carries live-call / pinned / admin data.
- ✅ O7. Group profile: rename, change photo, leave group.
- ✅ O8. Posts: share sheet (Facebook, Messenger, WhatsApp, Telegram, X, link, SMS), AI tools, tag anyone.
- ✅ O9. Chat tools sheet redesign + Video, AI write, AI reply.
- ✅ O10. Notes redesign (thought bubbles, moods, time left).
- ✅ O11. Universal file viewer: photos, video, audio, ZIP (PDF and text already existed).
- ✅ O12. Premium page: spotlight, smart plan picker, tools shelf; occasional non-blocking promo (max once per 3 days).
- ✅ O13. Role-aware Home top bar (admin / staff / Premium) with greeting.
- ✅ O14. Settings: "Backup & permissions" group.
- ✅ O15. Web: login tab switching fixed, QR on phones fixed, chat header opens profile info, motion/glass CSS layer. 🔧 Needs the web site to be re-uploaded (`deploy-web-companion.sh`, then Admin → Web app deploy).
- ⏳ Not done in 5.2: story editor tools/filters, a full redesign of the post composer and post cards, voice-message changes, deeper Settings features.

## P. Version 5.3
- ✅ P1. **Keyboard regression fixed**: `KeyboardResizeMode` restored the old mode with `setSoftInputMode(0)`, which Android ignores, so after one visit to the AI chat every screen stayed in resize mode and the chat composer was covered. The mode is now restored through the window attributes with a holder count; chat and group chat handle the keyboard themselves (resize mode + navigation/IME inset on the composer).
- ✅ P2. Realistic, animated reaction faces (tray, Like button, summary).
- ✅ P3. Quick-reaction button: halo, wobble, "Send once" tray.
- ✅ P4. **Audio library** (Audius, iTunes Search, Radio Browser) for posts and photo stories, with mini player.
- ✅ P5. Admin → Audio library page (master switch, catalogues, API configuration, test, preview) + Copilot switch.
- ❌ P6. Web deploy: the Cloudflare token supplied on 13 Nov had already expired (12 Nov 17:59 UTC), so nothing was published. The web changes are in the repo.

## N. Ideas for the next rounds (not built yet)
- Voice-message transcription and translation (needs a speech-to-text provider key on the Worker).
- SFU-based group calls for more than 8 people (the Worker already lists an `sfu` route).
- Message search across all chats with AI answers ("when did Rahim send the address?").
- Scheduled maintenance / scheduled broadcasts in the Admin Copilot.
- Chat folders and smart filters (Unread, Groups, Calls) on the chat list.
- End-to-end encrypted cloud backup with a user passphrase.
- Home-screen widgets (unread chats, quick AI) and app shortcuts for favourite contacts.
- Automated UI tests on an emulator in CI for the call and notification flows.

## K. One-time server steps for the admin
1. **Worker**: Admin → Server health → "Update Worker" (the app bundles the new `cloudflare-worker.js`, API 55). Needed for user e-mails and for the `recipientUid` push field.
2. **Realtime Database rules**: publish `database.rules.json` (adds `groupCalls/$roomId/memberIds/$uid`), so non-host participants can add people.

## L. Still to verify on real devices 📱
- Group call audio/video between 3+ phones, including the audio → video switch.
- Floating bubble on MIUI / ColorOS (they need "Display pop-up windows while running in the background").
- Front-LED detection on phones that have a selfie flash.
- Account switching with Google sign-in accounts (password accounts switch in one tap; Google accounts open the sign-in screen).
