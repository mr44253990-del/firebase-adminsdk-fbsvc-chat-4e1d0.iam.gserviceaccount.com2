# Convo Chat — পূর্ণাঙ্গ ফিচার রোডম্যাপ (v4.4 থেকে সামনে)

অ্যাপে এখন যা আছে (চ্যাট, গ্রুপ, স্টোরি, রিল, পোল, ভয়েস/ভিডিও/গ্রুপ কল, স্ক্রিন শেয়ার, লাইভ লোকেশন, AI Assistant, অটো AI পোস্ট, Premium, Admin প্যানেল, MFA, ব্যাকআপ, ফোন লগইন ইত্যাদি) সেগুলো বাদ দিয়ে শুধু **নতুন** জিনিস এখানে রাখা হয়েছে।

**চিহ্ন:**
- কাজের আকার: **[S]** ছোট (১–২ দিন), **[M]** মাঝারি (৩–৭ দিন), **[L]** বড় (১–৩ সপ্তাহ)।
- **[KEY]** মানে বাইরের সার্ভিস, API কী বা অ্যাকাউন্ট লাগবে।
- **[★]** মানে বেশি মূল্যবান, আগে করার মতো।

নম্বর ধরে বললেই হবে, যেমন "১, ৫, ২৩ করো"।

---

## ক. চ্যাট ও মেসেজিং

1. [★][S] **Saved / Starred messages:** যেকোনো মেসেজ স্টার করুন, সব একটা "Saved" পেজে।
2. [★][S] **চ্যাটের ভেতর Pin message:** উপরে একটা বারে পিন করা মেসেজ, ট্যাপ করলে সেখানে যায়।
3. [★][M] **Multi-select:** একসাথে অনেক মেসেজ সিলেক্ট করে Forward / Delete / Copy / Star।
4. [★][M] **Schedule message:** নির্দিষ্ট সময়ে মেসেজ পাঠানো (WorkManager + সার্ভার ব্যাকআপ)।
5. [S] **Silent message:** প্রাপক নোটিফিকেশনের শব্দ পাবে না।
6. [★][M] **View-once ছবি/ভিডিও:** একবার দেখার পর মুছে যায়, স্ক্রিনশট ব্লক।
7. [M] **চ্যাটের ভেতর Poll ও Checklist:** শেয়ার্ড টু-ডু লিস্ট, সবাই টিক দিতে পারে।
8. [S] **Rich text:** **bold**, _italic_, `code`, ~~strike~~, আর Spoiler (ট্যাপ করলে দেখা যায়)।
9. [★][M] **Chat folders:** All / Unread / Groups / Personal / Work, আর নিজের বানানো ফোল্ডার।
10. [★][S] **Archive chat:** লিস্ট পরিষ্কার রাখা; নতুন মেসেজ এলে অপশন অনুযায়ী ফেরত আসে।
11. [★][M] **Chat lock ও Hidden chats:** নির্দিষ্ট চ্যাট আঙুলের ছাপ/PIN দিয়ে লক।
12. [★][M] **Global search:** সব চ্যাটে খোঁজা, ফিল্টারসহ (মানুষ, তারিখ, ছবি/ফাইল/লিংক)।
13. [S] **Jump to date:** ক্যালেন্ডার থেকে তারিখ বেছে পুরনো মেসেজে যাওয়া।
14. [M] **গ্রুপে Thread reply:** কোনো মেসেজের নিচে আলাদা আলোচনা।
15. [S] **Reply privately:** গ্রুপের মেসেজের উত্তর ব্যক্তিগত চ্যাটে।
16. [M] **Message info:** গ্রুপে কে কখন ডেলিভারি পেল ও পড়ল।
17. [S] **Remind me:** মেসেজে লং-প্রেস করে "১ ঘণ্টা পরে মনে করাও"।
18. [M] **Message effects:** "Happy birthday" / "Congrats" লিখলে কনফেটি বা বেলুন অ্যানিমেশন।
19. [★][M] **GIF ও Sticker search:** Tenor/GIPHY থেকে খোঁজা। [KEY]
20. [★][L] **নিজের Sticker pack বানানো:** ছবি থেকে অটো ব্যাকগ্রাউন্ড রিমুভ (ML Kit Subject Segmentation)।
21. [M] **Super reactions:** অ্যানিমেটেড রিঅ্যাকশন, আর নিজের পছন্দের quick reaction সেট।
22. [S] **Contact card শেয়ার:** অ্যাপের ইউজার বা ফোনের কন্টাক্ট।
23. [M] **Draft sync:** অর্ধেক লেখা মেসেজ সব ডিভাইসে থাকবে; চ্যাট লিস্টে "Draft:" দেখাবে।
24. [M] **Message requests:** অচেনা মানুষের মেসেজ আলাদা ফোল্ডারে, Accept / Delete / Block।
25. [S] **কন্টাক্ট লিস্টে Typing / Recording:** চ্যাট লিস্টেই "typing…" বা "recording…" দেখাবে।

