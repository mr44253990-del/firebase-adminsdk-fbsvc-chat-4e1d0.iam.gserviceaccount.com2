# Convo Chat Desktop — Windows demo tour (স্ক্রিনশট + ভিডিও)

Windows runner-এ Convo Chat Desktop অ্যাপ চালিয়ে ডেমো অ্যাকাউন্ট দিয়ে লগইন ও পূর্ণ ট্যুর রেকর্ড করা হয়েছে (CI run 45338372423, সব জব success)।

## ডাউনলোড / দেখুন

| ফাইল | বিবরণ |
|---|---|
| [convo-desktop-demo-tour.mp4](convo-desktop-demo-tour.mp4) | সম্পূর্ণ স্ক্রিন-রেকর্ডিং (2 fps) — লগইন → হোম ফিড → App permissions → Profile & appearance (Light/AMOLED/Dark) → Chat |
| [01-login-screen.png](01-login-screen.png) | লগইন স্ক্রিন |
| [02-app-permissions.png](02-app-permissions.png) | Windows-নেটিভ Permission Center |
| [03-profile-appearance.png](03-profile-appearance.png) | Profile & appearance — লগইন অ্যাকাউন্ট দৃশ্যমান |
| [04-home-feed.png](04-home-feed.png) | হোম ফিড — আসল Firestore posts ডেটা |
| shot_0001.png … shot_0024.png | ট্যুরের প্রতিটি ধাপের ফ্রেম (2fps ভিডিওর সোর্স ফ্রেম) |

## যাচাইকৃত ফলাফল
- ✅ Windows x64 CI মেশিনে অ্যাপ বিল্ড ও রান
- ✅ ডেমো অ্যাকাউন্ট দিয়ে অটো-লগইন সফল (Firebase Identity Toolkit)
- ✅ হোম ফিডে আসল Firestore `posts` কালেকশনের ডেটা
- ✅ Light / Dark / AMOLED থিম + OS-নেটিভ পারমিশন স্ক্রিন

সম্পূর্ণ পরিবর্তনের জন্য PR #10 দেখুন।
