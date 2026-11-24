# Convo Chat ডেস্কটপ ক্লায়েন্ট (Windows + Linux) — রিচার্চ ও পরিকল্পনা

## ডিপ রিচার্চ সারাংশ (Android অ্যাপটি কীভাবে কাজ করে)

### আর্কিটেকচার
- **ভাষা/ফ্রেমওয়ার্ক**: Kotlin + Jetpack Compose (Material 3), `com.ebchat`, ভার্সন ৬.১.১ (versionCode 75)।
- **মোডিউল**: একটিই মডিউল `:app`; ১৬২টি Kotlin ফাইল, প্রায় ৫৭,৭০০ লাইন কোড।
- **ইউআই লেয়ার**: `ui/` ফোল্ডারে স্ক্রিন-ভিত্তিক ফাইল (SplashScreen, LoginNova/AuthScreen, HomeScreen, ChatScreen, GroupChatScreen, PostComposerScreen, PermissionCenterScreen, SettingsUi, PremiumNova, AdminControlCenterScreen ইত্যাদি)। ডিজাইন সিস্টেম: **Aurora Glassmorphism + Material 3**, থিম মোড Light / Dark / AMOLED + Material You Dynamic।
- **ডেটা লেয়ার** (`data/`): Room (`AppDatabase`), মডেল (`Models.kt`, `SocialModels.kt`), `WorkerRouter` (স্মার্ট মাল্টি-ওয়ার্কার লোড-ব্যালান্সিং: SMART ওয়েটেড-র‍্যান্ডম + ফেল-ওভার, ফেইলড ওয়ার্কার ২ মিনিট কুলডাউন)।

### ব্যাকএন্ড চুক্তি (сontracts) — ডেস্কটপ ক্লায়েন্ট এগুলোই পুনরাবৃত্তি করে
1. **Firebase Auth**: Google Sign-In (Credential Manager) + Phone Auth; ডিসপ্লে-নেম/ইমেল/ছবি Google থেকে।
2. **Firestore** কালেকশনগুলো (গুনে দেখা হয়েছে): `users` (৩৮ ব্যবহার), `app_config`, `posts` (১৪), `groups`, `stories`, `messages`, `friend_requests`, `message_requests`, `polls`, `notifications`, `presence`, `live_locations` প্রভৃতি।
3. **RTDB এনভেলাপ**:
   - চ্যাট: `chats/{chatId}/messages/{messageId}` যেখানে `chatId = sorted(uidA_uidB)`; `Message` ফিল্ড: messageId, senderId, senderName, senderUsername, text, timestamp, edited, replyTo*, seenByRecipient, deliveredToRecipient, expiresAt।
   - প্রেজেন্স: `status/{uid}`, আনরিড: `unread_counts/{uid}/{peer}`।
   - কনফিগ: `config/app_settings/workerPool{,Mode}`।
4. **Cloudflare ওয়ার্কার**: FCM v1 গেটওয়ে (`cloudflare-worker.js` ৩.২.০+ / `firechat-fcm-worker.js`), TURN/SFU (`/turn-credentials`), প্রেজেন্স (`firechat-presence-worker.js`, ৫ মিনিট ক্রন), স্টোরেজ (`firechat-storage-worker.js`, Supabase + R2)।
5. **নোটিফিকেশন ডেলিভারি**: Firestore হয়ে স্বল্পস্থায়ী কিউ → ক্লায়েন্ট Room-এ ডেটা হিসেবে জমা দেয় → রিমোট ডকুমেন্ট ডিলিট করে।

### চিহ্নিত সমস্যা (গবেষণা থেকে মূল্যবান গ্লিচ/ঝুঁকি)
- **ফুল পোর্ট এক সেশনে অসম্ভব**: ৫৭ হাজার লাইন + Android-নির্দিষ্ট API (FCM, Room, biometric, Activity). তাই পর্যায়ক্রমিক পোর্ট যুক্তিসঙ্গত — v1 এমন পাথ যা সরাসরি ব্যাকএন্ড চুক্তির সাথে যুক্ত (লগইন, ফিড, ডিরেক্ট চ্যাট, প্রেজেন্স, থিম)।
- **রিলিজ কম্পাইল ব্রিটেলনেস** (গত কমিট `1639612 fix: resolve release Kotlin compilation errors in CI` দেখায় CI-ই ভেরিফিকেশন পয়েন্ট): তাই ডেস্কটপে কম্প্যাক্ট কোডবেস রাখা হয়েছে এবং প্রতিটি রানারে বিল্ড+প্যাকেজ CI করা হয়।
- ডেস্কটপে **কোনো ডেঞ্জারাস পারমিশন লগইন-পূর্ব লাগে না** — অ্যান্ড্রয়েড মডেল থেকে পার্থক্য এবং এটি সুবিধা।

