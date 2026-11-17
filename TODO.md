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

## Q. Version 5.4
- ✅ Q1. "Open Web Version" opens the browser; admin-configurable web card (address, title, benefits, on/off, optional Home placement).
- ✅ Q2. Call delivery: push retry with a fresh token (5 s and 12 s) + realtime in-app call signal through `notifications/{uid}`.
- ✅ Q3. Group swipe-to-reply layout bug fixed.
- ✅ Q4. Calmer chat motion (softer entrance, item placement animation, gentler chat transitions).
- ✅ Q5. Floating call bubble uses the in-app card design (Compose in the overlay window) and has an X to hide; in-app card has X too.
- ✅ Q6. Audio: Deezer, Openverse, Jamendo; "All" tab; master switch sets every catalogue.
- ✅ Q7. Assistant tools drawer + AI Studio / Smart Hub buttons.
- ✅ Q8. Web published to Cloudflare Pages (`convo-chat-companion.pages.dev`, site v5.2.0).

## S. Version 5.5 — stability round
- ✅ S1. Incoming call: duplicate announcements ignored (service + activity), "answered" state read from the engine (survives re-creation), status never downgraded from connected to ringing.
- ✅ S2. TURN fetch can no longer crash or fail a call (STUN fallback, parse errors caught) — both engines.
- ✅ S3. Call foreground service always reaches the foreground (step-down list of service types, plain notification fallback).
- ✅ S4. Group call blank screen: the call screen no longer depends on the group that the chat screen clears when it closes.
- ✅ S5. Screen share: no capture before the projection service is up (Android 14 crash); group audio calls can share; FLAG_SECURE lifted while sharing.
- ✅ S6. Torch request mirrors WebRTC's capture request (frame-rate range etc.). 📱 needs a real-phone check.
- ✅ S7. External share opens the chat and shows a live upload bubble.
- ✅ S8. Sign-in: no silent saved-password lookup at start; saved accounts suggested while typing.
- ✅ S9. Web QR login implemented in the app (scan → confirm account → /web-pair/qr/approve).
- ✅ S10. Post music under the photo + autoplay in the feed; Freesound provider (key).
- ✅ S11. Animated GIF / WebP support (coil-gif) and lossless GIF sending in chats.
- ✅ S12. Web card as a slim strip (stories area + profile menu).
- ⏳ Not done: redesign of the in-call screen, music on video stories, a GIF search picker, "screenshot on = crash" could not be traced to a specific cause (only the screen-share path was found and fixed).

## Y. Version 6.1 — floating nav capsule, call-name fix, B–F audit

**Built new (compiles + lint; NOT tested on a real phone):**
- Floating glass capsule bottom navigation bar (GlowGlass style).
- Call screen: long caller names scale down + ellipsis.

**B–F audit (these already exist — found while tracing, not re-added):**
- B chat: scheduled messages, disappearing messages, read receipts, voice speed (0.5–2x), draft labels, full-screen composer, per-chat lock/archive, message menu.
- C group: animated poll results with percentages, polls builder, group AI tools, catch-up summaries.
- D posts/stories: post bookmarks ("Saved"), photo viewer + download, audio library on posts, tag friends, reactions.
- E AI: AI Studio tools (summarise/rewrite/ideas/translate), admin AI copilot, group summariser.
- F admin: announcements (broadcast) panel, insights dashboard, premium gifts, app promotions + APK upload, remote feature flags.

**Genuinely still missing (needs external key / backend / real-device):**
- GIF picker (provider key), music on video stories, call recording, noise-cancel, live captions, admin live-DAU metrics, APK rollback, voice-to-text (paid), nested comment replies, per-chat theme override UI.

**Admin steps still pending:** publish `firestore.rules` (note sound), publish `database.rules.json`, remove the Pages site, rotate tokens.

## X. Version 6.0 — chat-filter swipe + 6 new themes + full feature audit

**Built new (compiles; NOT tested on a real phone):**
- Swipe left/right on the chat list to cycle All · Unread · Archived · Locked (with a toast).
- 6 new "New 2050" themes (Aurora Prism, Black Opal, Cherry Cola, Ice Glacier, Ember Glow, Orchid Noir).

