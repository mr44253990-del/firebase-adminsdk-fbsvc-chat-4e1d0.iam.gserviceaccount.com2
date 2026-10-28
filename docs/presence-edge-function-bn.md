# Convo Chat — Presence probe এখন Supabase Edge Function-এ

প্রতি ৫ মিনিটে "কে online / reachable" যাচাই করার background probe এখন Cloudflare Worker-এর বদলে
**Supabase Edge Function `presence-monitor`** থেকে চলে। Schedule দেয় **Supabase Cron (`pg_cron` + `pg_net`)**।
Firebase Functions লাগে না, Blaze plan-ও লাগে না।

```
Supabase Cron (*/5 * * * *)
   └─ POST https://<ref>.supabase.co/functions/v1/presence-monitor/cron   (header: x-cron-secret)
         └─ Edge Function → Google OAuth (service-account secret)
               ├─ Firestore: users / presence পড়ে
               ├─ FCM: প্রতি device-এ একটি silent data-only probe (priority NORMAL, TTL 60s)
               └─ Firestore: presence_monitor/state লেখে (lock, শেষ রান, ফলাফল)
Android app → probe পেয়ে acknowledgement লেখে → তখনই "Reachable"
```

> FCM "accepted" মানে ফোনে পৌঁছেছে, এমন নয়। App-এর acknowledgement এলে তবেই device reachable ধরা হয়।
> Doze, battery saver, OEM restriction থাকলে probe দেরিতে আসতে পারে বা না-ও আসতে পারে। এটা best-effort।

## ফাইলগুলো

| ফাইল | কাজ |
|---|---|
| `supabase/functions/presence-monitor/index.ts` | Entry point (`Deno.serve`) |
| `supabase/functions/presence-monitor/handler.ts` | Routes: `/health`, `/cron`, `/presence/heartbeat`, `/admin/users`, `/admin/probe` |
| `supabase/functions/presence-monitor/presence.js` | Google OAuth (JWT), Firestore REST, FCM v1, probe logic, overlap lock |
| `supabase/functions/presence-monitor/presence_test.ts` | `deno test`: route, secret, auth, mocked full cron run |
| `supabase/config.toml` | `verify_jwt = false` (নিজস্ব auth আছে) |
| `supabase/functions/.env.example` | Local secrets-এর নমুনা (`.env` gitignored) |
| `supabase/sql/presence_cron.sql` | Vault secrets + ৫ মিনিটের cron job |
| `deploy-presence-edge-function.sh` | এক কমান্ডে link + secrets + deploy |
| `wrangler-presence.toml` | Legacy। এখন `crons = []`, তাই Cloudflare আর schedule চালায় না |

## Route আর নিরাপত্তা

| Route | কে ডাকতে পারে | যাচাই |
|---|---|---|
| `GET /health` | যে কেউ | কোনো secret দেখায় না, শুধু configured কি না |
| `POST /cron` | শুধু Supabase Cron | `x-cron-secret` header, constant-time compare, কমপক্ষে ২৪ অক্ষর |
| `POST /presence/heartbeat` | লগইন করা user | Firebase ID token (Bearer) |
| `GET /admin/users` | শুধু admin | Firebase ID token + verified admin email |
| `POST /admin/probe` | শুধু admin | একই। Lock উপেক্ষা করে একবার probe পাঠায় |

- Platform JWT check বন্ধ (`--no-verify-jwt`), কারণ app Supabase-এর নয়, **Firebase** token পাঠায়। প্রতিটি route নিজের credential নিজে যাচাই করে।
- একসাথে দুটো রান যাতে না চলে, তাই `presence_monitor/state.probeRunningUntil` lock আছে (৪ মিনিট)।
- `/cron` সাথে সাথে `202` ফেরত দেয়, আর probe চলে `EdgeRuntime.waitUntil` দিয়ে background-এ। তাই `pg_net`-এর timeout-এ cron fail হয় না।

---

## ধাপে ধাপে সেটআপ

### ধাপ ০ — যা লাগবে
1. একটি Supabase project। App-এর storage-এর জন্য যেটা আছে, সেটাই চলবে।
2. Firebase Console → Project settings → **Service accounts** → *Generate new private key* → JSON ফাইল।
   এই ফাইল কখনো Git, Android বা চ্যাটে দেবেন না।