## ডেস্কটপ পোর্ট সিদ্ধান্ত ও ভাষা

- **ভাষা**: Kotlin (Compose Multiplatform Desktop, JetBrains)। Android কোডবেসের একই ভাষা ও UI প্যারাডাইম — মানে ডিজাইন, লজিক ও ডেটা-মডেল সরাসরি ভাগ করা যায়।
- **নেটওয়ার্কিং**: Java HttpClient + kotlinx-serialization; কোনো ভারী Android SDK নেই, Firebase-এ যাওয়া হয় পাবলিক REST দিয়ে (Identity Toolkit, Firestore REST (`orderBy=timestamp desc` ফিড), RTDB REST `?auth=<idToken>` এনভেলাপ)।
- **প্যাকেজিং** (`jpackage` ভিত্তিক Compose nativeDistributions):
  - Windows: **MSI + EXE**, পার-ইউজার ইনস্টল (`%APPDATA%\ConvoChat`), Start-menu এন্ট্রি, স্থায়ী `upgradeUuid` — আপগ্রেড ঠিক থাকে।
  - Linux: **DEB + RPM**; `ubuntu-24.04-arm` রানারে **arm64 নেটিভ বিল্ড** (ক্রস-কম্পাইল নয়) — ইনস্টল সহজ (`dpkg -i`)।
  - উভয় প্যাকেজেই নিজস্ব ট্রিমড JDK রানটাইম থাকে, ব্যবহারকারীকে Java ইনস্টল করতে হয় না।

## স্ক্রিন-ভিত্তিক ফিচার ম্যাপিং

| Android (বিদ্যমান) | Desktop v1 (নতুন) | অবস্থা |
|---|---|---|
| SplashScreen (Aurora) | SplashScreen — একই ব্র্যান্ড, পালস লোগো | হয়েছে |
| LoginNova/AuthScreen | LoginScreen — email/pass + sign-up, সেশন পারসিস্টেন্স + রিফ্রেশ টোকেন | হয়েছে |
| HomeScreen ফিড | HomeScreen — Firestore `posts` (টেক্সট-অনলি) | হয়েছে |
| ChatScreen + ChatViewModel | ChatScreen — একই RTDB এনভেলাপ দিয়ে মেসেজ পাঠানো/পড়া | হয়েছে |
| PermissionCenterScreen | PermissionsScreen — প্রতিটি Android পারমিশনের OS-নেটিভ ম্যাপিং (Windows Privacy, xdg-desktop-portal) | হয়েছে |
| Theme (Light/Dark/AMOLED) | একই তিন মোড Aurora গ্রেডিয়েন্টসহ | হয়েছে |
| WebRTC কল, স্টোরি, গ্রুপ, পোল, AI | পরবর্তী ফেজ (README রোডম্যাপ) | পরিকল্পিত |

## সিকিউরিটি
- রিসোর্সে শুধু পাবলিক Firebase ওয়েব কনফিগ (google-services.json-এর একই পাবলিক মান); কোনো সার্ভিস-অ্যাকাউন্ট কী বা ওয়ার্কার সিক্রেট ডেস্কটপ মডিউলে নেই।
- সেশন শুধু ব্যবহারকারীর OS কনফিগ ডিরেক্টরিতে; রিপোজিটরিতে কিছুই লেখা হয় না।

## কীভাবে বিল্ড/ইনস্টল হবে
`desktop/README.md` এবং `.github/workflows/desktop-build.yml` দেখুন — প্রতিটি পুশে Windows/Linux-x64/Linux-arm64 বিল্ড হয়ে `Actions → artifacts`-এ MSI/EXE/DEB জমা হয়।
