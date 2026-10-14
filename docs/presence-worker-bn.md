# ৫ মিনিটের Presence Probe — বাংলা সেটআপ

## আগে পরিষ্কার করে নিন

এই ফিচারের পাঁচ মিনিটের FCM বার্তাটি **সাধারণ ব্যবহারকারীর নোটিফিকেশন নয়**। এটি `convo_presence_v1` topic-এ পাঠানো নীরব, data-only `presence_probe`; কোনো notification banner, শব্দ বা বিজ্ঞাপনী বার্তা দেখায় না। অ্যাপ সাইন-ইন হলে topic-এ নিজে subscribe করে, আর probe পেলে নিজের presence acknowledgement লেখে। Android/FCM-এর Doze, ব্যাটারি, নেটওয়ার্ক ও ফোন-নির্মাতার নিয়মে উত্তর দেরি হতে বা বাদ পড়তে পারে—এটি ঠিক পাঁচ মিনিটে প্রতিটি ফোন জাগানোর নিশ্চয়তা নয়।

## Cloudflare ছাড়া Firebase Functions পদ্ধতি (প্রস্তাবিত)

এই রিপোজিটরিতে Firebase Cloud Functions v2-এর বিকল্প যোগ করা হয়েছে। এটি একই topic-এ পাঁচ মিনিট পরপর probe পাঠায় এবং অ্যাডমিনের presence তালিকার API-ও দেয়; ফলে **শুধু এই presence-monitor ফিচারের জন্য Cloudflare Worker বাধ্যতামূলক নয়**। অ্যাপের chat/call FCM gateway, Storage Worker বা অন্য Cloudflare সেবা এই পরিবর্তনে স্বয়ংক্রিয়ভাবে Firebase-এ যায় না।

### কী লাগবে

- একই Firebase project যেটি Android অ্যাপ ব্যবহার করে। Project ID Android-এর `google-services.json`-এ দেখা যায়।
- Firebase CLI, Node.js 22 এবং এই রিপোজিটরির `functions/` ফোল্ডার।
- Scheduled Functions চালাতে Firebase project-এ billing-enabled **Blaze plan** লাগতে পারে এবং Cloud Scheduler API সক্রিয় থাকতে হবে। Deploy-এর আগে Google Cloud Billing ও Scheduler API যাচাই করুন। Cloud Scheduler-এর মূল্য/ফ্রি allowance বদলাতে পারে; বর্তমান খরচ Firebase pricing calculator-এ দেখুন।
- একই `ADMIN_EMAIL`—ডিফল্টটি অ্যাপের বর্তমান admin account-এর সঙ্গে মেলে। Deploy-এ CLI value চাইলে সেই Firebase Auth admin email দিন।

### ধাপে ধাপে

1. Terminal-এ repository root-এ যান; Firebase CLI-তে login করে Android অ্যাপের সঠিক project বেছে নিন:

   ```bash
   npm install -g firebase-tools
   firebase login
   firebase projects:list
   firebase use --add
   ```

2. Functions dependencies lockfile অনুযায়ী ইনস্টল করুন:

   ```bash
   npm --prefix functions ci
   ```

3. Firebase Console / Google Cloud Console-এ ওই project-এর billing plan এবং **Cloud Scheduler API** সক্রিয় আছে কি না নিশ্চিত করুন। Firebase Console-এ Cloud Messaging চালু রাখুন।

4. Functions deploy করুন:

   ```bash
   firebase deploy --only functions
   ```

   Deploy হলে `presenceProbeEveryFiveMinutes` schedule-টি `*/5 * * * *`-এ তৈরি হবে; `presenceAdmin` হবে অ্যাডমিনের HTTPS API। CLI deploy শেষে function URL দেখাবে। Region `asia-south1`।

5. Admin Control Center → **Presence / 5-min probe**-এ `presenceAdmin`-এর **base URL** দিন, যেমন:

   ```text
   https://asia-south1-YOUR_PROJECT_ID.cloudfunctions.net/presenceAdmin
   ```

   `/health` বা `/admin/users` URL-এর শেষে যোগ করবেন না। **Save URL → Test → Refresh Firebase user list** চাপুন। Health response ঠিক থাকলে dashboard-এ probe time ও Online/Reachable status দেখা যাবে।

6. Google Cloud Console → Cloud Scheduler-এ job-টি `*/5 * * * *` schedule-এ সক্রিয় আছে কি না দেখুন। Firebase Functions logs-এ `Five-minute FCM presence probe submitted` লগও যাচাই করতে পারেন.

7. আগে Cloudflare `firechat-presence-worker`-এর Cron Trigger চালু থাকলে, Firebase scheduler যাচাই করার পর **Cloudflare dashboard-এ ওই Cron Trigger বন্ধ করুন**। একই সঙ্গে দুই scheduler চালু থাকলে probe দ্বিগুণ পাঠানো হবে. API URL-কে Firebase Function URL-এ বদলালে user-list API-ও Firebase Functions-এ চলে যায়.

### Environment variable / key সম্পর্কে

- Android-এর Firebase `apiKey`, `projectId` বা `google-services.json`-এর client config **server-side FCM পাঠানোর অনুমতি নয়**। এগুলো দিয়ে শুধু পাঁচ মিনিটের schedule নিজে থেকে তৈরি হয় না.
- এই Functions কোড `firebase-admin` ব্যবহার করে; deployed Function Google/Firebase-এর runtime credentials পায়। তাই Firebase service-account JSON-কে Android, Firestore, GitHub বা `.env` ফাইলে কপি করার দরকার নেই। Repo-তে এমন private key যোগ করবেন না.
- Admin API-তে Firebase ID token যাচাই হয়; শুধু নির্ধারিত admin email `/admin/users` এবং `/admin/probe` ব্যবহার করতে পারে। `/health` শুধু readiness তথ্য দেয়.

## বিকল্প: আগের Cloudflare Worker

Firebase Functions deploy করতে না চাইলে আগের `firechat-presence-worker.js` + Cloudflare Cron পদ্ধতি চালু রাখা যায়। তার setup [presence-worker.md](presence-worker.md)-তে আছে। Admin page-এ Worker base URL রাখুন এবং Firebase scheduled function deploy করবেন না—**একটি provider-এর একটি schedule-ই চালু রাখুন**.

## যদি আপনি সত্যিকারের visible notification বোঝান

বর্তমান probe ইচ্ছাকৃতভাবে silent। প্রতি ৫ মিনিটে banner/শব্দসহ notification পাঠানো আলাদা broadcast/reminder feature; সেটি এই probe-এর আচরণ নয়। কারা পাবে, কী message দেখাবে, কতক্ষণ চলবে, opt-out/consent কী হবে—এসব নির্দিষ্ট না করে visible alert চালু করবেন না.

## অফিসিয়াল রেফারেন্স

- [Firebase scheduled functions](https://firebase.google.com/docs/functions/schedule-functions)
- [Cloud Functions environment এবং Firebase Admin auto-configuration](https://firebase.google.com/docs/functions/config-env)
- [Firebase Admin SDK দিয়ে FCM message পাঠানো](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk)
- [Cloud Functions supported Node.js runtime](https://firebase.google.com/docs/functions/manage-functions#node.js_version)
- [Android PeriodicWorkRequest timing](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
