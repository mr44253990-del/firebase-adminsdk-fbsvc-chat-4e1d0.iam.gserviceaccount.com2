# Convo Chat Desktop (Windows ও Linux)

Convo Chat-এর অ্যান্ড্রয়েড অ্যাপের একদম একই অভিজ্ঞতা এখন Windows ও Linux-এ। ডেস্কটপ ক্লায়েন্ট শেয়ার্ড ওয়েব কোর (`web-companion/site/`) ব্যবহার করে, তাই লেআউট, চ্যাট, QR লগইন, পোস্ট ফিড — সব কিছু ফোনের মতোই কাজ করে।

## সাপোর্ট

| OS | Arch | ইনস্টলার |
| --- | --- | --- |
| Windows 10 / 11 | x64 | NSIS সেটআপ (`.exe`) + পোর্টেবল `.zip` |
| Linux (Ubuntu/Debian/Raspberry Pi OS) | arm64 | `.AppImage` + `.deb` |

## যেভাবে কাজ করে

- অ্যাপ চালু হলে শুধু **loopback (`127.0.0.1`)**-এ একটি লোকাল সার্ভার চালু হয় — নেটওয়ার্কে কখনও শোনা হয় না।
- ব্যাকএন্ড একই Cloudflare Worker (`https://convo-chat-companion.mr4425390.workers.dev`)।
- প্রতিটি OS-এর জন্য অ্যান্ড্রয়েড পারমিশন কনভার্ট হয় (`desktop/desktop-map.js`):
  - **ক্যামেরা + মাইক** → Windows privacy settings / macOS media request / Linux PipeWire
  - **নোটিফিকেশন** → Windows Action Center / macOS notification center / Linux desktop daemon
  - **জিওলোকেশন** → ডেস্কটপে GPS provider নেই, তাই denied; ফোন অ্যাপ ব্যবহার করুন
- **Skin size** (View মেনু): Phone 420×760, Tablet 1024×720, Desktop 1280×820 — স্ক্রিনের সাইজ অনুযায়ী সেরকম দেখাবে, শেষ সাইজ মনে রাখে।

## ডেভেলপমেন্ট

```bash
cd desktop
npm install
npm run prepare-site   # web-companion/site -> desktop/site কপি
npm start              # লোকাল রান
```

## বিল্ড

```bash
npm run dist:win           # Windows x64 (.exe + .zip)
npm run dist:linux-arm64   # Linux arm64 (.AppImage + .deb)
```

CI-তে বিল্ড হয় `.github/workflows/desktop-release.yml` দিয়ে (Windows runner ও Linux arm64 নেটিভ runner), প্রতিটি বিল্ডের সাথে SHA-256 চেকসাম artifact আকারে আপলোড হয়।

## সিকিউরিটি নোট

- contextIsolation ON, nodeIntegration OFF, sandbox ON preload।
- লোকাল সার্ভার শুধু loopback-এ bind করে, বাইরের কেউ কানেক্ট করতে পারে না।
- কোনও টোকেন/কী বান্ডেলে থাকে না; সব ক্রেডেনশিয়াল ব্যাকএন্ড Worker-এ।
