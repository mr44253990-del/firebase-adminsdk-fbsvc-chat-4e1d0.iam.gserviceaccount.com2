# Cloudflare Presence Worker — ৫ মিনিটের সেটআপ

## এই ফিচার কী করে

Presence probe সাধারণ notification নয়—এটি নীরব, normal-priority, data-only FCM message; কোনো banner বা শব্দ দেখায় না। Cloudflare Worker Firestore-এর `users/{uid}.fcmToken` থেকে প্রতিটি আলাদা device token নিয়ে probe পাঠায়। FCM send গ্রহণ করেছে—এটি ডিভাইস message পেয়েছে তার প্রমাণ নয়। ডিভাইসের acknowledgement `presence/{uid}`-তে লেখা হলেই সেটিকে reachable ধরা হয়। Worker সেই acknowledgement ও lease থেকে `online`, `reachable`, বা `offline` অবস্থা Firestore-এ মিলিয়ে লেখে।

এই presence ফিচারের scheduler হলো **Cloudflare Worker-এর পাঁচ মিনিটের Cron Trigger**। Firebase Functions ব্যবহার করা হয় না। একসঙ্গে একটিই presence scheduler চালু রাখুন। অ্যাপে Worker URL save করলেই Cron তৈরি বা চালু হয় না।

## যা লাগবে

- Android অ্যাপ যে Firebase project ব্যবহার করে, সেই project-এর ID।
- Cloudflare account এবং Wrangler CLI-তে login।
- Firebase Cloud Messaging API চালু করা একটি Firebase project।
- Worker-এর জন্য সীমিত অনুমতিসহ Firebase service account: Firestore read/write এবং FCM message পাঠানোর অনুমতি।
- অ্যাপের admin account-এর Firebase Auth email।

## ধাপে ধাপে

1. Repository root-এ এসে Wrangler login করুন এবং Firebase project-এর জন্য service account তৈরি করুন। Key-টি আপনার মেশিনে নিরাপদে রাখুন; কখনো Android, Firestore, Git বা public log-এ রাখবেন না।

2. Service-account JSON-টি Cloudflare-এ encrypted secret হিসেবে দিন:

   ```bash
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT --config wrangler-presence.toml
   ```

   Wrangler-এর secret prompt-এ JSON paste করুন। JSON ফাইল বা key repository-তে যোগ করবেন না, commit করবেন না, এবং কারও সঙ্গে share করবেন না।

3. চাইলে Cloudflare Worker variable `ADMIN_EMAIL`-এ Firebase Auth admin email দিন। সেট না থাকলে Worker-এ থাকা app-এর নির্ধারিত admin email ব্যবহৃত হবে।

4. Worker deploy করুন:

   ```bash
   npx wrangler deploy --config wrangler-presence.toml
   ```

   `wrangler-presence.toml`-এ `*/5 * * * *` Cron Trigger সেট করা আছে। Deploy-এর পরে Cloudflare Dashboard → Worker → Settings/Triggers-এ trigger-টি enabled আছে কি না নিশ্চিত করুন। আগে Firebase-এর কোনো scheduled presence job deploy করা হয়ে থাকলে সেটি Cloud Scheduler থেকে আলাদাভাবে disable/delete করুন—source code সরালেই deploy করা schedule মুছে যায় না। সবশেষে ঠিক একটি presence schedule সক্রিয় আছে নিশ্চিত করুন।

5. `https://<worker-host>/health` খুলে `ok: true` দেখুন। এটি secret parse করা যায় কি না যাচাই করে; Firebase/FCM permission সত্যিই কাজ করছে কি না জানতে Cron run বা admin-এর manual probe দরকার। Worker URL যেকোনো valid HTTPS host ও path হতে পারে।

6. Android অ্যাপে Admin Control Center → **Presence / 5-min probe**-এ Worker-এর HTTPS base URL দিন—শেষে `/health` দেবেন না। **Save URL → Test → Refresh Firebase user list** চাপুন। URL save করা Cron schedule করে না; Cloudflare Cron Trigger-টি আলাদাভাবে enabled থাকতে হবে।

## Security ও delivery সীমাবদ্ধতা

- Service-account JSON/key শুধু Cloudflare Worker secret-এ থাকবে। Android, Firestore, Git, `.env` বা public log-এ নয়।
- FCM acceptance কখনো device receipt-এর প্রমাণ নয়। কেবল app-এর device acknowledgement reachability হিসেবে গণ্য হয়।
- Android/FCM-এর Doze, battery, network restriction, permission এবং ফোন-নির্মাতার আচরণে delivery দেরি হতে বা বাদ পড়তে পারে। পাঁচ মিনিটে probe schedule হলেও প্রতিটি ফোন ঠিক পাঁচ মিনিটে জাগবে—এমন নিশ্চয়তা নেই।
- Chat ও call message-এর FCM gateway আলাদা; presence probe শুধু Firestore-এর per-device token ব্যবহার করে, topic fanout নয়। Worker API FCM token ফেরত দেয় না এবং probe log-এ token লেখে না।
- ঠিক একটি presence Cron সক্রিয় রাখুন; duplicate schedule চালালে একই ডিভাইসে একাধিক probe যাবে।

বিস্তারিত ইংরেজি নির্দেশনা: [presence-worker.md](presence-worker.md)।
