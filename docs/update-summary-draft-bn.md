# First-entry update summary — draft (বাংলা)

> Release candidate copy only. Do not publish or put this in a build until the Android code compiles, a release APK is tested, and the companion Worker/Pages deployment is verified. Keep the update’s version code and release notes consistent with the signed package.

## Dialog title

**Convo Chat-এ নতুন কী**

## Short summary

Chat list-এ quick actions, আরও সহজ browser pairing, screenshot control, এবং recent images offline-এ দেখার সুবিধা।

## What’s new
- **Chat list actions:** যেকোনো conversation চেপে ধরে Pin, Mute, Archive বা Lock বেছে নিন। Archived ও locked chat আলাদা filter-এ থাকবে।
- **Web companion:** Profile menu থেকে **Open web version** চাপুন। ৯০ সেকেন্ডে মেয়াদোত্তীর্ণ একবার-ব্যবহারযোগ্য code দিয়ে নিজের browser pair হয়। Login browser restart-এর পরও থাকবে; ব্যবহার শেষে browser থেকে logout করুন বা Android থেকে সব web session revoke করুন। ব্যক্তিগত device-এ ব্যবহার করুন।
- **Screenshot control:** Privacy বা conversation settings থেকে chat screenshot protection চালু/বন্ধ করুন। Call screens সুরক্ষিত থাকে। অন্য camera দিয়ে capture ঠেকানো যায় না।
- **Offline images:** সাম্প্রতিক profile, story, feed এবং খোলা chat-এর কিছু ছবি private, bounded cache-এ রাখা/refresh করা হয়। সব পুরোনো media offline থাকবে—এমন নিশ্চয়তা নয়।

## Dialog actions

- Primary: **চ্যাট শুরু করুন** — home/chat tab খুলুন এবং tour শুরু করুন।
- Secondary: **সেটআপ গাইড** — বাংলার Web companion setup guide খুলুন।
- Dismiss: **পরে দেখব** — এই version-এর summary আর না দেখান।

## Admin release-notes field — concise version

“Long-press chat actions (pin/mute/archive/lock); configurable screenshot protection in chats; one-tap secure web pairing with persistent, revocable browser sessions; and bounded offline image cache with online refresh. Pairing codes expire in 90 seconds; persistent browser login is for trusted private devices. See the Bengali setup guide.”

## Before enabling

- Verify which destinations the existing `AppVersionWelcomeDialog` buttons open; update action labels only if wiring is implemented.
- Do not claim unlimited offline images, end-to-end encryption, or automatic expiry of browser sessions.
- Verify the web companion is actually deployed and confirm logout/revoke behavior before mentioning it in remote `releaseNotes`.
