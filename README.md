# ⚡ FireChat 2.0

A next-generation **social chat app** for Android — Messenger + Instagram +
Facebook in one glassmorphism package. Built with Kotlin, Jetpack Compose,
Firebase and Supabase Storage.

## ✨ Features

### 🔐 Auth
- Stunning animated glass login / signup with floating bubbles
- Profile **photo upload during signup** → saved to Supabase Storage
- Forgot-password via email

### 🏠 Home (Facebook / Instagram style feed)
- **Stories rail** on top with glowing gradient rings
- Stories **auto-delete after 12 hours** (Firestore + Storage purged)
- Story viewer with progress bars + quick **emoji reactions**
- Posts with **likes, comments, view counts, time-ago**
- **Auto-playing muted videos** while scrolling (Media3)
- **Public / private** posts — switchable anytime, edit & delete everywhere
- Tap any author → their profile
- Progressive loading with shimmer placeholders

### 💬 Messages (own tab)
- Conversation list with **pulsing red unread badges** + counters
- **Active now** rail with green presence dots
- Live search across people
- **Swipe right to reply**, long-press for actions
- **Voice messages** (record / play with waveform), **photo messages**
- **Edit, delete, forward** messages, emoji **reactions**
- Typing indicator, online / last-seen labels
- **Block / unblock** users

### 👥 Groups
- Create groups with photo + member picker (from the Messages tab)
- Add members later, custom **chat background image**
- Group messages stay silent — **no push notifications** for groups

### 🔔 Notifications
- In-app hub: messages, likes, comments, story reactions
- Unread counters on the bell + red glow on the Chats tab
- Push notifications via n8n webhook + FCM (direct messages only)

### 👤 Profile
- Cover, avatar, bio, stats (posts / likes / comments)
- **Edit profile** (name, bio, avatar → Supabase)
- Copyable profile link, your full post history

### 📝 Notes
- Colorful personal notes, pinnable, synced to Firestore

### 🎨 Settings
- **6 dynamic themes** (Aurora, Ocean, Sunset, Emerald, Rose, Midnight) — applied live, synced to your account
- **Activity status** toggle (online / last-seen privacy)
- Blocked users manager, push webhook config
- About: **Rakibul Islam — Kapilmuni College**

### 🚀 Performance & offline
- Firestore + Realtime Database **disk persistence** → works offline like Facebook
- Coil memory + 512 MB disk image cache
- Hardware-accelerated, allocation-free animations (no lag)

## 🗄️ Architecture

| Layer      | Technology                                             |
|------------|--------------------------------------------------------|
| Auth       | Firebase Authentication (email/password)               |
| Data       | Cloud Firestore (users, posts, stories, groups, notes, notifications) — offline cached |
| Realtime   | Firebase RTDB (messages, presence, typing, unread) — disk persisted |
| Media      | **Supabase Storage** via `supabase-kt` (+ REST fallback) |
| Push       | FCM + n8n webhook (configurable in Settings)           |
| UI         | Jetpack Compose, Material 3, custom glass design system |

See **[SUPABASE_SETUP.md](SUPABASE_SETUP.md)** for the one-time storage bucket setup.

## 🧑‍💻 Developer

**Rakibul Islam** — student at **Kapilmuni College**.
Crafted with ❤️, Kotlin and a lot of glass.
