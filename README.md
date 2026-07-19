# FireChat (ফায়ারচ্যাট) Setup & Configuration Guide

## Premium Aurora UI

The app now uses a unified **Glassmorphism + Material 3** design system across onboarding, authentication, feed, stories, direct chat, group chat, admin, profile, dialogs, cards, and navigation.

- Aurora gradient canvas with translucent glass surfaces and soft ambient depth
- Large 24–32dp corners, pill controls, refined type hierarchy, and accessible touch targets
- Material You wallpaper colors on Android 12+ through the **Dynamic** appearance mode
- Explicit **Light**, **Dark**, and true **AMOLED** appearance modes plus themed palettes
- Edge-to-edge layouts, animated state transitions, premium loading/disabled states, and consistent component styling

Choose an appearance from **Profile & appearance → Choose Application Theme**.

### Social system configuration

1. Enable **Google** in Firebase Authentication → Sign-in providers and add the app's SHA-1/SHA-256 fingerprints.
2. Replace/publish the included `firestore.rules` before testing activity notifications, friend requests, message requests, profiles, posts, stories, or groups.
3. Notifications use Firestore as a short-lived delivery queue. The Android client writes each received item to Room and then deletes the remote notification document, keeping history locally without growing Firestore indefinitely.
4. Friend and message request queries use a single `receiverId` equality filter, so no composite Firestore index is required.
5. Google provides display name, email, and profile image. Birthday is not included in the standard Google ID token; users can complete it safely from **Profile & appearance**.
6. Redeploy `cloudflare-worker.js` after this update. The authenticated sender reads the recipient's `users/{uid}.fcmToken` routing field and includes that token with activity type, target ID, sender name, and public profile image URL. Worker `3.2.0` performs no Firestore lookup; it validates the caller's Firebase ID token and sends directly through FCM v1.

### Direct FCM v1 gateway — no n8n

FireChat no longer requires an n8n webhook. The Android client calls the included Cloudflare Worker directly. The Worker exchanges its encrypted service-account credential for an OAuth token and calls FCM v1.

1. Revoke every service-account key ever pasted into chat, source code, an APK, or a public repository.
2. Generate a fresh Firebase service-account JSON key.
3. In Cloudflare Worker → Settings → Variables, create an **encrypted secret** named `FIREBASE_SERVICE_ACCOUNT` and paste the fresh JSON there. Never place it in Android resources, BuildConfig, `.env`, `google-services.json`, or Git.
4. Deploy the current `cloudflare-worker.js`. It sends data-only high-priority messages so Android applies separate Message, Request, and Activity channels with custom vibration and profile imagery.
5. FireChat defaults to `https://solitary-hill-dcdc.mr44253990.workers.dev/`. The Admin Service panel can run `/health`, save a replacement URL, and send a self-test notification.

A safe deployment helper is included:

```bash
export CLOUDFLARE_ACCOUNT_ID='your-account-id'
export CLOUDFLARE_API_TOKEN='a Workers Scripts:Edit token'
export FIREBASE_SERVICE_ACCOUNT_FILE='/absolute/path/to/a-fresh-service-account.json'
./deploy-firechat-worker.sh
```

The new `firechat-fcm-worker.js` supports public health diagnostics and authenticated POST delivery. Use `./test-firechat-worker.sh` for cURL-based health/authenticated tests. Never put either credential in these scripts.

The explicit Google button uses `GetSignInWithGoogleOption`, which always requests the account chooser. Ensure Firebase Authentication → Google is enabled, the correct Web OAuth client exists, and debug/release SHA-1 and SHA-256 fingerprints are registered before downloading a fresh `google-services.json`.

### Ephemeral chat delivery

Direct messages use RTDB as a delivery envelope and Room as device-owned history. The sender stores a local copy immediately. When the receiver opens the conversation, FireChat stores the incoming text/image/voice metadata locally, writes a lightweight seen receipt for the sender, and removes the delivered message envelope from RTDB. Receipt documents are removed after the sender caches the seen state. A non-destructive Room `2 → 3` migration preserves existing local conversations during app updates.

Remote Supabase media files are intentionally not deleted during acknowledgement. Removing a media object before a verified local file download would break cached image and voice messages. A future server-side retention job may remove media only after both devices submit durable-download acknowledgements.

স্বাগতম! **FireChat** হলো একটি রিয়েল-টাইম চ্যাটিং অ্যান্ড্রয়েড অ্যাপ্লিকেশন যা Jetpack Compose, Kotlin এবং Firebase (Authentication, Firestore, Realtime Database) ব্যবহার করে তৈরি করা হয়েছে। ব্যাকগ্রাউন্ডে এবং অ্যাপ বন্ধ থাকা অবস্থায়ও নোটিফিকেশন পাঠাতে এটি **Cloudflare Workers** এবং **Firebase Cloud Messaging (FCM) v1 API** এর সফল সংযোগ ব্যবহার করে।

