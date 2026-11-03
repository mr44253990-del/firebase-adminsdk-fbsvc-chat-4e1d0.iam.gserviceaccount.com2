# Convo Chat — প্রচার ভিডিও স্ক্রিপ্ট (বাংলা, ৫০–৬০ সেকেন্ড)

**অবস্থা:** ড্রাফট। নতুন Android build, Worker ও Pages deployment, এবং প্রতিটি দেখানো feature যাচাই করার পরেই প্রকাশ করুন। ফুটেজে বাস্তব, বর্তমান UI ব্যবহার করুন; তৈরি/মক UI-কে live product capture বলে দেখাবেন না।

| সময় | দৃশ্য / স্ক্রিন নির্দেশনা | ভয়েসওভার |
|---|---|---|
| 0–5s | নীল-বেগুনি liquid-glass আলো থেকে Convo Chat logo ও Home screen-এ transition | “কথা হোক সহজ, সুন্দর আর একদম আপনার মতো।” |
| 5–13s | বন্ধুর chat খুলুন; কয়েকটি বাস্তব টেক্সট মেসেজ, ডেলিভারি অবস্থা, voice note থাকলে সেটি দেখান | “Convo Chat-এ বন্ধুদের সঙ্গে মেসেজ করুন, মুহূর্ত ভাগ করুন—সবকিছু থাকে পরিচিত, দ্রুত এক জায়গায়।” |
| 13–20s | অ্যাপের যাচাইকৃত voice/video call UI-তে ছোট cut; ব্যক্তিগত তথ্য blur | “প্রয়োজন হলে শুরু করুন ভয়েস বা ভিডিও কল—কথা চালিয়ে যান সামনাসামনি।” |
| 20–28s | Liquid-glass backdrop, chat row profile photo এবং translucent settings card-এর close-up | “নীল-বেগুনি liquid-glass ডিজাইন, আর পরিচিত মানুষের ছবি—চ্যাটকে করে তোলে আরও আপন।” |
| 28–36s | Conversation settings-এ screenshot protection toggle; toggle on অবস্থায় Android screenshot attempt-এর OS block দেখান, কেবল বাস্তবে যাচাই করলে | “সংবেদনশীল কথোপকথনে screenshot protection নিজের মতো চালু করুন। কলের স্ক্রিনও সুরক্ষিত।” |
| 36–43s | Wi-Fi disconnect করে আগে cache হওয়া avatar/media দেখান; শুধু সফলভাবে যাচাই করা image দেখাতে হবে | “আগে দেখা কিছু ছবি app-এর private cache-এ থাকে, তাই সংযোগ দুর্বল হলেও আবার দেখতে সুবিধা।” |
| 43–51s | Profile menu থেকে Open web version; browser-এ প্রথম pairing success, তারপর chat list | “নিজের বিশ্বস্ত browser-এ একবার secure pairing করুন, তারপর চাইলে স্বয়ংক্রিয় sign-in-এ ফিরুন।” |
| 51–58s | App home + logo + store/official link card | “Convo Chat—কাছের মানুষ, আরও কাছাকাছি। অফিসিয়াল লিংকে দেখে নিন।” |

## ১৫-সেকেন্ডের কাটডাউন

**দৃশ্য:** 0–4s glass home/chat, 4–8s call screen ও settings toggle, 8–12s browser pairing, 12–15s logo/official link.

**ভয়েসওভার:** “মেসেজ, মুহূর্ত আর কল—সব এক জায়গায়। নিজের মতো liquid-glass look, সংবেদনশীল chat-এর screenshot setting, আর বিশ্বস্ত browser-এ secure pairing। Convo Chat—কাছের মানুষ, আরও কাছাকাছি। অফিসিয়াল লিংকে দেখুন।”

## প্রকাশের আগে যাচাই

- [ ] Android screenshot protection on/off আচরণ বাস্তব device-এ পরীক্ষা; OS capture block ছাড়া কোনো অতিরিক্ত privacy claim নয়।
- [ ] Calls-এ `FLAG_SECURE`-এর বাস্তব আচরণ পরীক্ষা।
- [ ] Offline segment-এ শুধু আগে cache হওয়া ও airplane mode-এ সফলভাবে লোড হওয়া media ব্যবহার। Cache bounded; “সব ছবি offline” বলা যাবে না।
- [ ] Companion login ও logout/revoke flow, code expiry, Worker/Pages deployment এবং session persistence পরীক্ষা। Persistent browser token থাকলে trusted-device tradeoff-ও landing page-এ স্পষ্ট রাখুন।
- [ ] App version, official URL, brand assets ও permission-সহ final call to action যুক্ত করুন।
