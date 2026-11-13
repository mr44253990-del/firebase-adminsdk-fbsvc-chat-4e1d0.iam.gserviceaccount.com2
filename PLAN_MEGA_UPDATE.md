# Convo Chat — Mega Update Plan (A to Z)

Branch: `feature/mega-update-v3`
Date: 2026-11-13
Scope: bug fixes + group-call upgrades + admin tooling + AI expansion + UI polish.

> Security note: the GitHub PAT shared in chat should be **revoked and regenerated** after this push.

---

## ✅ Done in this branch (code committed)

| # | Area | Change | File(s) |
|---|------|--------|---------|
| 1 | Notification nav | Tapping a message notification now opens the chat even on fresh app launch (sender was only resolved if already cached — now fetched via `loadUserById`). | `MainActivity.kt` |
| 2 | Profile deep link | Profile links now resolve `uid` from query (`uid`/`id`/`userId`) **or** from the last path segment (`.../profile/<uid>`), and accept more host/path shapes. | `MainActivity.kt` |
| 3 | AI chat keyboard | Composer no longer jumps too high / leaves a middle gap: `navigationBarsPadding()+imePadding()` double-padding replaced with `windowInsetsPadding(ime.union(navigationBars))`; `MainActivity` set to `adjustResize`. | `AssistantScreen.kt`, `AndroidManifest.xml` |
| 4 | Remote ring (Security) | New `RemoteCommandExecutor` listens at `remoteCommands/<uid>`; admin "Ring device" runs torch blink (1 s on/off) + vibration, auto-off after chosen duration, consumed-once. | `service/RemoteCommandExecutor.kt`, wired in `MainActivity.onCreate` |
| 5 | Admin → Security | New screen: lists all users with live online/offline dot; per-user Flashlight / Ring buttons; 3/5/10 s duration picker. | `ui/AdminSecurityScreen.kt` |
| 6 | Admin → User management | New screen: lists users with email; "Reset" button calls `FirebaseAuth.sendPasswordResetEmail(email)` so a reset link is mailed to the user. | `ui/UserManagementScreen.kt` |
| 7 | Admin AI control | New screen: natural-language admin commands → verified Firestore writes: maintenance mode on/off (+ message), broadcast, enable/disable AI & web search, status. Quick-action chips included. | `ui/AdminAiControlScreen.kt` |
| 8 | AI Studio (new pages) | New AI hub grid: Assistant, Image Creator, Smart Reply, Summarize, Translator, Voice-to-Text live; Auto Post & Mood Coach marked "soon". | `ui/AiToolsHubScreen.kt` |
| 9 | Dynamic motivation box | Time-of-day-aware rotating quote banner (Bengali + English), auto-cycle 12 s, tap to refresh, dismissible. | `ui/DynamicMotivationBox.kt` |
| 10 | Group call Join banner | Banner shown at top of a group chat while a call is live: host name, participant count, Join button. Disappears for all when host ends the room. | `ui/GroupJoinCallBanner.kt` |

---

## 🟡 Partially done / needs wiring in `MainActivity` NavHost

Add these routes to the NavHost in `MainActivity.kt` (composables are ready):
- `admin_security` → `AdminSecurityScreen(users = viewModel.usersState..., onBack = ...)`
- `user_management` → `UserManagementScreen(onBack = ...)`
- `admin_ai` → `AdminAiControlScreen(onBack = ...)`
- `ai_studio` → `AiToolsHubScreen(onBack = ..., onOpenTool = { ... })`
Add entry buttons in `AdminControlCenterScreen` (admin drawer) and in the Settings menu.

---

## 🔜 Next: remaining requested items (ordered by impact)

### A. Loading / splash
- [ ] Remove any "sequence section" flash: fold all startup work (token refresh, presence, cache warm) into the `SplashScreen` progress ring so no intermediate screen ever shows. (No literal "Sequence" UI string found in code — likely a transient loading overlay; guard with a single `splashFinished` gate.)
- [ ] Splash additions: live percentage text, rotating tip lines, battery/time-aware greeting.

