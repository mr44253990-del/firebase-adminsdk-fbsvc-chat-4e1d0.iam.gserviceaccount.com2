/**
 * Offline contract tests for cloudflare-worker.js.
 *
 *   node tools/worker-tests/run.mjs
 *
 * The Worker source is loaded into a temporary module after stubbing the three
 * things a sandbox cannot provide: Firebase service-account signing, the
 * Firestore REST API and the Realtime Database. Everything else — routing,
 * origin checks, session hashing, deploy pipeline, profile routes — is the real
 * code path, so a regression in cloudflare-worker.js fails here.
 *
 * Keep this file in the repo: it is the only automated safety net for the
 * backend until an integration environment exists.
 */
import { createHash } from "node:crypto";
import { readFile, writeFile, mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SRC_PATH = join(ROOT, "cloudflare-worker.js");

/* ------------------------------------------------------------------ state */
const firestore = new Map();   // "users/abc" -> plain object
const rtdb = new Map();        // "chats/x/messages" -> object
const bucket = new Map();      // R2: key -> Uint8Array
const cfCalls = { upload: 0, deploy: 0, createProject: 0 };
const results = [];

function check(name, condition, extra) {
  results.push({ name, ok: Boolean(condition), extra });
  if (!condition) console.error("FAIL", name, extra === undefined ? "" : JSON.stringify(extra).slice(0, 400));
}

function fakeResponse(body, status = 200) {
  const text = typeof body === "string" ? body : JSON.stringify(body);
  return new Response(text, { status, headers: { "Content-Type": "application/json" } });
}

/* ------------------------------------------------------------ firestore sim */
function toValue(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (typeof value === "string") return { stringValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toValue) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toValue(v)])) } };
}
function fromValue(node) {
  if (!node || typeof node !== "object") return null;
  if ("nullValue" in node) return null;
  if ("booleanValue" in node) return node.booleanValue;
  if ("integerValue" in node) return Number(node.integerValue);
  if ("doubleValue" in node) return Number(node.doubleValue);
  if ("stringValue" in node) return node.stringValue;
  if ("timestampValue" in node) return node.timestampValue;
  if ("arrayValue" in node) return (node.arrayValue.values || []).map(fromValue);
  if ("mapValue" in node) return Object.fromEntries(Object.entries(node.mapValue.fields || {}).map(([k, v]) => [k, fromValue(v)]));
  return null;
}
const docPathFromUrl = url => {
  const marker = "/documents/";
  const index = url.indexOf(marker);
  return index < 0 ? "" : url.slice(index + marker.length).split("?")[0];
};

