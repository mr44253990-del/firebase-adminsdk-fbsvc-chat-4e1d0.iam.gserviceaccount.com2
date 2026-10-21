# Convo Chat — বড় ভবিষ্যৎ পরিকল্পনা (v4.5 → v5.0)

এই তালিকা আগের 124-item v4.4 roadmap-এর **পরের 100টি আইডিয়া (#125–#224)**। এটি কাজ শেষ হওয়ার প্রতিশ্রুতি নয়; পছন্দ, নিরাপত্তা, backend, API key, Android সীমা ও release-test অনুযায়ী ধাপে বাস্তবায়ন করতে হবে।

**চিহ্ন:** [S] ছোট, [M] মাঝারি, [L] বড়; [KEY] বাইরের key/provider/account; [PRIV] privacy review দরকার; [NET] server/backend কাজ; [AI] AI-provider খরচ/নীতি প্রযোজ্য। Key না লিখলে app-এর বিদ্যমান stack-এ সম্ভব হওয়ার কথা, তবে build/permission verification প্রয়োজন।

## v4.4.3-তে সম্পন্ন—#125–#131

125. **Chat-list tools menu [S] — সম্পন্ন:** Saved, call history, local search, discovery, notes ও filter expandable panel-এ; conversation list-এর জন্য জায়গা বেশি।
126. **Fullscreen safe-area pass [M] — সম্পন্ন:** Pro Tools overlay, conversation settings ও text-file viewer-এ status/navigation/keyboard insets ঠিক করা।
127. **Modern local call history [M] — সম্পন্ন:** Search, missed/incoming/outgoing filters, summary, per-entry remove এবং দ্রুত audio/video callback।
128. **Smart wallpaper/system theme [S] — সম্পন্ন:** Android 12+ dynamic wallpaper colors + system light/dark; পুরোনো Android-এ fallback।
129. **Dynamic local-search loader [S] — সম্পন্ন:** Theme accent-নির্ভর animated loading state।
130. **SMS OTP assist [S] — সম্পন্ন:** Firebase instant/auto retrieval বজায় রেখে clipboard paste shortcut; বর্তমান Compose version-এ SMS Autofill hint API নেই।
131. **2-step code-entry assist [S] — সম্পন্ন:** বিদ্যমান MFA flow অক্ষত রেখে OTP/backup-code paste shortcut।

## A. Chat interaction ও navigation (#132–#141)

132. [M] **Swipe action editor:** swipe left/right-এর কাজ user নিজের মতো বেছে নেবে; undo snackbar ও accessibility button থাকবে।
133. [S] **Conversation density setting:** compact, comfortable, large text—প্রতি সারির avatar, spacing ও preview বেছে নেওয়া।
134. [M] **Inbox sections:** unread, pinned, groups ও people smart section; drag-and-drop ordering, পুরোনো filter অক্ষত।
135. [S] **Unread boundary marker:** নতুন/শেষ-পড়া মেসেজের রেখা ও “jump to latest” button।
136. [M] **Conversation activity summary:** last activity, unread count, muted/archived/locked state এক accessible summary-তে।
137. [M] **Draft recovery:** app crash/force-stop-এর পর composer text/attachment draft উদ্ধার; encrypt-at-rest preference।
138. [M] **Message delivery timeline:** sent/delivered/read timestamps ও retry state; server receipt ছাড়া “delivered” দাবি নয়।
139. [S] **Long-press action editor:** Save/Forward/Reply/Copy/Report action rearrange ও hide।
140. [M] **Chat jump index:** নির্দিষ্ট তারিখ, attachment, link, voice message-এ দ্রুত jump।
141. [S] **Universal in-app command search:** “Calls”, “Theme”, “Saved”, “MFA” লিখে feature খুলবে; প্রয়োজনীয় permission আগে জানাবে।

## B. Smart messaging ও group workflow (#142–#151)

142. [M] **Scheduled-message queue:** edit, reschedule, cancel; offline queue clear statusসহ; WorkManager best-effort caveat।
143. [M] **Draft sync:** opt-in end-to-end protected multi-device drafts; server-side conflict resolution।
144. [M] **Send-later time-zone safety:** recipient timezone preview এবং DST পরিবর্তনে schedule পুনর্নিরীক্ষা।
145. [S] **Quick-reply templates:** ব্যক্তিগত phrase, language ও shortcut; cloud sync ঐচ্ছিক।
146. [M] **Message translation preview:** original text না বদলে selected language translation; provider key/AI cost স্পষ্ট। [KEY][AI]
147. [M] **Voice-to-text caption:** on-device transcription preferred; language pack availability ও consent UI। [AI]
148. [M] **Group poll close/remind controls:** creator permission, anonymous poll disclosure, exportable results।
149. [M] **Shared task list in group:** owner, due date, completion history ও notification controls।
150. [L] **Threads ও reply digest:** নির্দিষ্ট message-এর reply stream, unread count ও mute; large group load test।
151. [M] **Group onboarding kit:** rules, pinned guide, member role checklist ও invite preview।

## C. Security, privacy ও account controls (#152–#161)

152. [L][PRIV] **E2EE design/rollout:** threat model, key rotation, recovery, multi-device protocol review—custom crypto নয়; vetted protocol evaluation।
153. [M][PRIV] **Key transparency diagnostics:** user দেখতে পারবে device-key change ও verification history; secure backend requirement।
154. [M] **Passkey sign-in:** Firebase/provider compatibility যাচাই, fallback recovery এবং phishing-safe UX।
155. [M] **Account recovery center:** verified email/phone, trusted-device review, recovery-code rotation; support-agent access control।
156. [M][PRIV] **Account export/delete wizard:** data categories, export progress, confirmation cooling period, server deletion audit।
157. [S] **Privacy dashboard:** last active, read receipts, typing, call availability ও profile visibility এক জায়গায়।
158. [M] **Per-chat screenshot privacy choice:** platform caveat দেখিয়ে sensitive previews hide; FLAG_SECURE policy chat/call screen-এ সীমিত থাকবে।
159. [M] **Linked-device trust levels:** revoke one device, suspend all others, location-optional device label; exact location default নয়।
160. [M] **Abuse/report evidence export:** user-controlled, redacted, time-limited evidence package; moderation audit trail।
161. [S] **Permission privacy receipts:** Contacts/Camera/Mic/Location কখন ও কেন ব্যবহার হয়েছে—স্থানীয় log ও clear action।

## D. Calling & live collaboration (#162–#171)

162. [M] **Cross-device call history sync:** user opt-in encrypted record; local-only fallback বজায়।
163. [M] **Group-call history:** group name, participant count, direction, duration, missed/failed state; per-entry privacy delete।
164. [M] **Call scheduling:** reminder, timezone, cancel/edit, invitation link expiry।
165. [M] **Call health panel:** network type, RTT/jitter/loss estimate, codec ও reconnect history; peer data সীমিত।
166. [M] **Adaptive video quality:** bandwidth/battery-aware resolution, user override ও data saver।
167. [M] **Live captions in calls:** on-device speech recognition first; consent before audio leaves device। [AI][PRIV]
168. [L] **Call screen sharing upgrades:** app-window sharing, pause/resume, visible privacy indicator and Android-version gates।
169. [M] **Call notes & action list:** participants opt-in; recording/transcription off by default and local-only option।
170. [M] **Focus/quiet hours for calls:** exception list, emergency override, local schedule; no false “guaranteed block” claim।
171. [L] **Low-bandwidth voice mode:** resilient audio, delayed video start, recovery after network switch; device matrix tests।

## E. Smart themes, accessibility ও loading (#172–#181)

172. [M] **Smart theme schedule:** sunrise/sunset or fixed schedule; system theme always remains an option।
173. [M] **Per-surface contrast tuning:** message text, cards, status chips ও call controls-এর WCAG contrast checks।
174. [S] **Reduced motion:** system preference অনুযায়ী animated backgrounds, loaders ও transitions কমানো।
175. [M] **Adaptive typography:** font scale and display-size stress test; clipping-free Bengali/Arabic/English layouts।
176. [M] **Theme preview lab:** live sample conversation, call, profile and settings previews before apply।
177. [M] **Color-blind status system:** read/missed/online state শুধু color দিয়ে নয়—icon+label+shape।
178. [S] **Skeleton loading library:** cached profile, call list, discovery, posts-এর shape-safe skeleton; error/retry distinct।
179. [M] **Offline-first state language:** pending, queued, synced, stale cache এবং retry time পরিষ্কারভাবে দেখানো।
180. [M] **Large-screen adaptive layout:** tablet/foldable panes, hinge-safe lists, keyboard and rotation tests।
181. [M] **Screen-reader navigation audit:** custom labels, headings, traversal order, talkback tests for dialogs and call UI।

## F. Smart assistant & personal productivity (#182–#191)

182. [M][AI] **Opt-in inbox digest:** unread summary তৈরি করার আগে chat scope, data destination ও retention জানানো।
183. [M][AI] **Writing tone controls:** concise/formal/friendly; generated text user না পাঠানো পর্যন্ত draft only।
184. [M][AI] **Reply suggestions locally:** safe canned suggestions first; no automatic send।
185. [M] **Personal reminder inbox:** reminder snooze/complete/repeat; exact alarms optional and reboot recovery documented।
186. [M][AI] **Meeting action extraction:** user-selected message range only; editable tasks, source links retained।
187. [M] **Focus session:** mute selected rooms for a duration; emergency contacts bypass option।
188. [M][AI] **Personal knowledge search:** user-approved notes/saved items only; per-source delete and local-index reset।
189. [M] **Calendar hand-off:** ICS intent export before any account integration; no silent calendar write।
190. [S] **Quick note widget:** device home-screen widget with lock-screen privacy and no decorative emoji।
191. [M] **Assistant usage controls:** provider, token limit, history retention, data opt-out, usage estimate।

## G. Media, files & data transfer (#192–#201)

192. [M] **In-app image annotate:** crop, blur, draw, alt text, undo; original file preserved until send।
193. [M] **Video trim/data saver presets:** local preview, size estimate and original-quality override।
194. [M] **Voice-message noise gate:** on-device processing and before/after preview।
195. [M] **Media gallery filters:** sender, date, file type, favorites and clear local cache controls।
196. [L] **Resumable large-file upload:** chunk hashes, pause/resume, expiry and server-side quota. [NET]
197. [M] **Encrypted attachment links:** recipient-bound access, expiry and revocation; key management review। [PRIV][NET]
198. [S] **Attachment integrity check:** size/hash/status; warn if incomplete or altered during transfer।
199. [M] **Document OCR search:** on-device ML Kit where supported; user opt-in and extracted-text deletion।
200. [M] **Storage insights:** per-type and per-conversation totals, cache distinction, safe delete preview।
201. [M] **Backup health panel:** exact single-file local backup behavior retained; show last success, size, restore check and encryption state।

## H. Reliability, notifications & device resilience (#202–#211)

202. [M] **Notification bundling controls:** per-chat channels, quiet hours, priority preview and Android version behavior।
203. [S] **Notification actions:** mark-read/reply only when authenticated and notification privacy allows।
204. [M] **Presence diagnostics:** server acknowledgement and app heartbeat separated; FCM accepted ≠ device received।
205. [M] **Reachability history:** user-facing “last acknowledged” signal; preserve 5-minute Active policy and avoid false live status।
206. [M] **Crash-free startup telemetry:** opt-in/minimized Crashlytics or privacy-preserving alternative; key/config review। [KEY]
207. [M] **Network transition recovery:** Wi-Fi/mobile/VPN switch stress test for messages, upload, call signaling।
208. [S] **Background work status page:** permission, battery optimization, FCM token and best-effort caveats।
209. [M] **App update migration tests:** Room/preferences migration, rollback safety and corrupted-cache recovery।
210. [M] **Notification deduplication:** server event IDs, open-chat suppression and cross-device read receipts।
211. [M] **Diagnostics export:** redacted logs, user preview, secrets/token scrubbing before share।

## I. Discovery, community & creator tools (#212–#221)

212. [M] **Friend-only picker standard:** all mention/share/forward/member pickers show friends only, searchable and keyboard accessible।
213. [M] **Profile link preview controls:** privacy-aware fields, expiry, block/report and no external tracking pixels।
214. [M] **Interest discovery:** opt-in tags, private by default, report/block and recommendation reset।
215. [M][AI] **Creator caption studio:** language/tone/length control, generated draft provenance, no auto publish।
216. [M] **Private post analytics:** coarse, minimum-count thresholds, no viewer identity exposure without consent।
217. [M] **Community moderation queue:** report status, reason taxonomy, reviewer permissions and immutable audit trail।
218. [M] **Scheduled community posts:** draft queue, local time preview, cancel and offline state।
219. [S] **Content warnings:** hide sensitive media/text behind user-set reveal action; preference per account/device।
220. [M] **Friend import preview:** normalize/compare contacts, exact opt-in, hash-only server matching and remove matches।
221. [M] **Share-sheet chooser hardening:** friend-only results, search and recipient confirmation for every send surface।

## J. Admin, backend governance & release quality (#222–#224)

222. [L][NET] **Backend deployment dashboard:** secrets readiness, edge-function health and deploy version; never expose secret values।
223. [M] **Feature-flag rollout controls:** staged percentage, admin kill switch, offline-safe defaults and audit history।
224. [M] **Automated release gate:** compile, unit/UI test, rules tests, worker checks, emulator smoke and signed artifact provenance।

## বাস্তবায়ন নীতি ও dependency

1. একবারে ছোট, পরীক্ষাযোগ্য batch; features একত্রে push হলেও CI green না হওয়া পর্যন্ত release claim নয়।
2. **Firebase Functions নয়।** Scheduled presence কাজের জন্য Supabase Edge Functions; exactly one active scheduler (Supabase cron অথবা Cloudflare Cron, দুটো একসাথে নয়)।
3. Firebase service-account JSON/key শুধু server secret store-এ; Android, Firestore client document, Git ও public log-এ নয়।
4. FCM send accepted মানেই device received নয়; device acknowledgment ছাড়া reachable/online বলা যাবে না। Reachable status 5 মিনিট Active থাকবে।
5. Chat partner সামনে খোলা থাকলে তার message notification নয়। TikTok in-app থাকবে; “Not now” best-effort মাত্র।
6. Screenshot `FLAG_SECURE` শুধু chat/call screen-এ; call minimize/PiP শুধু active video call-এ। Voice gesture: নিচে টানলে cancel, ওপরে ছাড়লে send।
7. সব picker/share chooser-এ friends-only + search। Download করা received file Downloads-এর app-named folder ও type অনুযায়ী। Local backup একটিই file, একই file overwrite/restore।
8. Phone login admin-toggleable; signup-এ email ও phone। Gmail OAuth tokens server-side online-এ সংরক্ষিত হবে—mobile client-এ নয়।
9. নতুন key লাগলে release note-এ কোন provider, কোন setting, কোন secret name, এবং missing কি না—স্পষ্টভাবে তালিকা করতে হবে।
10. No decorative emoji in app UI; reaction emoji user-generated content হিসেবে রাখা যায়।

## প্রস্তাবিত ধাপ

- **v4.5:** #132–#151 (navigation, drafts, productivity) + basic accessibility audit.
- **v4.6:** #152–#171 (security design ও calls; বড় backend/security review আলাদা batch).
- **v4.7:** #172–#191 (theme, accessibility, assistant privacy controls).
- **v4.8:** #192–#211 (media, backups, reliability).
- **v5.0:** #212–#224 (discovery governance, rollout, release-quality automation).

প্রতিটি phase-এ user-নির্বাচিত scope, missing credentials, deployment owner, permission/API behavior, migration এবং test status আগে নিশ্চিত করতে হবে।