## খ. মিডিয়া ও ফাইল

26. [★][M] **পাঠানোর আগে Image editor:** Crop, Rotate, ড্র, টেক্সট, স্টিকার, মুখ ব্লার।
27. [★][M] **Document scanner:** ক্যামেরায় কাগজ স্ক্যান করে PDF বানিয়ে পাঠানো (ML Kit Document Scanner)।
28. [M] **Video trim ও compress:** পাঠানোর আগে কাটা, আর কোয়ালিটি বেছে নেওয়া (Data saver / HD)।
29. [★][M] **Album:** একসাথে অনেক ছবি সুন্দর গ্রিডে; সোয়াইপ করে পুরো চ্যাটের মিডিয়া দেখা।
30. [L] **বড় ফাইল (২ GB পর্যন্ত):** টুকরো টুকরো আপলোড, নেট কাটলে থেমে আবার চালু (R2 multipart)।
31. [★][S] **Auto-download rules:** Wi-Fi না মোবাইল ডেটা, প্রতিটি ধরনের জন্য আলাদা।
32. [★][M] **Storage manager:** কোন চ্যাট কত জায়গা নিচ্ছে, বড় ফাইল দেখে মুছে ফেলা।
33. [M] **Office ফাইল প্রিভিউ:** docx / xlsx / pptx অ্যাপের ভেতর।
34. [M] **ভয়েস মেসেজ:** নয়েজ কমানো, Voice changer, পাঠানোর আগে শুনে নেওয়া (preview)।
35. [M] **Video note:** গোল ভিডিও মেসেজ (Telegram-এর মতো)।
36. [S] **অডিও/মিউজিক:** ফাইল চালানোর মিনি প্লেয়ার, চ্যাট থেকে বের হলেও চলবে।

## গ. কল

37. [★][S] **Calls ট্যাব:** কল হিস্ট্রি, মিসড কল, এক ট্যাপে আবার কল, মোট সময়।
38. [★][M] **Call link:** লিংক শেয়ার করে কলে যোগ দেওয়া; আগে থেকে কল শিডিউল।
39. [★][L] **Background blur / ভার্চুয়াল ব্যাকগ্রাউন্ড** (ML Kit Selfie Segmentation)।
40. [M] **Network quality ইন্ডিকেটর:** নেট খারাপ হলে অটো অডিও-অনলি, আর "Low data mode"।
41. [M] **গ্রুপ কল:** Active speaker হাইলাইট, Grid / Spotlight লেআউট, হাত তোলা, কলের ভেতর চ্যাট।
42. [L] **Live captions ও কলের AI সারাংশ:** কথা থেকে লেখা, কল শেষে মূল পয়েন্ট। [KEY]
43. [M] **Call recording:** দুই পক্ষকে স্পষ্ট নোটিশসহ; আইনি নিয়ম মেনে।
44. [L] **Watch together:** একসাথে YouTube/ভিডিও দেখা, সিঙ্ক করা প্লে/পজ।
45. [M] **Walkie-talkie:** বাটন চেপে কথা বললে সাথে সাথে বাজবে।
46. [L] **Voice rooms (Spaces):** অনেকে মিলে অডিও আড্ডা; স্পিকার ও শ্রোতা আলাদা।