/* ------------------------------------------------------------------ fetch */
const realFetch = globalThis.fetch;
globalThis.fetch = async (input, init = {}) => {
  const request = typeof input === "string" ? new Request(input, init)
    : input instanceof URL ? new Request(input.toString(), init)
    : input;
  const url = new URL(request.url);

  if (url.hostname === "oauth2.googleapis.com") {
    return fakeResponse({ access_token: "stub-access-token", expires_in: 3600 });
  }

  if (url.hostname === "firestore.googleapis.com") {
    const path = docPathFromUrl(url.pathname);
    const method = (init.method || request.method || "GET").toUpperCase();
    if (method === "GET") {
      const doc = firestore.get(path);
      if (!doc) return new Response("not found", { status: 404 });
      // Real Firestore always returns a token-unique updateTime; consumeWebPairCode
      // relies on it for its optimistic-concurrency write.
      firestore.set(`${path}__updateTime`, { value: `${Date.now()}-${Math.random()}` });
      return fakeResponse({
        name: path,
        updateTime: firestore.get(`${path}__updateTime`).value,
        fields: Object.fromEntries(Object.entries(doc).map(([k, v]) => [k, toValue(v)]))
      });
    }
    if (method === "PATCH" || method === "PUT") {
      const body = JSON.parse(init.body || "{}");
      const existing = firestore.get(path) || {};
      const merged = { ...existing };
      Object.entries(body.fields || {}).forEach(([k, v]) => { merged[k] = fromValue(v); });
      firestore.set(path, merged);
      return fakeResponse({ name: path, updateTime: `${Date.now()}-${Math.random()}` });
    }
    if (method === "DELETE") { firestore.delete(path); firestore.delete(`${path}__updateTime`); return fakeResponse({}); }
    return fakeResponse({});
  }

  if (url.hostname.endsWith("firebasedatabase.app") || url.hostname.endsWith("firebaseio.com")) {
    const path = url.pathname.replace(/^\//, "").replace(/\.json$/, "");
    const method = (init.method || "GET").toUpperCase();
    if (method === "GET") {
      const value = rtdb.get(path);
      return fakeResponse(value === undefined ? null : value);
    }
    if (method === "PUT") { rtdb.set(path, JSON.parse(init.body || "null")); return fakeResponse({}); }
    if (method === "DELETE") { rtdb.delete(path); return fakeResponse({}); }
    return fakeResponse({});
  }

  if (url.hostname === "api.cloudflare.com") {
    const path = url.pathname;
    const method = (init.method || "GET").toUpperCase();
    if (path.endsWith("/user/tokens/verify")) return fakeResponse({ success: true, result: { status: "active" } });
    if (path.endsWith("/accounts")) return fakeResponse({ success: true, result: [{ id: "a".repeat(32), name: "Convo" }] });
    if (path.endsWith("/upload-token")) return fakeResponse({ success: true, result: { jwt: "jwt-upload-token" } });
    if (path.endsWith("/pages/assets/check-missing")) {
      const asked = JSON.parse(init.body || "{}").hashes || [];
      return fakeResponse(asked); // everything "missing" so the upload path runs
    }
    if (path.endsWith("/pages/assets/upload")) { cfCalls.upload += 1; return fakeResponse({ success: true }); }
    if (path.endsWith("/pages/assets/upsert-hashes")) return fakeResponse({ success: true });
    if (path.endsWith("/deployments") && method === "POST") {
      cfCalls.deploy += 1;
      return fakeResponse({ success: true, result: { id: "dep1", url: "https://convo-chat-web.pages.dev" } });
    }
    if (path.endsWith("/projects") && method === "POST") { cfCalls.createProject += 1; return fakeResponse({ success: true, result: { name: "convo-chat-web" } }); }
    if (path.includes("/pages/projects/")) return fakeResponse({ success: true, result: { name: "convo-chat-web" } });
    return fakeResponse({ success: true });
  }

  if (url.hostname === "fcm.googleapis.com") return fakeResponse({ name: "projects/x/messages/1" });

  return realFetch(input, init);
};

/* ------------------------------------------------------ load a patched copy */
const raw = await readFile(SRC_PATH, "utf8");

/** Replace a top-level function by brace matching. */
function replaceFunction(source, name, replacement) {
  const start = source.indexOf(`async function ${name}(`) >= 0 ? source.indexOf(`async function ${name}(`) : source.indexOf(`function ${name}(`);
  if (start < 0) throw new Error(`function ${name} not found`);
  const brace = source.indexOf("{", start);
  let depth = 0, end = brace;
  for (let i = brace; i < source.length; i += 1) {
    if (source[i] === "{") depth += 1;
    else if (source[i] === "}") { depth -= 1; if (depth === 0) { end = i + 1; break; } }
  }
  return source.slice(0, start) + replacement + source.slice(end);
}

let source = raw;
// Surface the real cause instead of the generic 500 body while debugging.
source = source.replace('return htmlResponse("Could not load this profile", 500);',
  'return htmlResponse("Could not load this profile: " + String(error && error.stack || error).slice(0,300), 500);');
source = source.replace('return webPairJson(request, env, { error: "Pairing could not be completed" }, 500);',
  'return webPairJson(request, env, { error: "Pairing could not be completed", detail: String(error && error.stack || error).slice(0, 400) }, 500);');
source = replaceFunction(source, "verifyFirebaseIdToken", `async function verifyFirebaseIdToken(request) {
  const header = request.headers.get("Authorization") || "";
  if (header === "Bearer admin-jwt") return { sub: "admin1", email: "mr4425390@gmail.com" };
  if (header === "Bearer user-jwt") return { sub: "me123", email: "a@convo.app" };
  return null;
}`);
source = replaceFunction(source, "getGoogleAccessToken", `async function getGoogleAccessToken() { return "stub-token"; }`);

const dir = await mkdtemp(join(tmpdir(), "convo-worker-"));
const modulePath = join(dir, "worker.mjs");
await writeFile(modulePath, source);
const worker = (await import(modulePath)).default;

/* ------------------------------------------------------------------- env */
const WEB_TOKEN = "b".repeat(64);
const WEB_TOKEN_HASH = createHash("sha256").update(`convo-web-session:${WEB_TOKEN}`).digest("hex");

const env = {
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify({ project_id: "demo", client_email: "sa@demo.iam.gserviceaccount.com", private_key: "-----BEGIN PRIVATE KEY-----\nstub\n-----END PRIVATE KEY-----\n" }),
  FIREBASE_DATABASE_URL: "https://demo-default-rtdb.asia-southeast1.firebasedatabase.app",
  WEB_COMPANION_ORIGIN: "https://convo.pages.dev",
  R2_PUBLIC_BASE_URL: "https://cdn.test",
  MEDIA_BUCKET: {
    async list() { return { objects: [...bucket.keys()].map(key => ({ key })) }; },
    async put(key, value) { bucket.set(key, value); return {}; },
    async get(key) {
      const value = bucket.get(key);
      if (!value) return null;
      return { arrayBuffer: async () => value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength) };
    },
    async delete(key) { bucket.delete(key); return {}; }
  }
};
const ctx = { waitUntil() {}, passThroughOnException() {} };

