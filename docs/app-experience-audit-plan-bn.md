# Convo Chat — পুরো অ্যাপের screen-by-screen UX ও design plan

**ধরন:** implementation roadmap + UI audit. এটি এক release-এ 100+ backend/security/product feature শেষ হওয়ার প্রতিশ্রুতি নয়। v4.4.4-তে নিচের scroll-responsive chat chrome বাস্তবায়িত হচ্ছে; অন্য page-গুলোর কাজ release phase ও test pass অনুযায়ী ধাপে হবে। আগের #125–#224 feature backlog-ও আলাদা করে রাখা আছে।

## 1) Design লক্ষ্য ও shared rules

1. **কথোপকথন আগে:** অপ্রয়োজনীয় header, tool row, banner ও duplicated action scroll-এ গুটিয়ে যায়; গুরুত্বপূর্ণ search/navigation হারায় না।
2. **একই design language:** Material 3, বর্তমান light/dark/premium palette, Smart wallpaper theme, shared radius/spacing/type scale; neon/glow শুধু accent, body text নয়।
3. **সিস্টেম-বার নিরাপদ:** status/navigation/IME inset, display cutout, edge-to-edge, keyboard ও gesture navigation—প্রতিটি full-screen surface পরীক্ষা।
4. **ছোট পর্দা আগে:** 360dp width, বড় font scale, Bengali/English text wrapping, foldable/tablet ও landscape-এ clipping নয়।
5. **Animation কাজের জন্য:** header collapse, search transition ও panel expand/shrink ছোট ও interruptible; reduced-motion preference পরিকল্পনায় থাকবে। List scroll বন্ধ/lag করবে না।
6. **Accessibility:** touch target, icon label, contrast, TalkBack order, keyboard/focus ও state শুধু রঙের ওপর নির্ভর না করা।
7. **Offline state সত্যভাবে:** queued, sending, sent, acknowledged/delivered—এক অর্থে ব্যবহার; FCM accepted মানেই device received নয়।
8. **Privacy default:** friend-only pickers + search; chat partner সামনে থাকলে message notification নয়; screenshot protection শুধু chat/call screen-এ।
9. **কোনো decorative emoji নয়** app UI-তে। User message/reaction content আলাদা।

## 2) v4.4.4 — Chats list scroll-collapse specification (এখন প্রয়োগ)

### Scroll behavior

- Chats tab-এ search field pinned থাকবে, যাতে scroll করার পরও মানুষ খোঁজা যায়।
- Chat list প্রায় 20dp scroll অতিক্রম করলে header chrome একসঙ্গে animated ভাবে গুটোবে: app-এর বড় brand/status bar, Tools & filters card, expanded tools এবং Active now strip।
- User scroll চালালে expanded tools নিজে থেকে বন্ধ হবে; scrollable area বাড়বে, chat row বেশি দেখা যাবে।
- List-এর একেবারে শুরুতে/উপরে ফিরলে bar, Tools & filters ও online contacts strip fade/slide/expand animation-এ ফিরবে।
- Status bar-এর নিরাপদ ফাঁকা স্থান থাকবে; content system area-র নিচে ঢুকে যাবে না। Bottom navigation আগের মতো থাকবে।
- Search query থাকলে Tools/Active now গোপন থাকবে; query মুছলেও list position অনুযায়ী header আচরণ করবে।
- Saved, call history, local search, discovery, Notes ও All/Unread/Archived/Locked option সরানো হচ্ছে না—এগুলো Tools & filters menu-তে থাকবে।

### Acceptance criteria