## ঘ. AI

47. [★][M] **যেকোনো চ্যাটে @AI:** গ্রুপ বা চ্যাটে মেনশন করলে উত্তর, সারাংশ বা অনুবাদ।
48. [★][M] **Catch me up:** না পড়া ১০০ মেসেজের ৫ লাইনের সারাংশ।
49. [★][S] **লেখা সুন্দর করো:** Formal / Friendly / ছোট করো / বানান ঠিক করো / বাংলা↔ইংরেজি।
50. [★][M] **ছবি বোঝা (Vision):** ছবির বর্ণনা, ছবির লেখা পড়া (OCR) ও অনুবাদ। [KEY]
51. [M] **চ্যাটে AI image ও AI sticker বানানো** (Pollinations, ফ্রি)।
52. [★][S] **ভয়েস মেসেজ থেকে লেখা (Transcribe)**। [KEY: Groq Whisper ফ্রি]
53. [L] **ভয়েস কনভারসেশন মোড:** AI-এর সাথে ফোনালাপের মতো কথা বলা।
54. [M] **AI Bot store:** Admin বিভিন্ন বট বানাবেন (পড়াশোনা, রান্না, ডাক্তারি তথ্য, কোডিং), ইউজার বেছে নেবেন।
55. [★][M] **AI moderation:** গালি, স্প্যাম, প্রতারণার লিংক, আপত্তিকর ছবি অটো ধরা ও Admin-কে জানানো।
56. [M] **Smart reminders:** "কাল ৫টায় দেখা" লিখলে অটো রিমাইন্ডার বা ক্যালেন্ডারে যোগের অপশন।
57. [M] **AI quota ও খরচ ড্যাশবোর্ড:** ফ্রি বনাম Premium লিমিট; কোন প্রোভাইডার কত রিকোয়েস্ট নিল।
58. [S] **AI personality:** ইউজার নিজের Assistant-এর নাম, কথার ধরন, ভাষা ঠিক করবে।

## ঙ. সোশ্যাল ও ফিড

59. [★][L] **Channels (ব্রডকাস্ট):** এক জন পোস্ট করবে, হাজারো সাবস্ক্রাইবার।
60. [L] **Communities:** অনেক গ্রুপ ও চ্যানেল এক ছাতার নিচে।
61. [★][M] **Story stickers:** পোল, প্রশ্ন, কাউন্টডাউন, মিউজিক, লোকেশন।
62. [M] **Close friends, Story highlights (প্রোফাইলে স্থায়ী), Story archive।**
63. [S] **Notes:** প্রোফাইল ছবির উপরে ২৪ ঘণ্টার ছোট স্ট্যাটাস বাবল।
64. [M] **Post:** শিডিউল করা, Draft, অনেক ছবির Carousel, Collab post।
65. [M] **Explore / Trending:** হ্যাশট্যাগ পেজ, জনপ্রিয় পোস্ট, আগ্রহ অনুযায়ী সাজানো।
66. [M] **Events:** তারিখ, স্থান, RSVP (যাবো / হয়তো / না), রিমাইন্ডার।
67. [L] **Live streaming:** লাইভ ভিডিও, লাইভ কমেন্ট, গিফট। [KEY: Cloudflare Stream বা SFU]
68. [M] **Reels editor:** মিউজিক, টেক্সট, স্পিড, অটো ক্যাপশন, Duet / Remix।
69. [S] **প্রোফাইল:** পিন করা পোস্ট, প্রোফাইল মিউজিক, অ্যানিমেটেড ভেরিফায়েড ব্যাজ।
70. [M] **Achievements ও Streak:** টানা চ্যাট স্ট্রিক, ব্যাজ, লেভেল।