এই নির্দেশিকায় সম্পূর্ণ সেটআপ প্রসেস (ফায়ারবেস কনফিগারেশন থেকে শুরু করে ক্লাউডফ্লেয়ার ওয়ার্কার ডিপ্লয়মেন্ট) বিস্তারিত আলোচনা করা হলো।

---

## ১. ফায়ারবেস কনসোল কনফিগারেশন (Firebase Console Setup)

প্রথমে আপনার অ্যাপ্লিকেশনের জন্য একটি ফায়ারবেস প্রজেক্ট তৈরি করতে হবে:

১. **ফায়ারবেস কনসোলে যান**: [Firebase Console](https://console.firebase.google.com/)-এ গিয়ে একটি নতুন প্রজেক্ট তৈরি করুন।
২. **অ্যান্ড্রয়েড অ্যাপ যুক্ত করুন**: প্রজেক্টে অ্যান্ড্রয়েড আইকন সিলেক্ট করে নতুন অ্যাপ যুক্ত করুন।
   - **Package Name (Application ID)**: আপনার অ্যাপের প্যাকেজ নেম ব্যবহার করুন। এই অ্যাপে প্যাকেজ নেমটি কনফিগার করা আছে:
     `com.aistudio.firechat.nzvjqp`
৩. **`google-services.json` ডাউনলোড করুন**: কনফিগারেশন শেষে `google-services.json` ফাইলটি ডাউনলোড করে আপনার অ্যান্ড্রোয়েড প্রজেক্টের এই ফোল্ডারে পেস্ট করুন:
   `[Project Root]/app/google-services.json`

---

## ২. ফায়ারবেস সার্ভিসসমূহ সেটআপ এবং সিকিউরিটি রুলস (Firebase Services & Rules)

### ক. Firebase Authentication
- ফায়ারবেস ড্যাশবোর্ডে **Build > Authentication**-এ যান।
- **Get Started**-এ ক্লিক করে **Sign-in method** ট্যাব থেকে **Email/Password** প্রোভাইডারটি **Enable** করুন।

### খ. Cloud Firestore Database (ইউজার প্রোফাইল সংরক্ষণের জন্য)
- **Build > Firestore Database**-এ গিয়ে **Create Database** ক্লিক করুন।
- **Start in test mode** বা Production সিলেক্ট করে ডাটাবেজ তৈরি করুন।
- **Rules** ট্যাবে গিয়ে নিচের রুলসটি আপডেট করুন এবং **Publish** করুন:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      // যেকোনো অথেনটিকেটেড ইউজার ডাটা পড়তে এবং লিখতে পারবে
      allow read, write: if request.auth != null;
    }
  }
}
```

### গ. Firebase Realtime Database (রিয়েল-টাইম চ্যাটের জন্য)
- **Build > Realtime Database**-এ গিয়ে **Create Database** ক্লিক করুন।
- ডাটাবেজের লোকোশন সিলেক্ট করে তৈরি করুন।
- **Rules** ট্যাবে গিয়ে নিচের রুলসটি পরিবর্তন করুন এবং **Publish** করুন:

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    "chats": {
      "$chatId": {
        "messages": {
          ".indexOn": ["timestamp"]
        }
      }
    }
  }
}
```

---

## ৩. ক্লাউডফ্লেয়ার ওয়ার্কার সেটআপ (Cloudflare Workers Setup)

চ্যাট মেসেজ পাঠানোর সাথে সাথে ব্যাকগ্রাউন্ড নোটিফিকেশন পাঠাতে ক্লাউডফ্লেয়ার ওয়ার্কার তৈরি করা প্রয়োজন। এটি Firebase FCM v1 API কল করবে।

### ক. Firebase Service Account Private Key তৈরি
১. ফায়ারবেস কনসোলে প্রজেক্টের পাশে থাকা গিয়ার আইকনে ক্লিক করে **Project Settings**-এ যান।
২. **Service accounts** ট্যাবে ক্লিক করুন।
৩. নিচে থাকা **Generate new private key** বাটনে ক্লিক করুন।
৪. একটি `.json` কী ফাইল ডাউনলোড হবে। ফাইলটি অত্যন্ত সুরক্ষিত রাখুন। এই ফাইলের ভেতরের সম্পূর্ণ কোডটি আমাদের ক্লাউডফ্লেয়ারের সিক্রেটস-এ ব্যবহার করতে হবে।

