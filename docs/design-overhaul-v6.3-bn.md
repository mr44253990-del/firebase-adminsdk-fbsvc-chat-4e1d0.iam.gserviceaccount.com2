# v6.3 "Prism" — Design Overhaul Plan (deep research + per-page changes)

Date: 2026-11-24 · Mockups: `docs/design-v6.3/v6.3-mockup-{home,chat,call}.png` (generated in-sandbox and reviewed)

## 1. Current design — what it looks like (research result)

I inspected the real assets and the design code instead of guessing:

- **App icon** (`convo_icon_safe.png`): rounded square, dark blue→black gradient, a gradient "C" (blue→pink→purple) holding a white speech bubble with 3 dots, "Convo" in white + "CHAT" in multicolor. Language: glassmorphism + neon gradient.
- **Chat backgrounds**: illustrated wallpapers (starry lake night scene, pastel milk waves…) — anime/illustration style, not flat.
- **Theme system** (`ui/theme/`): "Aurora" palette (violet `#7C5CFC`, blue `#3D8BFF`, cyan `#35D6C7`, pink `#FF6FAE`) on ink `#0C0E15`; 30+ named themes; `glassmorphic()` = 28 dp translucent card + 1 dp light border + shadow.
- **"New design" kit** (`ui/LiquidGlass.kt`, v5.6+): deep black `LiquidInk #04030B`, animated violet/blue glass ribbons with bright rims, floating glass beads, glossy logo tile. Used by the splash and (when "new design" is on) behind **every page** via `PremiumBackground(liquid = true)`.
- **Chrome** (`ui/ModernChrome.kt`): `modernBackdrop()` = theme-tinted gradient + 2 glow blobs; transparent top app bars with frosted scrolled state.
- **Web companion**: CSS variables `--accent-a/b/c` (#8b7bff/#5ac8fa/#ff6fb5), gradient `.btn.primary`, glass ghost buttons, 14–28 px radii.

**Verdict:** the base is strong (dark glass + aurora), but pages still feel like "theme + generic M3 surfaces". What's missing for a 2026 flagship feel: a visible *mesh/grid* signature, gradient primary actions everywhere, gradient outgoing bubbles, pulsing call rings, and consistent frosted bars.

## 2. Mockup review (what I generated and then reviewed)

- `v6.3-mockup-home.png` — frosted top bar with gradient "Convo" wordmark + search pill, avatar story row, **glass** chat rows (translucent over ink, not white), gradient unread badges, floating glass capsule nav with active pill. Review note: rows must stay dark-glass (not white) and nav icons must be real icons.
- `v6.3-mockup-chat.png` — glass header (avatar + online dot + call icons), incoming frosted bubble, **outgoing gradient bubble** (violet→blue→cyan) with white ticks, glass composer + gradient send FAB. Review note: timestamp on gradient bubble needs white-70% for contrast; mic icon instead of dots.
- `v6.3-mockup-call.png` — aurora mesh bg, avatar with pulsing rings, INCOMING VIDEO CALL, 3 round actions (Decline red / Message glass / Answer green-cyan gradient). Review note: drop the grid on call screens; subtitle "Convo Chat • video" is redundant.

## 3. v6.3 "Prism" design language (the new style)

1. **Signature backdrop** — aurora mesh (3 drifting radial orbs) + a faint perspective grid on deep ink; used app-wide through `LiquidGlassBackground`/`PremiumBackground`/`modernBackdrop`.
2. **Surfaces** — `prismGlass`: 24–32 dp radius, white-6% fill, 1 dp gradient rim (white→tint→blue), soft shadow.
3. **Primary actions** — always a violet→blue→cyan gradient (`PrismGradientButton`, `PrismFab`); danger = red gradient.
4. **Bubbles** — incoming = frosted glass; outgoing = gradient with white text and white ticks.
5. **Top bars** — frosted glass strip with gradient wordmark/accents; scrolled state slightly more opaque.
6. **Badges** — gradient pill for unread counts; green dot for online.
7. **Motion** — slow drift on background (draw-only), pulse rings on incoming call, button press scale .97.

## 4. Per-page change list

| Page | v6.3 change |
|---|---|
| Splash / launch | Prism mesh + grid behind `LiquidLogoTile`; gradient wordmark |
| Sign-in (`AuthScreen`, `LoginNova`) | Prism backdrop, glass card, `PrismGradientButton` for primary CTA, gradient "Convo" title |
| Home / chat list | Frosted `PrismTopBar` (gradient wordmark + search pill), glass chat rows, gradient unread badges, capsule nav with active gradient pill |
| Chat screen | Glass header, incoming frosted / outgoing gradient bubbles, glass composer with `PrismFab` send, typing dots in glass pill |
| Group chat | Same chat language + pinned announcement bar as gradient-tinted glass strip |
| Incoming call | Aurora mesh (no grid), pulsing avatar rings, 3 round gradient/glass actions |
| Call screen | Glass control bar, gradient mute/end buttons, timer chip |
| Profile / settings / admin | `PrismTopBar`, glass section cards, gradient primary rows |
| Posts / stories / notes | Glass cards over mesh, gradient action chips |
| Web companion | Same tokens in CSS: gradient primary buttons, glass top bar, gradient send button, prism avatar ring |

## 5. Updates researched and shipped in this round

- New app themes **"Prism Night"** (dark) and **"Prism Dawn"** (light) added to `Theme.kt` + theme picker.
- New centralized kit `ui/PrismKit.kt` (`PrismMeshBackground`, `prismGlass`, `PrismGradientButton`, `PrismFab`, `PrismTopBar`, `PrismBadge`).
- Global backdrops upgraded (every page): grid + third orb in `LiquidGlassBackground`, `modernBackdrop`, `PremiumBackground`.
- `glassmorphic` surfaces get a slightly stronger rim (all pages at once).
- Web: prism accents in `styles.css`.
- Version bump 6.1.1 → **6.3.0** (code 76).

## 6. Next rounds (needs real-device pass + bigger per-screen rewiring)

1. Rewire each screen's top bar to `PrismTopBar` and primary buttons to `PrismGradientButton` (screen-by-screen, verified on device screenshots like the v5.6 round).
2. Outgoing gradient bubbles + frosted incoming in `ChatScreen`/`GroupChatScreen` bubble components.
3. Pulse-ring incoming-call animation + gradient answer button in `IncomingCallUi`.
4. Story ring = conic gradient; post cards glass over mesh.
5. Web: full prism pass on header/composer/bubbles.
6. Motion specs: 260 ms cubic-bezier(.22,.68,.32,1) everywhere; haptics on send/answer.
7. Accessibility: contrast check for gradient text on glass, min 48 dp touch targets, TalkBack labels on new components.