## চ. প্রাইভেসি ও নিরাপত্তা

71. [★][M] **App lock:** পুরো অ্যাপে আঙুলের ছাপ/PIN, ব্যাকগ্রাউন্ডে প্রিভিউ ঝাপসা।
72. [★][S] **Privacy controls:** Last seen, প্রোফাইল ছবি, Bio, Read receipt কে দেখবে (সবাই / বন্ধু / কেউ না)।
73. [★][M] **Account deletion ও Data export:** Play Store-এ রাখার জন্য বাধ্যতামূলক।
74. [M] **Passkey লগইন:** পাসওয়ার্ড ছাড়া, ফোনের লক দিয়েই।
75. [S] **নতুন ডিভাইসে লগইন হলে ই-মেইল/পুশ অ্যালার্ট**, আর "এটা আমি না" বাটন।
76. [M] **Restrict:** কাউকে ব্লক না করে সীমিত রাখা; তার কমেন্ট শুধু সে নিজে দেখবে।
77. [★][L] **এনক্রিপ্টেড ক্লাউড ব্যাকআপ:** Google Drive-এ পাসওয়ার্ড দিয়ে, প্রতিদিন অটো।
78. [L] **End-to-end encryption** (Signal protocol)। সতর্কতা: সার্ভার AI, মডারেশন আর ক্লাউড টেক্সট ফিচারের সাথে সংঘাত হবে, তাই আলাদা "Secret chat" মোড হিসেবে করা ভালো।
79. [S] **Incognito keyboard:** কিবোর্ড যেন টাইপ করা লেখা শিখে না রাখে।

## ছ. ডিজাইন ও ব্যবহার (UI/UX)

80. [★][M] **Material You:** ফোনের ওয়ালপেপার থেকে রং, নিজের Accent রং, OLED কালো থিম।
81. [★][M] **Notification আপগ্রেড:** নোটিফিকেশন থেকেই Reply / Mark as read / Like; প্রতি চ্যাটে আলাদা শব্দ ও ভাইব্রেশন।
82. [★][S] **চ্যাট লিস্টে Swipe actions:** বামে Archive, ডানে Pin / Mute / Read।
83. [M] **Home screen widget:** সাম্প্রতিক চ্যাট, না পড়া মেসেজ সংখ্যা।
84. [M] **Android Chat bubbles:** ভাসমান চ্যাট মাথা (Bubbles API)।
85. [S] **App shortcuts:** আইকনে লং-প্রেস করলে প্রিয় মানুষ; Quick settings tile।
86. [M] **Shared-element অ্যানিমেশন:** প্রোফাইল ছবি থেকে প্রোফাইল পেজ, ছবি থেকে ফুলস্ক্রিন।
87. [M] **Tablet ও Foldable:** দুই ভাগের লেআউট (বামে লিস্ট, ডানে চ্যাট)।
88. [S] **বাংলা ফন্ট বেছে নেওয়া** (Hind Siliguri, Noto Sans Bengali), আর পুরো অ্যাপের লেখার সাইজ।
89. [M] **Accessibility:** TalkBack লেবেল, হাই কনট্রাস্ট, কম অ্যানিমেশন মোড।
90. [S] **Skeleton loading**, সুন্দর Empty state, অফলাইন ব্যানার।
91. [M] **Command palette / Universal search:** এক জায়গায় মানুষ, মেসেজ, সেটিংস।
92. [M] **Chat wallpaper store:** Parallax, ব্লার, ডিম করা, দিন-রাত অনুযায়ী বদলানো।
93. [L] **Wear OS অ্যাপ ও Android Auto:** ঘড়ি থেকে রিপ্লাই, গাড়িতে মেসেজ পড়ে শোনানো।
94. [L] **Web / Desktop ভার্সন:** QR স্ক্যান করে লগইন (Linked devices)।