### B. Group calls
- [ ] In `GroupChatScreen`, observe `groupCalls/<groupId>` and show `GroupJoinCallBanner` while `status == "live"`.
- [ ] Host-end tears down whole room: in `GroupCallEngine.endCall(host=true)`, write `status=ended` + clear participants; all clients listen and exit.
- [ ] Add-participant mid-call: picker writes new member ids to `groupCalls/<roomId>/participants` + sends FCM `group_call` invite.
- [ ] Audio → video switch mid-call: toggle `videoCall` flag on the room; each client upgrades on change.
- [ ] Smart group functions: raise-hand, mute-all (host), live captions toggle, reactions float.

### C. Calls (1:1)
- [ ] Incoming-call screen: regenerate layout — Decline left, Message middle, Answer right (already swipeable); add mute/speaker quick row, larger caller name, call-type badge. (`IncomingCallUi.kt`)
- [ ] Loudspeaker animation freeze: move the audio-level animation off the main composition — sample mic amplitude on a `LaunchedEffect` loop with `withFrameMillis` and throttle to ~30 fps; never `collect` inside draw. (`CallScreen.kt` / `CallAura.kt`)
- [ ] Video filters: add Google ML Kit `Segmenter`/`SelfieSegmentation` background blur + color LUTs (Warm, Cool, B&W, Vivid) in `CameraEffects.kt`; apply to the local preview `SurfaceView`.
- [ ] Camera flashlight toggle during video call: `CameraManager.setTorchMode` (only if `FLASH_INFO_AVAILABLE`), guarded by try/catch.
- [ ] Persistent mini call bubble (PiP-style) for **audio** too — ensure `CallMiniOverlay` shows for audio calls when app minimized; keep a slim "talking" call bar at the top with live mic indicator. (`CallMiniOverlay.kt`, `CallForegroundService.kt`)

### D. Multi-account
- [ ] Incoming call to a non-active account: notification shows "Switch account & receive" — tapping switches uid (via `RememberedAccount`) then accepts.
- [ ] Incoming message to a non-active account: `AccountSwitchBanner` already exists — wire it to show on FCM `message` for a remembered-but-inactive uid.
- [ ] Per-account FCM token scoping already stubbed in `FcmAccountIsolation` — finish `unsubscribeForUid`.

### E. Chat / media
- [ ] Image sizing: send original aspect; in the bubble use `ContentScale.Fit` inside a max-height box (e.g. 320.dp) so portrait images show tall, landscape show wide — no forced crop. Tap → full-screen viewer. (`ChatScreen.kt` / `MultiImageSend.kt`)
- [ ] Top call bar: constrain action icons so the contact name gets `weight(1f)` + `maxLines=1` ellipsis — name always visible. (`ChatTopBarIcons.kt`)
- [ ] Smart search: fuzzy match (Levenshtein/difflib), recent queries, filter chips (people/messages/media/links). (`SmartSearch.kt`)

### F. AI
- [ ] AI permission expansion: grant the assistant read-only DB search for any profile (already has `find user` — extend to posts, groups, mutual friends) with a "source: your data" citation chip.
- [ ] Admin AI: extend `executeAdminCommand` with more verbs (ban user, wipe post, force logout, set min app version).

### G. Login screen
- [ ] `AuthScreen.kt`: add animated gradient mesh background, biometric login row, "continue as guest" hint, social proof line, OTP phone option toggle.

### H. Notifications / motivation
- [ ] `EnhancedNotificationBox` + `DynamicMotivationBox` combined into one smart top strip on Home.
- [ ] Notification grouping per conversation, reply-from-notification already exists — verify it posts to the right chat.

---

## Push
- New branch `feature/mega-update-v3`, commit all of the above, push to origin.
