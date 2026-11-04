# Auto AI Post + Poll: সেটআপ (বাংলা)

## ১) Firestore rules deploy করুন (Poll-এর জন্য দরকার)
`firestore.rules`-এ নতুন `polls` rules যোগ হয়েছে। এটা deploy না করলে vote/poll তৈরি **fail** করবে।
- Firebase Console → Firestore → Rules → `firestore.rules`-এর পুরো লেখা paste করে **Publish** করুন,
- অথবা `firebase deploy --only firestore:rules`।

## ২) Auto AI post function deploy করুন (Supabase)
আগে presence function deploy করা থাকতে হবে (`FIREBASE_SERVICE_ACCOUNT` আর `PRESENCE_CRON_SECRET` secret তখনই সেট হয়ে যায়)।

```bash
./deploy-auto-ai-post.sh <project-ref>
```
- স্ক্রিপ্টটা আপনার **Mistral API key** চাইবে। টাইপ করার সময় key স্ক্রিনে দেখা যাবে না।
- key-টা শুধু Supabase secret হিসেবে থাকে। অ্যাপ বা Git-এ কখনো যায় না।
- Health check: `https://<ref>.supabase.co/functions/v1/auto-ai-post/health`।

## ৩) Cron চালু করুন
Supabase Dashboard → SQL Editor-এ `supabase/sql/auto_post_cron.sql` চালান।
- এটা প্রতি ১০ মিনিটে function-টা ডাকে।
- presence cron-এর Vault secret দুটোই আবার ব্যবহার করে, তাই আগে `presence_cron.sql` চালানো থাকতে হবে।

## ৪) অ্যাপে চালু করুন
Admin → **Auto AI posts**:
- চালু/বন্ধ করা যায়।
- দিনে কতটা পোস্ট হবে সেটা ঠিক করা যায়।
- পোস্টের ধরন বেছে নেওয়া যায়: Mixed / Tips / Quotes / Questions / Fun facts / Motivation / Polls।
- ভাষা আর prompt দেওয়া যায়।
- emoji থাকবে কিনা আর কোন সময়ের মধ্যে পোস্ট হবে, সেটাও সেট করা যায়।
- সব ঠিক করে **Save** চাপুন।

পোস্ট আপনার admin account থেকে যায়। ফোন বন্ধ থাকলেও server নিজেই পোস্ট করে।
- **Post one now** চাপলে পরের cron tick-এ, মানে ১০ মিনিটের মধ্যে, একটা পোস্ট হয়।
- ধরন "Polls" বাছলে AI নিজেই ২৪ ঘণ্টার একটা poll বানায়।
- Admin পেজের "Server status" অংশে দেখা যায় শেষ পোস্ট কখন হয়েছে, পরের পোস্ট কখন হবে আর কোনো error আছে কিনা।

## Poll
Admin → **Polls & votes**:
- প্রশ্ন লিখুন, ২–৬টা option দিন আর সময়সীমা ঠিক করুন।
- ইউজাররা Home feed-এর একদম উপরে poll দেখে vote দেয়।
- সময় শেষ হলে প্রত্যেক ফোন থেকে poll নিজে থেকেই সরে যায়। server cron-ও poll-টাকে inactive করে দেয়।
- Admin পেজে ফলাফল দেখা যায়, আর **কে কোন option-এ vote দিয়েছে** সেটাও দেখা যায়।