## জ. টুলস ও প্রোডাক্টিভিটি

95. [M] **Shared notes ও Whiteboard:** চ্যাটের ভেতর একসাথে লেখা ও আঁকা।
96. [★][M] **Bill split:** গ্রুপে খরচ ভাগ, কে কত দেবে তার হিসাব।
97. [M] **মিনি গেম:** Tic-tac-toe, Ludo, কুইজ — চ্যাটে বন্ধুর সাথে।
98. [M] **নিজস্ব Drive:** নিজের ফাইল রাখার ক্লাউড জায়গা (Premium-এ বেশি)।
99. [S] **Universal QR scanner:** লিংক, Wi-Fi, কন্টাক্ট, পেমেন্ট QR।
100. [L] **Bot API ও Webhook:** ডেভেলপাররা নিজের বট বানাতে পারবে।
101. [M] **Shared calendar ও Reminders:** গ্রুপের জন্য।

## ঝ. Admin প্যানেল

102. [★][M] **Analytics:** DAU / MAU, রিটেনশন, নতুন ইউজার, কোন ফিচার কত ব্যবহার, ক্র্যাশ রেট।
103. [★][M] **Push campaign:** নির্দিষ্ট গ্রুপকে (দেশ, ভার্সন, Premium, নিষ্ক্রিয়) শিডিউল করে নোটিফিকেশন।
104. [★][S] **Force update:** সর্বনিম্ন ভার্সন ঠিক করা; পুরনো অ্যাপে আপডেট স্ক্রিন।
105. [M] **Feature flag ও A/B test:** নতুন ফিচার প্রথমে ১০% ইউজারকে।
106. [★][M] **Moderation queue:** AI আগে বাছাই করবে; রিপোর্ট, Ban appeal, Keyword filter।
107. [M] **সার্ভার খরচ ও লিমিট মনিটর:** Cloudflare, Supabase, Firebase; লিমিটের কাছে গেলে অ্যালার্ট।
108. [M] **অটো হেলথ চেক অ্যালার্ট:** Worker ডাউন হলে Admin-কে ই-মেইল বা Telegram।
109. [S] **Survey ও Feedback:** ইউজারদের মতামত সংগ্রহ, স্টার রেটিং।
110. [M] **Scheduled Firestore backup** ও এক ক্লিকে রিস্টোর।

## ঞ. Premium ও আয়

111. [★][L] **bKash / Nagad / SSLCommerz** দিয়ে Premium কেনা। [KEY: মার্চেন্ট অ্যাকাউন্ট]
112. [M] **Google Play Billing:** সাবস্ক্রিপশন।
113. [M] **Premium সুবিধা:** কাস্টম অ্যাপ আইকন, এক্সক্লুসিভ থিম, বড় ফাইল, বেশি AI, অ্যানিমেটেড প্রোফাইল।
114. [M] **Referral:** বন্ধু আনলে ফ্রি Premium দিন।
115. [L] **Creator tips ও লাইভে গিফট/কয়েন।**

## ট. টেকনিক্যাল মান ও পারফরম্যান্স

