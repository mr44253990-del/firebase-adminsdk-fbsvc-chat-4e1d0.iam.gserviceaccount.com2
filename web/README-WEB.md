# Convo Nexus

A liquid-glass social messenger — stories, a live feed, private groups, dynamic themes and
progressive offline loading. Built by **Rakibul Islam** (Kapilmuni College).

This repository holds two things:

1. **`/` — the web reference implementation** (Next.js App Router + PostgreSQL + Drizzle).
   Every v5 product surface is implemented and interactive here.
2. **The Android upgrade** lives on the branch `feature/convo-nexus-v5` of the app repo and
   adds the shared foundations: a Supabase Storage file store, the GlassKit motion engine and
   the new About screen. See `docs/UPGRADE-CONVO-NEXUS.md` there.

---

## Product surfaces

| Area | What it does |
| --- | --- |
| **Auth** | Animated aurora hero, sign in / create account, **profile photo upload at signup**, accent + starting theme picker |
| **Feed** | Facebook-style posts: 5 reaction types with a hover picker and burst animation, comments, **view counter**, relative timestamps, author profile links, **video autoplay when 55% visible** |
| **Posts** | Public / private visibility at publish time, **editable and switchable to private after publishing**, delete removes the row, reactions, comments, views and the binary from the file store |
| **Stories** | 12-hour auto-expiry (server-side sweep on every read), progress bars, hold-to-pause, tap-to-navigate, view counts, inline reactions, owner delete |
| **Notes** | 24-hour text notes shown next to your name |
| **Messages** | Moved to their own hub. Unread rows glow red with a live count, **swipe (or double-click) to reply**, image / video / **voice notes**, edit, delete, forward, per-message reactions |
| **Groups** | Create with a **background cover image** and member picker. Group messages deliberately raise **no notifications**; 1:1 threads do |
| **People** | Every member listed with live presence (online / last-seen), search, filters, follow, message, block |
| **Activity** | Notification centre with unread red pulse, mark-read and clear |
| **Profile** | Cover, avatar, stats (posts / followers / reactions / views), copyable profile link, all authored posts |
| **Settings** | Profile editor, **10 dynamic themes** that recolour every screen instantly, accent picker, reduce-motion, activity-status toggle, read receipts, blocked list with unblock, About |

## Design system

* `--p / --s / --t` accent triple + `--bg / --glass / --fg` per theme, swapped live on the root node.
* `.glass` — translucent panel with a masked specular gradient border and coloured ambient shadow.
* `.aurora` — three drifting blobs plus a rising bubble field, all GPU transforms only.
* `.skeleton` — shimmer used for progressive loading.
* `.unread-glow` — the gentle red pulse on unread rows and badges.
* Motion is CSS-driven (`popIn`, `slideUp`, `burst`, `rise`, `drift*`) so nothing blocks the main thread.
* Reduce-motion is honoured both from the OS and from the in-app toggle.

## Performance notes

* Progressive hydration: stories load first, then the feed, then the people directory.
* Feed responses are cached in `localStorage`; if the network drops the cached copy renders with
  an "offline mode" banner and is replaced automatically when connectivity returns.
* Views and video playback are driven by a single `IntersectionObserver` per card.
* Presence and badge polling run on 15s / 12s / 8s intervals, never on every render.

## Data model

Postgres via Drizzle (`src/db/schema.ts`): `users`, `sessions`, `posts`, `reactions`,
`comments`, `post_views`, `stories`, `story_views`, `conversations`, `members`, `messages`,
`notifications`, `blocks`, `follows`, `notes`, `media`, `seed_state`.

`media` mirrors the Supabase Storage bucket layout used by the Android client
(`avatars`, `posts`, `stories`, `voice`, `groups`) and is served from `/api/media/[id]` with
immutable cache headers. Deleting a post, story, message or avatar deletes the binary too.

## API

| Route | Purpose |
| --- | --- |
| `GET /api/health` | Liveness + database probe |
| `GET /api/session` | Current user, presence heartbeat, unread badges |
| `POST /api/auth` | `login` / `signup` / `logout` |
| `GET /api/feed` | `?part=stories` for the fast first paint, full payload otherwise |
| `POST /api/posts` | `create` / `react` / `comment` / `view` / `update` / `delete` |
| `POST /api/stories` | `create` / `view` / `react` / `delete` |
| `GET POST /api/people` | Directory, single profile, `follow` / `unfollow` / `block` / `unblock` |
| `GET POST /api/chats` | Threads, messages, `open-dm`, `create-group`, `send`, `read`, `edit-message`, `delete-message`, `forward-message`, `react-message`, `update-group`, `leave` |
| `GET POST /api/notifications` | List, mark-read, clear |
| `POST /api/me` | Profile, appearance, privacy patches and notes |
| `POST /api/upload` | Media ingest (6 MB cap, base64 → `media`) |

## Demo accounts

The world seeds itself on first request. Password for every seeded account is `nexus1234`:

`rakibul`, `nadia.h`, `arif.dev`, `samira`, `tanvir`, `farhana`, `imran`, `priya`

## Local development

```bash
npm install
npx drizzle-kit push --config drizzle.config.json
npm run dev
```
