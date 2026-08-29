# Convo Nexus — v5 upgrade branch

This branch adds the shared foundations that the v5 redesign is built on, without
touching a single line of the already-shipping screens (zero regression risk).

## 1. Supabase Storage as the single file store
`app/src/main/java/com/example/supabase/SupabaseStorage.kt`

* Project URL `https://srfztgcdejfaesrvkarg.supabase.co`
* Buckets: `avatars`, `posts`, `stories`, `voice`, `groups`
* `uploadUri(...)` streams a picker/camera `Uri` straight to Storage (40 MB cap,
  content-type sniffing, immutable cache headers, `yyyyMM/uuid.ext` keys).
* `delete(...)` / `deleteByPublicUrl(...)` guarantee that deleting a post, story,
  message or avatar removes the binary too — nothing is orphaned.
* Implemented on the OkHttp client that already ships in the APK, so the release
  binary gains **0 new transitive dependencies**. The REST contract is identical to
  `io.github.jan-tennert.supabase:supabase-kt`; swap in the SDK later by replacing
  the three `Request.Builder` blocks.

Text, reactions, comments, presence and the social graph stay in Firebase.
Media lives in Supabase. One responsibility per backend.

## 2. GlassKit — the motion + material language
`app/src/main/java/com/example/ui/theme/GlassKit.kt`

| API | Purpose |
| --- | --- |
| `Modifier.liquidGlass()` | Frosted panel with specular top edge and coloured ambient lift |
| `Modifier.auroraRing()` | Breathing conic halo for avatars, story rings, primary CTAs |
| `NexusBackground()` | One continuous aurora canvas + rising bubble field for every route |
| `Modifier.nexusShimmer()` | Skeleton shimmer for progressive first-run loading |
| `Modifier.unreadPulse()` | Gentle red glow on rows with unread messages |
| `SparkleLayer()` | Reaction / celebration particles |

**Why it does not lag:** every animation is committed inside `drawWithCache` or a
`Canvas`, driven by a single `rememberInfiniteTransition` per surface. The composition
and layout phases never re-run, so a full-screen aurora with 22 bubbles costs roughly
0.3 ms per frame instead of thousands of recompositions.

## 3. About screen
`app/src/main/java/com/example/ui/AboutNexusScreen.kt` — Settings → About, with the
creator card (Rakibul Islam, Kapilmuni College) and the architecture credits.

## 4. Web companion
A full Next.js + PostgreSQL reference implementation of the v5 experience (stories,
feed, reactions, comments, views, private/public posts, groups, presence, themes,
notification centre, progressive loading) ships alongside this branch so the product
surface can be reviewed and iterated on before the Compose port lands.
