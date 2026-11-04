# Convo Chat — ওয়েব কম্প্যানিয়ন সেটআপ (বাংলা)

> এই নির্দেশিকা বর্তমান source implementation-এর জন্য। এটি ব্যবহার করতে Android app এবং Cloudflare Pages/Worker deployment দুটিই আপডেট থাকতে হবে; deployment যাচাই না হওয়া পর্যন্ত live feature হিসেবে প্রচার করবেন না।

## Android থেকে এক ট্যাপে চালু করুন

1. Android-এ Convo Chat-এ সাইন ইন করুন।
2. হোম স্ক্রিনের প্রোফাইল মেনু থেকে **Open web version / ওয়েব ভার্সন খুলুন** চাপুন।
3. ব্রাউজার খুলে যাবে এবং অ্যাপ একবার-ব্যবহারযোগ্য, ৯০-সেকেন্ডের pairing code দিয়ে প্রথম sign-in সম্পন্ন করবে। কোডটি URL fragment-এ পাঠানো হয়; পেজ সেটি redeem করার আগে address bar থেকে মুছে দেয়।
4. সফল হলে আপনার বন্ধুদের তালিকা থেকে একজনকে বেছে নিয়ে টেক্সট মেসেজ পাঠাতে পারবেন।

## হাতে pairing code দিন

1. Android-এ **Profile & settings → Account security → Web companion / Link my browser** খুলুন।
2. **Link my browser** চাপুন; তৈরি হওয়া ১০-সংখ্যার কোডটি ৯০ সেকেন্ডের মধ্যে companion পেজে লিখুন। এটি একবারই ব্যবহার করা যায়।
3. শুধু নিজের নিয়ন্ত্রিত ও বিশ্বস্ত ব্রাউজারেই কোডটি ব্যবহার করুন। কাউকে code, browser session token, বা signed-in device-এ প্রবেশাধিকার দেবেন না।

## Sign-in কতদিন থাকে?

Pairing code এক-use, ৯০ সেকেন্ডে মেয়াদোত্তীর্ণ এবং redemption-এ rate-limited। Pairing সফল হলে ব্রাউজারে শুধু একটি random opaque session token রাখা হয়; Firebase ID/refresh token নয়। এই token browser-এর `localStorage`-এ থাকে, তাই tab বা browser restart-এর পরেও স্বয়ংক্রিয় sign-in সম্ভব। Session-এর স্বয়ংক্রিয় সময়সীমা নেই—এটি স্থায়ী থাকার সুবিধা এবং shared-device ঝুঁকির বিনিময়।

- নিজের ব্যক্তিগত browser/device-এ ব্যবহার করুন; shared/public computer-এ নয়।
- ব্যবহার শেষে web companion-এর **Log out** চাপুন।
- ফোনে **Revoke all web sessions** চাপলে এই companion-এর সব session বাতিল হয়।
- ফোন বা browser হারালে যত দ্রুত সম্ভব Android থেকে session revoke করুন।
- Browser compromise বা malicious script থেকে `localStorage`-এর token পুরোপুরি সুরক্ষিত নয়। তাই device lock ও trusted, updated browser ব্যবহার করুন।

## Offline ছবি ও screenshot setting

Android app profile images, stories, feed images এবং খোলা chat-এর সাম্প্রতিক media disk cache-এ রাখে ও online হলে refresh করার চেষ্টা করে। Cache app-private, আনুমানিক সর্বোচ্চ ৩৮৪ MiB এবং LRU-ভাবে পুরনো entry সরাতে পারে; তাই প্রতিটি পুরোনো ছবি সবসময় offline থাকবে—এমন নিশ্চয়তা নয়। এটি Gallery-তে manual download নয়; manual download ও স্বাভাবিক message storage আলাদা।

Android-এ screenshot protection সংবেদনশীল chat-এ আলাদাভাবে চালু করা যায়; voice/video call screen সুরক্ষিত থাকে। এটি OS-level capture block—অন্য camera দিয়ে ছবি তোলা ঠেকায় না।

## Admin/deployment checklist

- Cloudflare Pages UI source: `web-companion/web/`। Root `web-companion/` copy বদলালে Pages copy-টিও একইভাবে update করুন।
- Pages URL `/web/` serve করবে; Worker-এর `WEB_COMPANION_ORIGIN` হবে শুধু `https://<pages-host>` origin, `/web` path ছাড়া।
- Worker service credentials server-side secret হিসেবে রাখুন; browser source বা Pages build-এ কখনও দেবেন না।
- Android pairing endpoint, Worker routes, CORS allowlist, Firestore session writes এবং Pages/Worker deployment বাস্তবে পরীক্ষা করুন। Source change একা deploy প্রমাণ করে না।
- বর্তমান companion scope: friend list এবং one-to-one text messaging। Posts, media send, voice/video call এবং group chat web companion-এ আছে বলে প্রচার করবেন না।
