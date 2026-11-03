# Convo Chat — প্রচার ভিডিও স্ক্রিপ্ট (বাংলা, ৫০–৬০ সেকেন্ড)

**অবস্থা:** ড্রাফট। নতুন Android build এবং companion deployment যাচাই করার পরেই প্রকাশ করুন। ফুটেজে বাস্তব, বর্তমান UI ব্যবহার করুন; mock UI-কে live product capture বলে দেখাবেন না।

| সময় | দৃশ্য / স্ক্রিন নির্দেশনা | ভয়েসওভার |
|---|---|---|
| 0–5s | Convo Chat logo থেকে বর্তমান Home screen-এ transition | “কথা হোক সহজ, পরিচিত আর একদম আপনার মতো।” |
| 5–13s | বন্ধুর chat খুলুন; বাস্তব টেক্সট মেসেজ, ডেলিভারি অবস্থা, voice note থাকলে দেখান | “Convo Chat-এ বন্ধুদের সঙ্গে মেসেজ করুন, মুহূর্ত ভাগ করুন—সবকিছু পরিচিত এক জায়গায়।” |
| 13–20s | যাচাইকৃত voice/video call UI-তে ছোট cut; ব্যক্তিগত তথ্য blur | “প্রয়োজন হলে শুরু করুন ভয়েস বা ভিডিও কল—কথা চালিয়ে যান সামনাসামনি।” |
| 20–28s | Profile photo-সহ chat list; একটি chat-এ long-press করে Pin/Mute/Archive/Lock menu দেখান | “বন্ধুর ছবি দেখুন, আর কথোপকথন চেপে ধরে দ্রুত Pin, Mute, Archive বা Lock করুন।” |
| 28–36s | Conversation settings-এ screenshot protection toggle; toggle-on অবস্থায় OS screenshot block দেখান, বাস্তব device-এ যাচাই করলে | “সংবেদনশীল কথোপকথনে screenshot protection নিজের মতো চালু করুন। কলের স্ক্রিনও সুরক্ষিত।” |
| 36–43s | আগে cache হওয়া avatar/media দেখান; airplane mode-এ সত্যিই লোড হলে তবেই | “আগে দেখা কিছু ছবি app-এর private cache-এ থাকে, তাই সংযোগ দুর্বল হলেও আবার দেখতে সুবিধা।” |
| 43–51s | Profile menu থেকে Open web version; browser-এ প্রথম pairing success, তারপর chat list | “নিজের বিশ্বস্ত browser-এ secure pairing করুন, তারপর চাইলে auto sign-in-এ ফিরুন।” |
| 51–58s | App home + logo + verified official link | “Convo Chat—কাছের মানুষ, আরও কাছাকাছি। অফিসিয়াল লিংকে দেখে নিন।” |

## ১৫-সেকেন্ডের কাটডাউন

**দৃশ্য:** 0–4s current Home/chat, 4–8s call screen ও settings toggle, 8–12s browser pairing, 12–15s logo/official link.

**ভয়েসওভার:** “মেসেজ, মুহূর্ত আর কল—সব এক জায়গায়। Chat-এ দ্রুত actions, সংবেদনশীল কথোপকথনের screenshot setting, আর নিজের বিশ্বস্ত browser-এ secure pairing। Convo Chat—কাছের মানুষ, আরও কাছাকাছি।”

## প্রকাশের আগে যাচাই

- [ ] Android screenshot protection on/off আচরণ বাস্তব device-এ পরীক্ষা; OS capture block ছাড়া কোনো অতিরিক্ত privacy claim নয়।
- [ ] Calls-এ `FLAG_SECURE`-এর বাস্তব আচরণ পরীক্ষা।
- [ ] Offline segment-এ শুধু আগে cache হওয়া ও airplane mode-এ সফলভাবে লোড হওয়া media ব্যবহার। Cache bounded; “সব ছবি offline” বলা যাবে না।
- [ ] Companion login, logout/revoke, code expiry, Worker/Pages deployment এবং persistent session বাস্তবে পরীক্ষা। Persistent browser token থাকলে trusted-device tradeoff-ও landing page-এ স্পষ্ট রাখুন।
- [ ] App version, official URL, brand assets ও permissions-সহ final call to action যুক্ত করুন।
