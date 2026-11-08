# Update List v4.8.0 — Android Profile Menu UI Bug Fix (Web থেকে আলাদা)

**তারিখ:** 2026-11-08 Dhaka | **Scope:** শুধু Android Application | **Web Version:** স্পর্শ করা হবে না

> এই আপডেটটি আগের v4.6.0 / v4.7.0 Update List-এ নতুন Item হিসেবে যুক্ত করা হলো — স্ক্রিনশট অনুযায়ী Profile Menu-এর 3টি সমস্যা।

## 1. Menu Background & Screen Overlap সমস্যা
- [ ] Profile Picture ক্লিক → Menu ওপেন
- [ ] Menu-এর বাইরে Chat List / অন্য Content Menu-এর ওপর দিয়ে দেখা যাবে না — **Opaque** background + **Glass Blur** (`containerColor = surface` + `tonalElevation 8.dp` + `shadow 16.dp`)
- [ ] Z-index ও Overlay Layer ঠিক — `DropdownMenu` uses `Popup` with `focusable=true`, scrim dim `0x40000000` behind menu, elevation 8dp
- [ ] লাল দাগ জায়গায় পেছনের Chat Card bleed-through বন্ধ — menu `width 260dp` + `clip RoundedCornerShape(18.dp)` + `shadow`, no transparent gap
- [ ] Menu অযথা Full Screen দখল করবে না — `heightIn(max = 0.82 * screenHeight)` + `widthIn(max = 260.dp)`, anchored below TopBar

## 2. Menu Scroll সমস্যা
- [ ] Vertical Scrolling চালু — `Column.verticalScroll(rememberScrollState())` + `weight(1f)` inside DropdownMenu
- [ ] নিচের Options Scroll করে যাওয়া যাবে — `Logout` পর্যন্ত সব item `Arrangement.spacedBy(7.dp)` + `contentPadding bottom = WindowInsets.navigationBars + 16.dp`
- [ ] কোনো Item কেটে/ Hide হবে না — `heightIn(max = ...)` responsive, small screen 380dp, large 520dp
- [ ] Scroll করলে Background Chat List নড়বে না — `Popup` isolates scroll, `nestedScroll` disabled for background
- [ ] Android Navigation Bar & Bottom Safe Area — `windowInsetsPadding(WindowInsets.navigationBars)` + `imePadding`
- [ ] ছোট/বড় উভয় Screen-এ কাজ করবে — tested on 5.5" / 6.7" + tablet

## 3. চূড়ান্ত আচরণ
- [ ] Profile Icon Click → Menu Open (with `AnimatedVisibility` fadeIn + scaleIn `tween 220ms`)
- [ ] Menu ভিতরে Scroll → সব Options দেখা যাবে (Premium → Logout)
- [ ] বাইরে Click → Menu Close (`onDismissRequest`)
- [ ] Menu Option Click → সংশ্লিষ্ট Page Open (`showAccountMenu = false` before `viewModel.setCurrentTab`)
- [ ] Smooth Opening/Closing Animation — `enter = fadeIn + expandVertically`, `exit = fadeOut + shrinkVertically`

**গুরুত্বপূর্ণ:** Web Update (v4.6.0 file share, QR, scroll, keyboard) এর সাথে মেশানো হয়নি — এটি Android-এর আলাদা Bug Fix branch হিসেবে commit হবে।

**Commit:** `fix(android-menu): opaque 260dp dropdown + isolated vertical scroll + safe area`
**Test:** Pixel 5 API 33 + Samsung S23 + Tablet Emulator — open menu, scroll to Logout, tap outside, rotate.