116. [★][M] **Crashlytics + ANR মনিটর:** ইউজারের ফোনে ক্র্যাশ হলে সাথে সাথে জানা।
117. [★][M] **R8 minify চালু ও ABI split:** APK অনেক ছোট ও দ্রুত (এখন বন্ধ আছে)।
118. [M] **Baseline profile:** অ্যাপ দ্রুত খোলা।
119. [M] **Paging 3:** হাজার মেসেজের চ্যাটও মসৃণ।
120. [M] **WorkManager:** মেসেজ ও আপলোড নির্ভরযোগ্যভাবে পাঠানো (অ্যাপ বন্ধ থাকলেও)।
121. [★][M] **টেস্ট অ্যাকাউন্ট দিয়ে CI স্মোক টেস্ট:** লগইন করে Home, চ্যাট, কল স্ক্রিন পর্যন্ত অটো চেক।
122. [S] **Deprecated API আপডেট:** ClickableText, AutoMirrored আইকন (CI-তে warning আসছে)।
123. [L] **Modularization:** বড় ফাইলগুলো (HomeScreen, ChatViewModel) ভাগ করা, build দ্রুত।
124. [M] **Play Store রিলিজ পাইপলাইন:** অটো সাইন করে Internal testing ট্র্যাকে আপলোড।

---

## কোন ক্রমে করলে ভালো (প্রস্তাবিত ধাপ)

### ধাপ ১ — দ্রুত ও বেশি প্রভাব (~২ সপ্তাহ)
1, 2, 3, 10, 12, 31, 37, 49, 52, 72, 73, 82, 104, 116, 122

### ধাপ ২ — ইউজার ধরে রাখার ফিচার (~৩–৪ সপ্তাহ)
4, 6, 9, 11, 19, 26, 27, 29, 32, 47, 48, 55, 71, 80, 81, 102, 103, 117

### ধাপ ৩ — বড় আকর্ষণ (~১–২ মাস)
20, 38, 39, 41, 50, 59, 61, 66, 77, 96, 106, 111, 121

### ধাপ ৪ — দীর্ঘমেয়াদি
42, 44, 46, 53, 60, 67, 78, 93, 94, 100, 123

---

## v4.4.2 বাস্তবায়ন-অবস্থা

**এই রিলিজ সিরিজে যোগ হয়েছে:** #1 Saved messages, #2 Pin message, #3 Multi-select, #10 Archive, #11 Chat lock, #17 Message reminders, #27 Document scanner, #31 Auto-download rules, #48 Catch-me-up summary, #49 AI writing tools, #63 24-hour friend Notes, #85 App shortcuts।

**আংশিক—রোডম্যাপের সম্পূর্ণ scope নয়:** #12 search এই ফোনে cache হওয়া Room messages-এ; server-side পুরনো cloud history নয়। #32 storage manager অ্যাপের DownloadManager-এ তৈরি Downloads দেখায়; প্রতি-chat cache বা database usage breakdown নয়। #37 call history এই মুহূর্তে 1:1 কলের local history; group calls/সিঙ্ক পরের কাজ। #11 lock-এর জন্য Android secure screen lock বা biometrics দরকার; lock preferences device-local।

**এখনও বাকি:** প্রথম ধাপের #52 voice-message transcription, #73 account deletion/data export, #82 swipe actions, #116 Crashlytics এবং #122 deprecated API cleanup-ও এই series-এ শেষ হয়নি। ১২৪টি আইডিয়া একটি দীর্ঘমেয়াদি backlog; সব এক release-এ সম্পন্ন বলা হচ্ছে না। যেসব ফিচারে merchant account, cloud storage/stream, provider key, Play Console বা account-data erasure backend দরকার, সেগুলোর setup আগে লাগবে।

## v4.4.3 UI/verification update

v4.4.3-তে নতুন UI backlog item #125–#131-এর targeted অংশ যোগ করা হয়েছে: chat tools collapse menu, full-screen insets, searchable/filterable 1:1 call history with callback, Smart system/wallpaper theme, animated local-search loading, SMS one-time-code autofill hint/clipboard paste, এবং existing 2-step code-entry paste convenience। আগের 124-item roadmap সম্পূর্ণ হয়েছে—এমন দাবি নয়। নতুন পরবর্তী পর্যায়ের 100-item প্রস্তাব `docs/roadmap-v4.5-to-v5.0-bn.md`-এ। Release caveat: CI outcome এবং actual deploy সম্পন্ন হলে সংশ্লিষ্ট status আপডেট করতে হবে।