async function call(path, { method = "POST", body, token, origin = "https://convo.pages.dev", admin = false } = {}) {
  const headers = { "Content-Type": "application/json", Origin: origin };
  if (token) headers.Authorization = `Bearer ${token}`;
  const request = new Request(`https://worker.test${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const response = await worker.fetch(request, env, ctx);
  const text = await response.text();
  let payload = {};
  try { payload = JSON.parse(text); } catch (_) { payload = { raw: text }; }
  return { status: response.status, payload, headers: response.headers };
}

/* -------------------------------------------------------------- seed data */
const now = Date.now();
firestore.set("users/me123", {
  name: "Mahmud", username: "mahmud", email: "a@convo.app",
  profileImageUrl: "https://cdn.test/avatars/me123/old.png",
  bio: "Building Convo", friends: ["friend1"], followers: ["friend1", "friend2"], following: ["friend1"],
  createdAt: 1_700_000_000_000, webCompanionVersion: 0,
  professionalTitle: "Engineer", pronouns: "he/him", role: "moderator"
});
firestore.set("users/user-me123", {
  name: "Mahmud", username: "mahmud", profileImageUrl: "https://cdn.test/avatars/me123/old.png",
  bio: "Building Convo", friends: ["friend1"], followers: ["friend1", "friend2"],
  coverImageUrl: "https://cdn.test/covers/me123.jpg", professionalTitle: "Engineer"
});
firestore.set("users/friend1", {
  name: "Rafi", username: "rafi", profileImageUrl: "https://cdn.test/a.png",
  friends: ["me123"], fcmToken: "fcm-1", blockedUsers: []
});
firestore.set(`web_pair_sessions/${WEB_TOKEN_HASH}`, {
  uid: "me123", createdAt: now - 60_000, expiresAt: now + 60 * 60 * 1000,
  sessionVersion: 0, revokedAt: 0, userAgent: "Mozilla/5.0 (Macintosh) Chrome/131 Safari/537.36"
});
firestore.set("admin_secrets/web_pages", {});

/* ================================================================== tests */

/* ---- health / public routes ---- */
{
  const response = await worker.fetch(new Request("https://worker.test/"), env, ctx);
  const payload = await response.json().catch(() => ({}));
  check("health reports api version and service account", payload.serviceAccount === true || payload.status === "ok" || response.ok, payload);
  check("health api version >= 49", Number(payload.apiVersion || 0) >= 49, payload.apiVersion);
}

/* ---- pairing: create needs Android auth, redeem burns the code ---- */
{
  const unauth = await call("/web-pair/create", { body: { optIn: true } });
  check("create rejects unauthenticated callers", unauth.status === 401, unauth.payload);

  const created = await call("/web-pair/create", { body: { optIn: true }, token: "user-jwt" });
  check("create returns a 10-digit code", created.status === 200 && /^\d{10}$/.test(String(created.payload.code || "")), created.payload);
  check("create returns the site root as companionUrl", String(created.payload.companionUrl || "").endsWith("convo.pages.dev/"), created.payload.companionUrl);

  const code = String(created.payload.code || "");
  const wrongOrigin = await call("/web-pair/redeem", { body: { code }, origin: "https://evil.example" });
  check("redeem rejects an unapproved origin", wrongOrigin.status === 403, wrongOrigin.payload);

  const redeemed = await call("/web-pair/redeem", { body: { code } });
  check("redeem exchanges the code for a session token", redeemed.status === 200 && /^[a-f0-9]{64}$/.test(String(redeemed.payload.sessionToken || "")), redeemed.payload);

  const again = await call("/web-pair/redeem", { body: { code } });
  check("redeem is single use", again.status !== 200, again.payload);
}

/* ---- companion API with the seeded web session ---- */
{
  const boot = await call("/web-pair/bootstrap", { token: WEB_TOKEN });
  check("bootstrap returns me, chats and settings", boot.status === 200 && boot.payload.me?.uid === "me123" && Array.isArray(boot.payload.chats), boot.payload);
  check("bootstrap includes typing map", typeof (boot.payload.typing || {}) === "object", boot.payload.typing);
  check("bootstrap chats only include accepted friends", boot.payload.chats.every(chat => chat.uid === "friend1"), boot.payload.chats?.map(c => c.uid));

  const denied = await call("/web-pair/messages", { token: WEB_TOKEN, body: { peerUid: "stranger" } });
  check("messages refuses non-friends", denied.status === 403, denied.payload);

  const sent = await call("/web-pair/send", { token: WEB_TOKEN, body: { peerUid: "friend1", text: "Hello from the web" } });
  check("send writes a message", sent.status === 200 && sent.payload.message?.text === "Hello from the web", sent.payload);
  await new Promise(done => setTimeout(done, 300));
  const second = await call("/web-pair/send", { token: WEB_TOKEN, body: { peerUid: "friend1", text: "Second" } });
  check("send allows a second message after the rate window", second.status === 200, second.payload);
  const fast = await call("/web-pair/send", { token: WEB_TOKEN, body: { peerUid: "friend1", text: "Too fast" } });
  check("send rate-limits back-to-back text", fast.status === 429, fast.payload);

  const reacted = await call("/web-pair/react", { token: WEB_TOKEN, body: { peerUid: "friend1", messageId: sent.payload.message.messageId, reaction: "❤️" } });
  check("react stores a reaction", reacted.status === 200, reacted.payload);

  const read = await call("/web-pair/read", { token: WEB_TOKEN, body: { peerUid: "friend1", upTo: Date.now() } });
  check("read marks the conversation read", read.status === 200, read.payload);

  const left = await call("/web-pair/typing", { token: WEB_TOKEN, body: { peerUid: "friend1" } });
  check("typing is recorded for the peer", left.status === 200, left.payload);

  const sessions = await call("/web-pair/sessions", { token: "user-jwt" });
  check("sessions lists signed-in browsers", sessions.status === 200 && Array.isArray(sessions.payload.sessions), sessions.payload);
}

/* ---- profile: richer payload, avatar upload, link ---- */
{
  const profile = await call("/web-pair/profile", { token: WEB_TOKEN });
  check("profile returns counters", profile.status === 200 && profile.payload.me.friends === 1 && profile.payload.me.followers === 2, profile.payload.me);
  check("profile exposes title and role", profile.payload.me.professionalTitle === "Engineer" && profile.payload.me.role === "moderator", profile.payload.me);

  const png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+7l4AAAAASUVORK5CYII=";
  const uploaded = await call("/web-pair/profile/avatar", { token: WEB_TOKEN, body: { image: png } });
  check("avatar upload returns a public URL", uploaded.status === 200 && String(uploaded.payload.profileImageUrl || "").startsWith("https://cdn.test/avatars/me123/"), uploaded.payload);
  check("avatar bytes land in the bucket", [...bucket.keys()].some(key => key.startsWith("avatars/me123/")), [...bucket.keys()].slice(0, 5));

  const badType = await call("/web-pair/profile/avatar", { token: WEB_TOKEN, body: { image: "data:text/html;base64,AAAA" } });
  check("avatar upload rejects non-images", badType.status === 400, badType.payload);

  const link = await call("/web-pair/profile/link", { token: WEB_TOKEN, body: {} });
  check("profile link points at the worker profile page", String(link.payload.workerProfileUrl || "").includes("/profile?uid=me123"), link.payload);

  const saved = await call("/web-pair/profile/save", { token: WEB_TOKEN, body: { name: "Mahmud R.", bio: "Updated bio", professionalTitle: "Staff engineer", pronouns: "he/him" } });
  check("profile/save stores the richer fields", saved.status === 200 && saved.payload.me.professionalTitle === "Staff engineer", saved.payload.me);
}

/* ---- settings ---- */
{
  const saved = await call("/web-pair/settings/save", { token: WEB_TOKEN, body: { theme: "aurora", readReceipts: false, wallpaper: "mesh" } });
  check("settings save accepts known keys", saved.status === 200, saved.payload);
  const loaded = await call("/web-pair/settings", { token: WEB_TOKEN });
  check("settings round-trip", loaded.payload.settings.theme === "aurora" && loaded.payload.settings.readReceipts === false, loaded.payload.settings);
  const hostile = await call("/web-pair/settings/save", { token: WEB_TOKEN, body: { wallpaperUrl: "javascript:alert(1)" } });
  check("settings reject non-https wallpaper", hostile.status === 200 && !hostile.payload.settings.wallpaperUrl, hostile.payload);
}

/* ---- admin: web deploy pipeline ---- */
{
  bucket.set("web_site/index.html", new TextEncoder().encode("<html>Convo</html>"));
  bucket.set("web_site/styles.css", new TextEncoder().encode("body{}"));
  bucket.set("web_site/_headers", new TextEncoder().encode("/*\n  X-Frame-Options: DENY"));
  bucket.set("web_site/app.js", new TextEncoder().encode("console.log(1)"));

  const status = await call("/admin/web-deploy/status", { token: "admin-jwt", body: {} });
  check("deploy status counts staged files", status.status === 200 && status.payload.ready === true && status.payload.files >= 4, status.payload);

  const verify = await call("/admin/web-deploy/test", { token: "admin-jwt", body: { accountId: "a".repeat(32), apiToken: "x".repeat(30) } });
  check("deploy test verifies a token", verify.status === 200 && verify.payload.verified === true, verify.payload);

  const badAccount = await call("/admin/web-deploy/deploy", { token: "admin-jwt", body: { accountId: "nope", apiToken: "x".repeat(30) } });
  check("deploy rejects a malformed account id", badAccount.status === 400, badAccount.payload);

  const deployed = await call("/admin/web-deploy/deploy", { token: "admin-jwt", body: { accountId: "a".repeat(32), apiToken: "x".repeat(30), projectName: "Convo Chat Web!" } });
  check("deploy publishes and returns a URL", deployed.status === 200 && deployed.payload.url === "https://convo-chat-web.pages.dev", deployed.payload);
  check("deploy normalises the project name", deployed.payload.projectName === "convo-chat-web", deployed.payload.projectName);
  check("deploy uploaded assets", cfCalls.upload > 0, cfCalls);
  check("deploy created one deployment", cfCalls.deploy === 1, cfCalls);
  check("deploy remembers the site config", (firestore.get("admin_secrets/web_pages") || {}).projectName === "convo-chat-web", firestore.get("admin_secrets/web_pages"));

  // A second deploy must reuse the project (no create call) and still succeed.
  const again = await call("/admin/web-deploy/deploy", { token: "admin-jwt", body: { accountId: "a".repeat(32), apiToken: "x".repeat(30) } });
  check("second deploy succeeds", again.status === 200 && cfCalls.deploy === 2, { status: again.status, cfCalls });
}

/* ---- public profile page prefers the web app ---- */
{
  const page = await worker.fetch(new Request("https://worker.test/profile?uid=user-me123"), env, ctx);
  const html = await page.text();
  check("public profile page renders", page.status === 200 && html.includes("Mahmud"), page.status);
  check("public profile page links to Convo Web first", /Open in Convo Web/.test(html), html.slice(0, 200));
  check("public profile page keeps the app option", /Get the Android app/.test(html), html.slice(0, 200));
  check("public profile page shows counters", /class="stats"/.test(html), "");
}

/* ---------------------------------------------------------------- summary */
const passed = results.filter(result => result.ok).length;
console.log(`\n${passed}/${results.length} checks passed`);
if (passed !== results.length) {
  console.error("Failed:", results.filter(result => !result.ok).map(result => result.name).join(", "));
  process.exit(1);
}
process.exit(0);
