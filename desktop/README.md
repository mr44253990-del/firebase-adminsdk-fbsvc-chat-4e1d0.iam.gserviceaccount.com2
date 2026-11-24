# Convo Chat — Desktop (Windows & Linux)

Convo Chat Desktop is the native desktop client for the Convo Chat Android app
(v6.1.1). It is written in **Kotlin with Compose Multiplatform Desktop** — the
same language and UI framework family as the Android app — so the Aurora
glass theme, navigation model, and data envelopes are shared concepts.

Supported targets:

| Target | Package format | Runner |
|---|---|---|
| Windows 10/11 x64 (all editions) | `.msi`, `.exe` (per-user, WiX) | `windows-latest` |
| Linux arm64 (Raspberry Pi 4/5, ARM servers, ARM laptops) | `.deb`, `.rpm` | `ubuntu-24.04-arm` |
| Linux x64 | `.deb`, `.rpm` | `ubuntu-latest` |

## What matches the Android app

- **Splash/loading screen**: branded Aurora splash, then automatic session
  restore from the OS config directory (no re-login on every launch).
- **Login screen**: email/password sign-in and account creation through
  Firebase Identity Toolkit REST; refresh-token session persistence.
- **Home feed**: text-only posts from the same Firestore `posts` collection.
- **Chat**: 1:1 text messages through the same RTDB envelope
  `chats/{chatId}/messages/{messageId}`, where `chatId` is the sorted
  `uidA_uidB` pair — identical to `ChatViewModel.sendMessage` on Android.
- **Presence**: `status/{uid} = { state, lastChanged }`, same node Android uses.
- **Appearance**: Light / Dark / AMOLED — same modes as the Android theme.
- **Permission Center**: a desktop-specific permission screen that maps each
  Android runtime permission to its Windows / Linux equivalent, including
  where the user grants it (Windows Privacy settings, XDG portals).

## What is intentionally desktop-native

- No Android permission prompts: the Permission Center explains OS settings.
- Session stored in `%APPDATA%\ConvoChat` (Windows) or
  `~/.config/convochat` (Linux), never in the repo.
- Distribution via `jpackage`-based native installers (MSI/EXE on Windows,
  DEB/RPM on Linux) — no Java pre-installation required; each bundle carries
  its own trimmed runtime (targeting arm64 on ARM runners).

## Build locally

Prerequisites: JDK 17+.

```bash
cd desktop
./gradlew run                      # dev run on this machine
./gradlew packageDistributionForCurrentOS   # native installer for this OS/arch
./gradlew build                    # test + jar
```

CI (`.github/workflows/desktop-build.yml`) builds all three targets on every
push/PR that touches `desktop/` and uploads the installers as artifacts.

## Configuration

Public Firebase web values live in `desktop/src/main/resources/firebase.json`
(the same public values the Android app ships in `google-services.json`).
They can be overridden by environment variables:

- `CONVO_FIREBASE_API_KEY`
- `CONVO_FIREBASE_PROJECT_ID`
- `CONVO_FIREBASE_DATABASE_URL`

Never place service-account credentials, private keys, or worker secrets in
this module; those stay in Cloudflare Worker secrets exactly as documented in
the root `README.md`.

## Roadmap (matching Android feature-for-feature)

1. WebRTC 1:1 audio/video calls (reuse the existing `/turn-credentials`
   worker endpoint; microphone access on desktop needs no runtime prompt).
2. Stories, group chat, activity feed, polls (`groups`, `stories`, `polls`,
   `notifications` collections already exist).
3. Google sign-in via system-browser OAuth loopback flow.
4. Tray icon + notifications for background presence.
5. AI assistant via the existing `/ai/chat` worker route.