- Down-scroll করলে title/online/tools/active strip দৃশ্যমানভাবে সরে যাবে; pinned people-search ব্যবহারযোগ্য থাকবে।
- Up-scroll করে একেবারে top-এ গেলে সব header control ফেরত আসবে।
- Expanded tools থাকাকালে scroll শুরু করলে এটি animate করে বন্ধ হবে; scroll আটকাবে না বা list jump করবে না।
- Empty results, active contacts নেই, offline banner, filter change, search typing/clear, rotation ও tab switch-এ stale collapsed state থাকবে না।
- Compose lazy list stable keys, TalkBack labels, status-bar spacer, bottom navigation inset ঠিক থাকবে।
- UI test: first row click, long-press mute, tools expand/collapse, filter selection, Saved/Call history open, search during scroll।

## 3) Main shell, launch, sign-in ও first run

**বর্তমান surface:** splash/loading, language selection, onboarding, email/phone authentication, session/MFA gates, deep-link entry।

**পরিকল্পনা:**
- launch sequence-এ cached shell আগে, remote data পরে; প্রতিটি stage-এর ছোট, branded loading state ও timeout/retry।
- Sign-in ও sign-up-এ email/phone পথ আলাদা করে স্পষ্ট; admin phone-login toggle respected।
- MFA gate-এ paste/autofill affordance, backup-code সতর্কতা, resend/cooldown এবং session status বুঝতে সহজ করা।
- onboarding-এর feature preview পরে করা যায়; permission prompt কেবল ফিচার ব্যবহার করার সময়, কারণসহ।
- session expired, banned, offline, MFA pending—ভিন্ন error/action; এক generic spinner নয়।

**Done when:** cold start, no network, expired session, phone instant verification, 2-step enabled/disabled ও deep-link থেকে নিরাপদে route খুলবে।

## 4) Home feed, post composer, stories, polls ও reels

**বর্তমান surface:** Home feed-এ story strip, active poll, post composer, filters, suggestion strip, post cards, comments/reactions; আলাদা immersive video/reel flow।

**পরিকল্পনা:**
- Feed header scroll-এ compact হবে; active video/post-এর tap target ও scroll gesture একে অপরের সঙ্গে সংঘর্ষ করবে না।
- Composer actions (photo/video/reel/feeling/analytics) কম উচ্চতার action row-তে; user-created post draft পুনরুদ্ধারযোগ্য।
- Post card-এ author, visibility, timestamp, text/media, reactions/comments এবং report controls-এর visual hierarchy স্থির।
- Stories/reels-এর close/back, profile, captions ও actions safe insets মেনে; swipe/scroll intent স্পষ্ট।
- Poll-এর ended state, vote state, result access ও anonymity স্পষ্টভাবে দেখানো।
- Empty feed, load-more, offline cached item ও failed media-র পৃথক skeleton/placeholder/retry।

**সীমা:** video autoplay data/battery settings মেনে; TikTok in-app থাকবে; “Not now” কেবল best-effort।

## 5) Chats list ও 1:1 message screen

**Chats list বর্তমান:** people-search, compact Tools & filters, online strip, unread/pinned status, message preview, local search/saved/calls/notes/discovery।

**পরিকল্পনা:**
- v4.4.4 collapsing header (উপরের spec) একটি shared scroll behavior হবে; search ও bottom navigation স্থিতিশীল।
- conversation row-তে avatar/story ring, name/verification, time, preview/type, active/last-seen, read/delivered state ও unread badge—এক নজরে পড়ার মতো।
- sort order (unread, recency, pin) UI label/setting-এর সঙ্গে মিলবে; archive/lock filters কখনো নীরবে thread হারাবে না।
- 1:1 chat: pinned message, reply preview, typing, read receipt, attachment actions, voice gesture (down cancel/up send), expiry ও privacy controls—কোন gesture কী করে তা ইঙ্গিতসহ।
- chat toolbar scroll/call mode অনুযায়ী compact; call/video/profile/settings, search, more menu-র action duplicates সরানো।
- keyboard-open, multi-select, quoted reply, voice recording, file viewer ও full-screen media-তে composer/controls safe area মেনে।
- image/video, document ও link preview loading/fail states; cached text ও remote file আলাদা status।