### খ. ক্লাউডফ্লেয়ার ওয়ার্কার তৈরি ও কোড ডিপ্লয়মেন্ট
১. আপনার [Cloudflare Dashboard](https://dash.cloudflare.com/)-এ লগইন করুন।
২. বামদিকের মেনু থেকে **Workers & Pages**-এ গিয়ে **Create Application** এবং তারপর **Create Worker** বাটনে ক্লিক করুন।
৩. ওয়ার্কারটির নাম দিন (যেমন: `firechat-notif-worker`) এবং **Deploy**-এ ক্লিক করুন।
৪. ডেপ্লয় হওয়ার পর **Edit code** বাটনে ক্লিক করুন।
৫. অ্যান্ড্রোয়েড প্রজেক্টের রুটে থাকা `cloudflare-worker.js` ফাইলের সমস্ত কোড কপি করে ওয়ার্কারের এডিটর বক্সে পেস্ট করুন।
৬. ডানদিকের উপরে থাকা **Save and Deploy** বাটনে ক্লিক করুন।

### গ. ওয়ার্কার এনভায়রনমেন্ট ভেরিয়েবল (Secret Environment Variable) কনফিগারেশন
১. আপনার ক্লাউডফ্লেয়ার ওয়ার্কার ড্যাশবোর্ডে ফিরে যান (যেমন: `firechat-notif-worker` এর সেটিংস পেজে)।
২. **Settings** ট্যাব থেকে **Variables** অপশনে যান।
৩. **Environment Variables** সেকশনে গিয়ে **Add Secret** বাটনে ক্লিক করুন।
৪. সিক্রেটের বিস্তারিত:
   - **Variable Name (Key)**: `FIREBASE_SERVICE_ACCOUNT`
   - **Value**: ফায়ারবেস থেকে ডাউনলোড করা সার্ভিস অ্যাকাউন্ট `.json` ফাইলের ভেতরে থাকা **সম্পূর্ণ কন্টেন্ট/লেখা কপি করে** হুবহু এখানে পেস্ট করুন।
৫. **Encrypt and Save** বাটনে ক্লিক করুন।

---

## ৪. অ্যাপ্লিকেশনে ওয়ার্কার ইন্টিগ্রেশন ও টেস্টিং (App Integration & Testing)

১. সম্পূর্ণ অ্যান্ড্রয়েড প্রজেক্টটি কম্পাইল এবং রান করুন।
২. অ্যাপে প্রবেশ করলে প্রথমে **Notification Permission** চাওয়া হবে, এটি মঞ্জুর (Allow) করুন।
৩. **Sign Up** ট্যাবে গিয়ে আপনার ইমেইল, নাম এবং জন্ম তারিখ দিয়ে অ্যাকাউন্ট তৈরি করুন।
   - সাইনআপ সফল হওয়ার সাথে সাথে আপনার জন্য একটি ইউনিক ইউজারনেম জেনারেট হবে (যেমন: `shuvo_2391`) এবং আপনার ডিভাইসের একটি ইউনিক **FCM Token** ফায়ারস্টোরে সেভ হবে।
৪. আপনার প্রোফাইল সেটিংস চেক করতে হোম স্ক্রিনের ডান কোণায় থাকা **Settings (গিয়ার আইকন)**-এ ক্লিক করুন।
৫. সেখানে আপনার নিজের FCM টোকেন দেখতে পাবেন।
৬. সেখানে **Cloudflare Worker URL** টেক্সট ফিল্ডে আপনার ডেপ্লয় করা ক্লাউডফ্লেয়ার ওয়ার্কারের পাবলিক URL-টি পেস্ট করুন (যেমন: `https://firechat-notif-worker.yourdomain.workers.dev`)। এটি রিয়েল-টাইমে সেভ হয়ে থাকবে।
৭. এখন অন্য ডিভাইসে আরেকটি অ্যাকাউন্ট তৈরি করুন। হোম স্ক্রিনে সার্চ বারে ইউজারনেম সার্চ দিয়ে তার চ্যাটরুমে প্রবেশ করুন।
৮. তাকে মেসেজ পাঠালে মেসেজটি সাথে সাথে ফায়ারবেস রিয়েল-টাইম ডাটাবেজে যুক্ত হবে এবং ব্যাকগ্রাউন্ড নোটিফিকেশন পাঠানোর জন্য ক্লাউডফ্লেয়ার ওয়ার্কারকে কল করবে। 
৯. রিসিভার ডিভাইসটি যদি ব্যাকগ্রাউন্ডে থাকে বা ইন্টারনেট সংযোগ সহ লক থাকে, তবুও তার কাছে সাথে সাথে পুশ নোটিফিকেশন পৌঁছে যাবে এবং সে নোটিফিকেশনে ক্লিক করে সরাসরি আপনার চ্যাটরুমে প্রবেশ করতে পারবে!

---

যেকোনো সমস্যা বা বাড়তি ফিচারের সহায়তায় অ্যান্ড্রয়েড বিল্ড বা ক্লাউডফ্লেয়ার লগ চেক করুন। শুভ চ্যাটিং!
