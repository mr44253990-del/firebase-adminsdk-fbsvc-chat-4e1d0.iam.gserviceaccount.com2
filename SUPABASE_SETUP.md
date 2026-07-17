# Supabase Storage Setup (Required once)

FireChat stores **all media files** (avatars, post photos/videos, stories, voice
messages, group photos) in **Supabase Storage**, while structured data lives in
Firebase (Auth + Firestore + Realtime Database).

Project credentials (already configured inside `SupabaseManager.kt`):

```
supabaseUrl = "https://srfztgcdejfaesrvkarg.supabase.co"
supabaseKey = "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA"
```

The Android client uses the official package:

```kotlin
implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")
```

## 1. Create the buckets

Open **Supabase Dashboard → Storage → New bucket** and create these **PUBLIC** buckets:

| Bucket    | Used for                                   |
|-----------|--------------------------------------------|
| `avatars` | Profile photos (signup + edit profile)     |
| `posts`   | Post images/videos + chat image messages   |
| `stories` | Story photos/videos (auto-deleted in 12h)  |
| `voice`   | Voice messages (.m4a)                      |
| `groups`  | Group photos & chat backgrounds            |

> If a bucket is missing, uploads fail gracefully (the app never crashes) —
> the post/message is simply sent without media.

## 2. Storage policies (RLS)

For each bucket, add a policy that allows public read + insert/update/delete
with the publishable key, e.g. in **SQL editor**:

```sql
create policy "public read"   on storage.objects for select using (bucket_id in ('avatars','posts','stories','voice','groups'));
create policy "public write"  on storage.objects for insert with check (bucket_id in ('avatars','posts','stories','voice','groups'));
create policy "public update" on storage.objects for update using (bucket_id in ('avatars','posts','stories','voice','groups'));
create policy "public delete" on storage.objects for delete using (bucket_id in ('avatars','posts','stories','voice','groups'));
```

## 3. Firebase rules (recommended)

Firestore (allows the new collections: posts, stories, groups, notifications, notes):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

Realtime Database (messages, presence, typing, unread counters):

```
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

## 4. How deletion works

Every post / story / message stores its public URL. When the user deletes
content, the app:

1. deletes the Firestore document / RTDB node,
2. parses `bucket/path` from the URL and deletes the file from Supabase Storage.

Stories also self-destruct: expired stories (`expiresAt < now`, created +12h)
are purged from Firestore **and** Storage the next time anyone loads the feed.