**Done when:** 1:1 পাঠানো, delivered/read state, locked chat, no-notification-for-open-partner rule, FLAG_SECURE only on chat, file auto-download behavior regression test pass।

## 6) Groups, group-chat ও group calls

**বর্তমান surface:** group list/create, group conversation, participants, group voice/video call, invite/join flow।

**পরিকল্পনা:**
- Group header-এ name, member count, role/permission ও call state; details এক scrollable settings page-এ।
- member list-এ friend-only searchable picker; group owner/moderator actions confirm ও auditযোগ্য।
- group-chat message rows 1:1-এর typography/status reuse করবে; sender identity ও reply context group-এ স্পষ্ট।
- group call-এ active speaker, mute/camera/share, connection state, participant overflow ও minimize affordance; PiP/minimize শুধুমাত্র active video call-এ।
- room ID/invite expiry, failed join, permission denied ও reconnect screen-এর জন্য retry/leave path।
- group-call history পরবর্তী scope; বর্তমান call-history-কে group-call support আছে বলে দেখানো হবে না।

## 7) Search, discovery, contacts, share/mention/member pickers

**বর্তমান surface:** people search/discovery, cached chat search, profile picker, contact matching, incoming share flow।

**পরিকল্পনা:**
- এক global search shell-এ People, messages, media, links filter; প্রতিটি result-এর source, local/server scope ও freshness label।
- সকল mention/share/forward/add-member/recipient picker friend-only + search; “no friends” এবং duplicate-recipient state।
- contact import opt-in, hash-only matching, preview/consent এবং match removal action।
- search debounce/cancel, empty/loading/error/offline result cache; দ্রুত typing-এ পুরোনো query ফলাফল দেখাবে না।

**সীমা:** বর্তমান global chat search device cache-নির্ভর; server-wide full-text search নয়। নতুন cloud search হলে security rules, indexing ও cost review আগে।

## 8) Profile, account, settings, themes ও privacy

**বর্তমান surface:** profile/post details, settings hub, language, app/chat/call themes, privacy, notifications, backup, app lock, two-step verification।

**পরিকল্পনা:**
- settings hub-এ category search, “যা বদলেছে” preview, state summary ও stable back behavior।
- theme gallery-তে light/dark/Smart, live chat + call preview, wallpaper-derived fallback, contrast preview; premium entitlement ঠিকমতো gate।
- account/privacy controls—online/last-seen/read/typing/contact permissions—per-setting explanation ও reset path।
- backup page-এ এক local backup file-এর current behavior স্পষ্ট; overwrite, restore confirmation, encryption/last-tested status।
- profile image/bio, phone/email verification, linked-device sessions, logout/delete/export—পরস্পরের সঙ্গে consistent।

**বিশেষ:** service-account key কখনো Android/Firestore public doc/Git-এ নয়; server secret store-এ।

## 9) Calls, call history ও active call screen

**বর্তমান surface:** 1:1 audio/video, group calls, TURN routing, active-call controls, call history, video PiP/minimize, call skins।

**পরিকল্পনা:**
- Recents list-এ incoming/outgoing/missed/completed, avatar, duration, date, callback type ও delete—all filters/search consistent।
- in-call controls safe bottom area-তে; answer/decline/end/hang-up states পৃথক; network reconnect status স্পষ্ট।
- video self-view/remote content, screen share, speaker, mute, captions future option—gesture ও keyboard access; active non-video call-এ PiP নয়।
- caller ID/privacy, microphone/camera permission explanation এবং failed-call diagnostics user consent-সহ।

**সীমা:** FCM send acceptance device receipt-এর নিশ্চয়তা নয়; background call delivery OS/OEM/network-নির্ভর।

## 10) Assistant, AI, Notes, Saved, files, storage ও backup

**বর্তমান surface:** AI assistant/writing helpers, friend Notes, saved messages, local reminders, received file viewers, storage manager, local backup।