3. [Supabase CLI](https://supabase.com/docs/guides/cli) ইনস্টল করুন:
   ```bash
   npm i -g supabase        # অথবা: brew install supabase/tap/supabase
   supabase --version
   ```
4. Docker লাগবে শুধু local-এ `supabase functions serve` চালাতে চাইলে। শুধু deploy করতে Docker লাগে না।

### ধাপ ১ — লগইন ও project link
```bash
cd firebase-adminsdk-fbsvc-chat-4e1d0.iam.gserviceaccount.com2
supabase login
supabase link --project-ref <your-project-ref>
```
`<your-project-ref>` হলো `https://abcdxyz.supabase.co` URL-এর `abcdxyz` অংশ।

### ধাপ ২ — Secrets সেট করা
```bash
# ২৪+ অক্ষরের random cron secret
CRON_SECRET=$(openssl rand -hex 32); echo "$CRON_SECRET"   # এটা লিখে রাখুন, ধাপ ৪-এ লাগবে

supabase secrets set \
  "FIREBASE_SERVICE_ACCOUNT=$(tr -d '\n' < /path/to/service-account.json)" \
  "PRESENCE_CRON_SECRET=$CRON_SECRET"
# ঐচ্ছিক: supabase secrets set ADMIN_EMAIL=you@example.com
supabase secrets list
```
Dashboard থেকেও দেওয়া যায়: **Edge Functions → Secrets**। Secret বদলালে redeploy লাগে না।
নাম `SUPABASE_` দিয়ে শুরু করা যাবে না।

### ধাপ ৩ — Deploy
```bash
supabase functions deploy presence-monitor --no-verify-jwt
curl https://<ref>.supabase.co/functions/v1/presence-monitor/health
```
সঠিক উত্তর হবে এমন:
`{"ok":true,"platform":"supabase-edge-function","cronSecretConfigured":true,...}`

> শর্টকাট: `./deploy-presence-edge-function.sh <ref> /path/to/service-account.json` একসাথে ধাপ ১-৩ করে,
> আর শেষে cron secret দেখিয়ে দেয়।

### ধাপ ৪ — ৫ মিনিটের Supabase Cron
1. Dashboard → **SQL Editor** খুলুন।
2. `supabase/sql/presence_cron.sql` পুরোটা paste করুন।
3. দুটো placeholder বদলান:
   - `https://YOUR-PROJECT-REF.supabase.co`
   - `PASTE-THE-SAME-PRESENCE_CRON_SECRET` (ধাপ ২-এর একই secret)
4. **Run** চাপুন।

এটা `pg_cron` ও `pg_net` চালু করে, URL আর secret **Vault**-এ encrypted রাখে, আর `convo-presence-probe` job বানায়।
আবার চালালে নতুন job হয় না, পুরোনোটাই update হয়।

যাচাই করতে:
```sql
select jobname, schedule, active from cron.job where jobname = 'convo-presence-probe';
select status, return_message, start_time from cron.job_run_details order by start_time desc limit 5;
select status_code, content from net._http_response order by created desc limit 5;  -- 202 হওয়া উচিত
```
Dashboard → **Integrations → Cron**-এও job আর ইতিহাস দেখা যায়।

### ধাপ ৫ — পুরোনো Cloudflare Cron বন্ধ (শুধু একটি scheduler)
- আগে Cloudflare Worker deploy করে থাকলে `npx wrangler deploy -c wrangler-presence.toml` চালান।
  এখন `crons = []`, তাই Cloudflare trigger মুছে যাবে।
- অথবা Cloudflare Dashboard → Workers → firechat-presence-worker → **Triggers → Cron** মুছে দিন।
- চাইলে Worker-টাই delete করতে পারেন। App এখন Supabase URL ব্যবহার করবে।

### ধাপ ৬ — App-এ URL বসানো
App → **Admin Control Center → Presence / 5-min probe**:
1. **"Use my Supabase project URL"** chip চাপুন। Storage-এর Supabase URL থাকলে এটা নিজে থেকেই
   `https://<ref>.supabase.co/functions/v1/presence-monitor` বসিয়ে দেয়। নাহলে হাতে লিখুন।
2. **Save URL → Test** চাপুন। সবুজ "Supabase Edge Function is ready" দেখাবে।
3. **Send one-time silent probe** দিয়ে সাথে সাথে পরীক্ষা করুন। এক মিনিটের মধ্যে user list-এ *Reachable* বাড়বে।

### Local-এ চালানো (ঐচ্ছিক)
```bash
cp supabase/functions/.env.example supabase/functions/.env   # আসল মান দিন
supabase start
supabase functions serve presence-monitor --no-verify-jwt --env-file supabase/functions/.env
curl http://127.0.0.1:54321/functions/v1/presence-monitor/health
curl -X POST -H "x-cron-secret: <secret>" http://127.0.0.1:54321/functions/v1/presence-monitor/cron
```
টেস্ট চালাতে: `deno test --allow-env supabase/functions/presence-monitor/`। GitHub Actions-এও প্রতি push-এ চলে।

### সমস্যা হলে
| লক্ষণ | সমাধান |
|---|---|
| `/health` 503, `FIREBASE_SERVICE_ACCOUNT` missing | ধাপ ২ আবার করুন, JSON এক লাইনে দিন |
| `cronSecretConfigured:false` | `PRESENCE_CRON_SECRET` সেট করুন (২৪+ অক্ষর) |
| `net._http_response`-এ 401 | Vault-এর `presence_cron_secret` আর Edge secret মিলছে না |
| app থেকে 401 (Supabase gateway "Invalid JWT") | `--no-verify-jwt` ছাড়া deploy হয়েছে, আবার deploy করুন |
| `"skipped":true, "previous-run-still-active"` | স্বাভাবিক। আগের রান শেষ হয়নি, lock কাজ করছে |
| Reachable কম | ফোনে battery optimization বন্ধ করুন, notification permission দিন। FCM best-effort |

Logs দেখতে: Dashboard → Edge Functions → presence-monitor → **Logs**। Service-account বা token কখনো log হয় না।

---

## Edge Functions দিয়ে আপনার website/app-এ আর কী কী করা যায়

Edge Function হলো আপনার নিজের ছোট server API: Deno/TypeScript-এ লেখা, বিশ্বজুড়ে কাছের region থেকে চলে,
আর secret নিরাপদে রাখে।

1. **Webhook গ্রহণ**: Stripe, bKash/SSLCommerz, GitHub, Paddle। Payment সফল হলে premium চালু করা যায়।
   Signature যাচাই server-এ হয়, app-এ কোনো secret থাকে না।
2. **Push notification / FCM sender**: নতুন post, mention বা admin broadcast-এ server থেকে FCM পাঠানো
   (এই presence function যেভাবে পাঠায়)।
3. **Email / OTP / digest**: Resend, Postmark বা SMTP দিয়ে welcome mail, সাপ্তাহিক "আপনি যা মিস করেছেন" digest,
   password reset। Cron দিয়ে schedule করা যায়।
4. **AI ফিচার**: OpenAI, Gemini বা Claude-এর key server-এ রেখে chat summary, smart reply, translation,
   ছবির caption, spam বা abuse ধরা। App-এ API key রাখতে হয় না।
5. **Link preview proxy**: URL-এর title, ছবি আর description server-এ আনা। User-এর IP লুকায়, cache করা যায়।
6. **OG image / share card**: profile বা post share করলে সুন্দর preview ছবি বানানো (`og_edge`)।
7. **Media pipeline**: Supabase Storage-এ upload হলে thumbnail বানানো, virus বা NSFW scan,
   মেয়াদোত্তীর্ণ ফাইল cron দিয়ে মুছে ফেলা।
8. **Rate-limited public API**: website-এর contact form, waitlist, invite link, QR profile page।
9. **Bot**: Telegram, Discord বা Slack bot দিয়ে admin alert ("নতুন report এসেছে")।
10. **Scheduled কাজ**: inactive account cleanup, story বা status ২৪ ঘণ্টা পর মুছে ফেলা, দৈনিক analytics snapshot।
11. **Secure admin action**: user ban, data export। Firebase ID token যাচাই করে server থেকে Firestore-এ লেখা।
12. **Website SSR / redirect**: short link (`convo.link/abc`), deep link fallback, app download page।

প্রতিটি নতুন function বানাতে `supabase functions new <name>` চালান, তারপর
`supabase functions deploy <name>`। Secret লাগলে `supabase secrets set`।