**Audit result — these idea-list items ALREADY exist (so they were not re-added):**
- Scheduled messages (composer → Schedule), disappearing messages (auto + per-chat timer), read-receipt toggle + hide-my-typing, online/last-seen/show typing privacy, post bookmarks ("Saved" feed filter + bookmark button), pin chats, archive, app lock, notification-content hiding, screenshot protection on sensitive screens, admin app promotions + APK upload, media auto-delete after delivery, post photo viewer + download, 20+ themes, camera filters, notes with sound/reactions/replies, groups hub, audio library, AI Studio / assistant tools, Permission Center, chat backup, Qr contact.

**Still genuinely not built (short list):**
- GIF picker (needs a GIF-provider key), music on video stories, call recording, noise-cancel, live captions, admin live dashboard, bulk broadcast, per-chat theme override UI, nested comment replies, emoji/label reactions shown on the note itself, tablets multi-pane.
- Several need server/rule changes and admin action.

**Admin steps still pending:** publish `firestore.rules` (note sound), publish `database.rules.json`, remove the Pages site, rotate tokens.

## W. Version 5.9 — tools into a menu, top bar clean, loading screen restored

**Built (compiles; NOT tested on a real phone):**
- Removed the in-feed tools/filter row. A new funnel icon in the top bar opens one menu with the filters and tools (ChatFilterMenu).
- Loading screen restored to the app-icon look (LiquidLogoTile + wordmark + waveform).
- Removed the duplicate global-search icon on the Chats tab (the inline search bar is the search).

**Complete idea list — what else can be added (not built):**
- Chat: swipe row between filters; pin/favourite contacts at the top of the list; per-chat theme override; chat themes with auto-cycling colors; scheduled messages; self-destructing messages already exist (expiresAt) but no UI timer picker; message translation already exists.
- Calls: full redesign of the receiving call screen; call queue; call recording (needs permissions + storage); voice-isolating "noise cancel" toggle; live captions; hold music; multi-device handoff.
- Groups: group voice/video already exist — add group polls (done 5.1), group surveys with result charts, recurring group calls, group file vault, mod-only announcements, slow mode, auto-mod word filter.
- Posts/stories: video stories with music (left over), GIF picker (left over), stickers on stories, comments with nested replies, post bookmark collections, view-count analytics for the author.
- AI: AI reply suggestions per chat, AI summariser for long chats/groups, AI composer (rewrite/expand/tone), image captioning for accessibility, voice-to-text in every language, admin AI scheduled reports.
- Privacy/settings: passcode pattern unlock, per-app folder lock, screenshot warning, "who can see my online" control, read-receipt toggle, data usage meter, account deactivation flow.
- Admin: live dashboard (DAU, messages/day, storage used), bulk message broadcast, user-generated uptake charts, remote feature toggles already exist (FlagshipConfig) — extend menu, APK update rollback, staff audit log export.
- Design: animated home feed with scroll-linked parallax, RTL perfect layout, tablet/multi-window tweaks, per-user accent colour, dark AMOLED "true black" extra theme, custom app icon packs.
- Reliability: offline queue already exists — add retry indicator; media compression on send; resumable uploads; presence kick-out accuracy; background call wake-up on more OEMs.
- Monetisation/premium: premium wall with animations, one-time tips, regional pricing, subscription gift cards.

**Still pending admin steps (unchanged):** publish `firestore.rules` (note sound), publish `database.rules.json`, remove the Pages site, rotate tokens.

## V. Version 5.8 — icon tools row, persistent top bar, APK promotions, gallery download

**Built (compiles; NOT tested on a real phone):**
- Chats tab tools are icon-only (no text), so they fit one row. The old "Tools & filters" card is gone.
- The top bar stays visible while scrolling; only the notes/quick-tools header collapses.
- Admin promotions: upload an APK; the download link is set from the upload (Supabase `chat_files`, APK mime).
- Full-screen post viewer: download button for the current photo.
- Loading screen no longer shows the app icon tile (straight to the connecting bar).
- 3 more call filters (Ruby, Lagoon, Champagne); more emojis in the message reaction menu and note reactions.