**পরিকল্পনা:**
- AI screens-এ selected context, provider, approximate cost/limit, retention ও retry state দৃশ্যমান; generated text user না পাঠানো পর্যন্ত draft।
- Notes-এ owner/friend access status, expire time, permission-denied state; notes Firestore rules deploy না হলে স্পষ্ট diagnostic।
- Saved/Catch-up-এ source conversation ও jump-to-message; no result/removed thread behavior।
- file list type, bytes, location, download state, open/share/delete confirmation; app folder under Downloads policy বজায়।
- local-only reminders/restrictions app data clear/reboot-এর পর বোঝানো।

**Key:** AI providers current Worker configuration-এ; নতুন provider যুক্ত হলে key/backend requirements আলাদা release note-এ।

## 11) Admin, staff, analytics ও moderation

**বর্তমান surface:** admin control center, staff permissions, analytics, users, flags/content moderation, configs।

**পরিকল্পনা:**
- User-management search/status filter/profile quick actions একই reusable table/list system-এ।
- permission-by-role access, destructive-action confirmation, audit reason/timestamp ও rollback path।
- live metrics-এ source/time-window/coverage label; missing data শূন্য বলে দেখানো নয়।
- feature flags staged rollout/kill switch; secrets view masked, copy/reveal audit।
- moderation queue report context, block/appeal state, PII redaction ও reviewer handoff।

## 12) Notifications, background presence, offline ও system integration

- chat partner বর্তমানে সামনে থাকলে তার message notification নয়; Android channel/quiet hours respect।
- Reachable acknowledgement এলে Active 5 মিনিট; server accepted-send-কে device received বলা যাবে না।
- exactly one scheduler active—Supabase cron অথবা Cloudflare cron; Firebase Functions ব্যবহার নয়।
- presence/background jobs best effort; Doze/battery/FCM/network restriction user-কে জানানো।
- local cache stale/queued/retry state-এ duplicate message prevent; data/attachment download current folder-by-type behavior বজায়।
- notification permission request contextual, user control আছে, notification preview hide setting কাজ করে।

## 13) Priority batches ও exit gates

### Batch A — v4.4.4 (এই push)
- Chats list header auto-collapse/restore with animation; tools and online contacts hide on down-scroll; search stays pinned.
- Screen-by-screen audit plan ও user-benefit `.txt`।
- Exit gate: release build, emulator smoke, compact/expanded, filter/search/empty/offline, status/nav inset checks।

### Batch B — v4.5 (পরবর্তী নির্বাচন দরকার)
- Home/feed, group list, search/discovery, profile/settings—shared visual hierarchy + empty/loading/error states।
- Accessibility/large-font pass এবং UI tests।

### Batch C — v4.6
- 1:1/group chat, call screens, picker consistency; network/permission states।

### Batch D — v4.7+
- Backup/account lifecycle, privacy/security, assistant/data governance, admin/moderation, advanced integrations।
- E2EE, cloud call history, external AI/API integration-এ threat model, backend ownership ও credentials আগে নিশ্চিত।

## 14) Regression checklist ও user constraints

- No unrelated mode-only Git changes stage/commit.
- No Firebase Functions; Supabase Edge Functions for scheduled presence; only one active cron.
- Service-account key backend secret only; no Android, Firestore client doc, Git or logs.
- Friends-only + searchable everywhere a user chooser/share/mention appears.
- Phone login admin-toggleable; signup email and phone.
- Local backup exactly one file updated in place; restore reads same file.
- Downloads under app-named Downloads folder by file type.
- Keep existing email/Worker flows and add provider failover without replacing them.
- FLAG_SECURE only on chat/call; call minimization only active video call; voice gesture down=cancel/up=send.
- No decorative emojis in app UI; reaction content can remain.
- Every phase must preserve existing routes, deep links, account/session/MFA gates and build variants.
