# Unified Supabase Storage Worker

This Worker routes app media through one authenticated server-side path. It reads **non-secret** routing settings live from Firestore `app_config/storage`; saving the Project URL/provider in Admin Settings therefore takes effect on the next request and does not require editing or redeploying Worker source.

Android sends a Firebase ID token to this Worker. The Supabase service-role key is used only by the Worker and must never be entered in the app, Firestore, or a checked-in file.

## One-time Cloudflare setup

1. In Supabase, open **Project Settings → API** and copy the service-role/secret key for the project that will hold app media.
2. From the repository root, deploy the dedicated Worker:

   ```sh
   npx wrangler deploy --config wrangler-storage.toml
   ```

3. Add the two secrets to this Worker. Wrangler prompts for each value; do not put either value in shell history, source code, or a committed file:

   ```sh
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT --config wrangler-storage.toml
   npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY --config wrangler-storage.toml
   ```

   `FIREBASE_SERVICE_ACCOUNT` is the Firebase service-account JSON for this app's Firebase project. The service account must have permission to read the Firestore document `app_config/storage` (for example, the **Cloud Datastore User** IAM role). `SUPABASE_SERVICE_ROLE_KEY` is the Supabase `service_role`/secret key from the same Supabase project whose URL will be saved in the app.

4. Deploy the Firestore rules in this branch so the non-secret `app_config/storage` document can be read before sign-up (all other app config remains signed-in only):

   ```sh
   firebase deploy --only firestore:rules
   ```

5. Copy the deployed Worker base URL. A `workers.dev` address is fine, but not required; a custom HTTPS domain and reverse-proxy path such as `https://app.example.com/w/superbase` are accepted.
6. In the app, sign in with the admin account and open **Admin control center → Unified app storage**. Enable **Use Supabase for all uploads**, enter the Supabase API base URL and the Storage Worker base URL (each may include its own proxy path; leave off query strings, fragments, and the endpoint suffixes), leave automatic bucket creation enabled, and tap **Save + test**.

The setup test checks the saved URL and server-side key, creates missing standard buckets when enabled, and performs a small upload/delete smoke test in every bucket. The Admin panel displays the specific failing bucket and the action to take.

## Buckets and object layout

The Worker starts with these standard **public** bucket IDs. Admin Settings lets the admin change each non-secret ID; those IDs are saved in `app_config/storage` and the Worker reads them on the next request. Supabase folders are virtual; the Worker creates object paths during upload, so folders do not need to be pre-created.

| Bucket | App content |
| --- | --- |
| `avatars` | Profile, cover, and group photos |
| `chat_images` | Chat attachments, stories, and payment proofs |
| `voice_notes` | Voice messages |
| `posts` | Post images and videos |
| `reels` | Reel videos |
| `thumbnails` | Post/reel thumbnails |
| `updates` | Admin-published APK updates |

These buckets must be public because app clients display the returned public media URLs without a privileged key. Do not store private documents in them. If an existing bucket is private, the test reports the bucket name and asks the admin to make it public in **Supabase Dashboard → Storage → bucket settings**. The Worker does not silently change an existing bucket's visibility.

## What is protected / supported

- Each upload and delete requires a verified Firebase ID token.
- The service-role key never leaves Cloudflare. It is not returned by `/health`, diagnostics, or upload responses.
- Standard user uploads are stored under that user's UID. The Worker rejects attempts to delete another user's object.
- Only the configured admin email can upload or delete files in `updates` (APK releases).
- Posts/reels/thumbnails retain the app's existing 10-day expiry metadata; APKs do not expire.
- Worker uploads are limited to 95 MiB to match the existing media route's cap.
- Admin `Save + test` checks bucket presence/public visibility and exercises actual write/delete permissions, not just URL reachability.

## Diagnosing common errors

- **Worker unreachable / 404:** confirm the saved Worker URL is the deployed `convo-chat-storage` URL, including `https://` and without an endpoint path.
- **Missing `SUPABASE_SERVICE_ROLE_KEY`:** run the matching `wrangler secret put` command above, then retry the Admin test.
- **Supabase rejected key (401/403):** set the correct `service_role`/secret key from the same project URL; do not use an anon/publishable key.
- **Bucket missing:** keep automatic creation enabled and tap **Save + test**, or manually create the exact bucket ID shown in the diagnostic.
- **Bucket private:** switch that standard media bucket to public in the Supabase Dashboard, then test again.
- **Write/delete smoke check failed:** verify the Supabase project is active, Storage is enabled, and the Cloudflare secret has Storage administrative privileges; Cloudflare Worker logs include the upstream status.
- **Firestore config read failed:** verify `FIREBASE_SERVICE_ACCOUNT` belongs to the app's Firebase project and can read Firestore. The app's current `firestore.rules` already permits signed-in users to read `app_config` and restricts writes to the admin.

To return to the existing mixed R2/direct-Supabase routing, switch off **Use Supabase for all uploads** and save. The Worker can remain deployed; no secret is copied back to Android.