**Not done / caveats:**
- The "smooth photo switching" in the full-screen viewer already uses an animated pager; pinch-zoom still pans the image once zoomed in.
- Media auto-delete unchanged from 5.7: photos need the admin switch ON; voice/files delete after play/download regardless.
- Call screen receive side kept the 5.7 layout with the new filters; no full re-architecture.

**Ideas found while working (not built):**
- APK promotions should show file size and a progress bar during upload.
- Per-photo caption/sticker in the post viewer.
- A "new design" preview thumbnail in the design switch.

## U. Version 5.7 — design switch, chats tab, notes, promotions

**Built (compiles, lint clean; NOT tested on a real phone):**
- Old/new design switch (user: Settings → Design; admin default: Admin → App design & media). New = theme "dark liquid glass" + liquid backdrop behind every page. Individual screens were not re-laid-out one by one; they take the new colours and backdrop.
- Chats tab: search in the top bar, slim quick bar instead of the "Tools & filters" card, one strip for notes + online people.
- Notes with sound, reactions and replies. Reactions/replies are sent as normal chat messages (no new collection).
- Post photo auto-slide + full-screen viewer (`PostGallery.kt`).
- Admin app promotions (`AppPromotions.kt`, Firestore `app_config/promotions`).
- Optional delete-after-delivery for chat photos (`LocalImageStore.kt`, flag `deleteDeliveredMedia`, default off).
- Comment box, message menu, full-screen composer (1:1 chats only), draft label in chat list.
- 3 bubble styles, 10 call filters, portrait self-preview, caller picture in the call notification, natural time wording, 5 s presence refresh.
- Web version removed from the app UI; `web-companion/` and its deploy script deleted from the repo.

**Admin must do:**
- Publish `firestore.rules` — without it a note's sound is not saved (the note itself still saves).
- The Cloudflare Pages site `convo-chat-companion.pages.dev` is still online; delete the Pages project in the Cloudflare dashboard to take it down.

**Not done in 5.7:**
- Faster call ringing / connecting — not changed (needs measurements on real phones).
- Full-screen composer in group chats; draft label for groups.
- Delete-after-delivery covers photos only (files already had it); voice messages and videos are not covered. With it on, a reinstall or new phone cannot re-download old photos, and photos sent before it was switched on can disappear for the sender.
- Unused code left in place: device-login QR screen, admin web pages.
- Notification redesign beyond the call notification; Home top-bar action redesign beyond the search box.

**Ideas found while working (not built):**
- Note reactions shown on the note itself (needs a new collection + rules).
- Promotion click/impression counters for the admin.
- Per-chat design override when the new design is on.
- Swipe between chat filters; pin favourite people in the notes strip.

## T. Version 5.6 — dark liquid glass
- ✅ T1. Design kit `LiquidGlass.kt`: background ribbons, glass tile, wordmark, waveform, frosted surface.
- ✅ T2. Launch screen, sign-in (dark look) and audio call screen rebuilt on the kit, following the supplied reference images. Checked with rendered screenshots of the launch screen and the kit components.
- ✅ T3. Liquid layer on Permission Center, AI Studio, What's new, incoming group call.
- ✅ T4. Calls blocked while a message request is pending (chat buttons + view-model guard).
- ⏳ Not yet in the new look: Home, chat list, chat screen, settings, profile, admin.

## R. Feature ideas — status
| Idea | Status |
|---|---|
| Search every audio catalogue at once | ✅ 5.4 |
| AI tools drawer inside the assistant | ✅ 5.4 |
| Admin-controlled web / sponsor card | ✅ 5.4 |
| Second delivery channel for calls | ✅ 5.4 |
| Voice-message transcription and translation | ⏳ needs a speech-to-text key on the Worker |
| Group calls for more than 8 people (SFU) | ⏳ needs a media server |
| AI answers across all chats ("when did X send the address?") | ⏳ |
| Scheduled maintenance and scheduled broadcasts in the Copilot | ⏳ |
| Chat folders / smart filters on the chat list | ⏳ |
| Music on video stories and on reels | ⏳ |
| Sponsor slots with click statistics for the admin | ⏳ |
| Encrypted cloud backup with a passphrase | ⏳ |
| Home-screen widgets | ⏳ |
| Automatic UI tests on an emulator in CI | ⏳ |

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
