/**
 * Convo Chat — Auto AI Posts (Supabase Edge Function).
 *
 * The admin configures Firestore `app_config/auto_post` from the app (Admin → Auto AI posts):
 *   enabled, postsPerDay, postType, prompt, language, useEmoji, startHour, endHour,
 *   tzOffsetMinutes, adminUid, runNowRequestedAt
 * Supabase Cron calls POST /cron every 10 minutes. When a post is due, this function asks
 * Mistral for a short post and writes it to Firestore `posts` as the admin account, so posts
 * keep appearing even while every phone (including the admin's) is closed.
 *
 * It also closes expired polls (`polls` with active=true and endsAt in the past).
 *
 * Routes (base: https://<project-ref>.supabase.co/functions/v1/auto-ai-post):
 *   GET  /health   public: shows whether secrets are configured
 *   POST /cron     x-cron-secret header required (same secret as presence-monitor)
 *
 * Secrets: FIREBASE_SERVICE_ACCOUNT, PRESENCE_CRON_SECRET, MISTRAL_API_KEY
 * Deploy:  supabase functions deploy auto-ai-post --no-verify-jwt
 */
// ---------------------------------------------------------------------------------------
// Self-contained helpers. This file has NO local imports, so it deploys the same way from
// the Supabase Dashboard editor (single file) or the CLI. Earlier versions imported
// ../presence-monitor/presence.js, which the Dashboard bundler cannot see
// ("Module not found .../presence-monitor/presence.js").
// ---------------------------------------------------------------------------------------
// deno-lint-ignore no-explicit-any
type Any = any;

function corsHeaders(extra: Record<string, string> = {}): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, apikey, x-client-info, x-cron-secret",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Cache-Control": "no-store",
    ...extra,
  };
}

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: corsHeaders({ "Content-Type": "application/json; charset=utf-8" }) });
}

function safeError(error: Any): string {
  return String(error?.message || "Request failed").slice(0, 240);
}

function clampInt(value: unknown, minimum: number, maximum: number, fallback: number): number {
  const number = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(number) ? Math.min(maximum, Math.max(minimum, number)) : fallback;
}

function readServiceAccount(source: { FIREBASE_SERVICE_ACCOUNT?: string }): Any {
  try {
    return JSON.parse(source.FIREBASE_SERVICE_ACCOUNT || "{}");
  } catch (_) {
    return null;
  }
}

function base64UrlEncode(value: Any): string {
  const bytes: Uint8Array = value instanceof Uint8Array ? value : new TextEncoder().encode(JSON.stringify(value));
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + 0x8000, bytes.length)));
  }
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function signRs256(value: string, pem: string): Promise<string> {
  const base64 = String(pem || "")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\\n/g, "")
    .replace(/\s+/g, "");
  if (!base64) throw new Error("Firebase private key is missing");
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", bytes.buffer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(signature));
}

let cachedToken = "";
let cachedTokenUntil = 0;

async function getGoogleAccessToken(account: Any, scope: string): Promise<string> {
  if (cachedToken && Date.now() < cachedTokenUntil) return cachedToken;
  const now = Math.floor(Date.now() / 1000);
  const input = `${base64UrlEncode({ alg: "RS256", typ: "JWT" })}.${base64UrlEncode({
    iss: account.client_email,
    scope,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })}`;
  const assertion = `${input}.${await signRs256(input, account.private_key)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.access_token) throw new Error(`Could not obtain a Firebase service access token (${response.status})`);
  cachedToken = result.access_token;
  cachedTokenUntil = Date.now() + 50 * 60_000;
  return cachedToken;
}

function fromFirestoreValue(value: Any): Any {
  if (!value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("timestampValue" in value) return Date.parse(value.timestampValue);
  if ("nullValue" in value) return null;
  if (value.arrayValue) return (value.arrayValue.values || []).map(fromFirestoreValue);
  if (value.mapValue) return Object.fromEntries(Object.entries(value.mapValue.fields || {}).map(([k, v]) => [k, fromFirestoreValue(v)]));
  return null;
}

async function firestoreGet(projectId: string, documentPath: string, token: string): Promise<Any> {
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${documentPath}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore read failed (${response.status})`);
  const document = await response.json();
  return Object.fromEntries(Object.entries(document.fields || {}).map(([k, v]) => [k, fromFirestoreValue(v)]));
}

async function firestorePatch(projectId: string, documentPath: string, data: Any, token: string): Promise<Any> {
  const fields = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, toValue(v)]));
  const url = new URL(`https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${documentPath}`);
  Object.keys(data).forEach((field) => url.searchParams.append("updateMask.fieldPaths", field));
  const response = await fetch(url.toString(), {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
  if (!response.ok) throw new Error(`Firestore merge failed (${response.status})`);
  return response.json().catch(() => ({}));
}

declare const EdgeRuntime: { waitUntil?: (promise: Promise<unknown>) => void } | undefined;

const VERSION = "1.1.0-standalone";
const SCOPE = "https://www.googleapis.com/auth/datastore";
const DAY_MS = 24 * 60 * 60 * 1000;
const POST_TYPES = ["status", "tip", "quote", "question", "fun_fact", "motivation", "poll", "news", "story"] as const;
const BACKGROUNDS = ["sunset", "ocean", "aurora", "forest", "rose", "midnight", "candy", "peach", "gold", "mint"];

// deno-lint-ignore no-explicit-any
type Json = Record<string, any>;

function env(name: string): string {
  return Deno.env.get(name) ?? "";
}

function safeEqual(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  let diff = left.length ^ right.length;
  for (let i = 0; i < Math.max(left.length, right.length); i++) diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  return diff === 0;
}

function cronAuthorized(request: Request): boolean {
  const expected = env("AUTO_POST_CRON_SECRET") || env("PRESENCE_CRON_SECRET");
  if (expected.length < 24) return false;
  return safeEqual(request.headers.get("x-cron-secret") ?? "", expected);
}

function firestoreBase(projectId: string): string {
  return `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents`;
}

// deno-lint-ignore no-explicit-any
function toValue(value: any): Json {
  if (value == null) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toValue) } };
  if (typeof value === "object") return { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toValue(v)])) } };
  return { stringValue: String(value) };
}

async function firestoreCreate(projectId: string, collection: string, id: string, data: Json, token: string) {
  const fields = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, toValue(v)]));
  const response = await fetch(`${firestoreBase(projectId)}/${collection}?documentId=${encodeURIComponent(id)}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
  if (!response.ok) throw new Error(`Firestore create ${collection} failed (${response.status})`);
}

/** Active polls whose deadline passed: mark them inactive so they disappear everywhere. */
async function closeExpiredPolls(projectId: string, token: string): Promise<number> {
  const response = await fetch(`${firestoreBase(projectId)}:runQuery`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "polls" }],
        where: { fieldFilter: { field: { fieldPath: "active" }, op: "EQUAL", value: { booleanValue: true } } },
        limit: 100,
      },
    }),
  });
  if (!response.ok) return 0;
  const rows = await response.json().catch(() => []);
  const now = Date.now();
  let closed = 0;
  for (const row of Array.isArray(rows) ? rows : []) {
    const doc = row?.document;
    if (!doc) continue;
    const endsAt = Number(doc.fields?.endsAt?.integerValue ?? doc.fields?.endsAt?.doubleValue ?? 0);
    if (endsAt > 0 && endsAt <= now) {
      const id = String(doc.name).split("/").pop();
      await firestorePatch(projectId, `polls/${id}`, { active: false, closedBy: "auto" }, token).catch(() => {});
      closed++;
    }
  }
  return closed;
}

/** Hour of day in the admin's timezone (default Asia/Dhaka, UTC+6). */
function localHour(now: number, tzOffsetMinutes: number): number {
  return new Date(now + tzOffsetMinutes * 60_000).getUTCHours();
}

function insideWindow(hour: number, start: number, end: number): boolean {
  if (start === end) return true; // whole day
  return start < end ? hour >= start && hour < end : hour >= start || hour < end;
}

function pickType(configured: string): string {
  if ((POST_TYPES as readonly string[]).includes(configured)) return configured;
  // "news" needs web search and "story" is the multi-part series; neither is picked at random.
  const pool = POST_TYPES.filter((t) => t !== "poll" && t !== "news" && t !== "story");
  return pool[Math.floor(Math.random() * pool.length)];
}

const TYPE_GUIDE: Record<string, string> = {
  status: "a friendly community status update or greeting that fits the time of day",
  tip: "one genuinely useful, practical tip (tech, productivity, safety, health basics or using the Convo Chat app)",
  quote: "an inspiring short quote (only attribute it if you are certain of the author) with a one-line reflection",
  question: "an engaging open question that invites people to comment",
  fun_fact: "one surprising but TRUE, well-known fun fact (no made-up statistics)",
  motivation: "a short motivational message",
  poll: "a light, fun community poll question with 2 to 4 short answer options",
  news: "a short, neutral summary of ONE fresh, interesting and non-political item from the web results (tech, science, sport, culture), ending with why it matters",
  story: "one part of an engaging multi-part story",
};

/** Fresh web results for news-style posts: Tavily (admin key) first, then keyless DuckDuckGo. */
async function webResults(projectId: string, token: string, query: string): Promise<{ title: string; url: string; snippet: string }[]> {
  const cfg: Json = (await firestoreGet(projectId, "admin_secrets/search", token).catch(() => null)) ?? {};
  const strip = (v: unknown) => String(v ?? "").replace(/<[^>]+>/g, "").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim();
  const tavily = String(cfg.tavilyKey || env("TAVILY_API_KEY") || "").trim();
  if (tavily) {
    try {
      const r = await fetch("https://api.tavily.com/search", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${tavily}` }, body: JSON.stringify({ query, max_results: 5, topic: "news", search_depth: "basic" }) });
      const j: Json = await r.json().catch(() => ({}));
      if (r.ok && Array.isArray(j.results) && j.results.length) return j.results.map((x: Json) => ({ title: strip(x.title), url: String(x.url || ""), snippet: strip(x.content).slice(0, 300) }));
    } catch (_) { /* fall through */ }
  }
  try {
    const r = await fetch(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`, { headers: { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36" } });
    const html = r.ok ? await r.text() : "";
    const out: { title: string; url: string; snippet: string }[] = [];
    const re = /<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>[\s\S]*?(?:class="result__snippet"[^>]*>([\s\S]*?)<\/a>)?/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(html)) && out.length < 5) {
      let href = m[1].replace(/&amp;/g, "&");
      const u = href.match(/[?&]uddg=([^&]+)/);
      if (u) href = decodeURIComponent(u[1]);
      if (href.startsWith("//")) href = "https:" + href;
      if (!/^https?:\/\//.test(href) || href.includes("duckduckgo.com/y.js")) continue;
      out.push({ title: strip(m[2]), url: href, snippet: strip(m[3] || "").slice(0, 300) });
    }
    return out;
  } catch (_) { return []; }
}

/**
 * Generate an auto-post image before publishing, then store it in Supabase Storage so clients
 * fetch a stable, cacheable file instead of waiting on a third-party image-generation URL.
 * If the admin has not configured OVH image generation or a public posts bucket, use the
 * post's styled background instead of publishing a slow/broken external image link.
 */
async function generateCachedPostImage(projectId: string, token: string, prompt: string): Promise<string> {
  const cleanPrompt = prompt.replace(/[\r\n]+/g, " ").trim().slice(0, 900);
  if (!cleanPrompt) return "";
  const ai: Json = (await firestoreGet(projectId, "admin_secrets/ai", token).catch(() => null)) ?? {};
  const imageProvider: Json = ai.imageProvider && typeof ai.imageProvider === "object" ? ai.imageProvider : {};
  const ovhToken = String(imageProvider.apiKey || "");
  if (imageProvider.enabled !== true || ovhToken.length < 8) return "";

  const storage: Json = (await firestoreGet(projectId, "app_config/storage", token).catch(() => null)) ?? {};
  const supabaseUrl = String(env("SUPABASE_URL") || storage.supabaseUrl || "").replace(/\/+$/, "");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");
  const bucket = String(storage.bucketPosts || "posts").trim();
  if (!/^https:\/\//i.test(supabaseUrl) || serviceKey.length < 20 || !/^[A-Za-z0-9_-]{1,100}$/.test(bucket)) return "";

  try {
    const upstream = await fetch("https://stable-diffusion-xl.endpoints.kepler.ai.cloud.ovh.net/api/text2image", {
      method: "POST",
      headers: { Authorization: `Bearer ${ovhToken}`, "Content-Type": "application/json", Accept: "image/*, application/octet-stream" },
      body: JSON.stringify({ prompt: cleanPrompt, negative_prompt: "text, letters, watermark, logo, blurry, low quality" }),
      signal: AbortSignal.timeout(50_000),
    });
    if (!upstream.ok) {
      console.warn(`Auto-post image generation failed with HTTP ${upstream.status}`);
      return "";
    }
    const mime = String(upstream.headers.get("Content-Type") || "image/png").split(";")[0].toLowerCase();
    if (!mime.startsWith("image/")) return "";
    const bytes = await upstream.arrayBuffer();
    if (!bytes.byteLength || bytes.byteLength > 12 * 1024 * 1024) return "";
    const extension = mime.includes("jpeg") || mime.includes("jpg") ? "jpg" : mime.includes("webp") ? "webp" : "png";
    const objectPath = `auto-ai-posts/${new Date().toISOString().slice(0, 10)}/${crypto.randomUUID()}.${extension}`;
    const upload = await fetch(`${supabaseUrl}/storage/v1/object/${encodeURIComponent(bucket)}/${objectPath.split("/").map(encodeURIComponent).join("/")}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${serviceKey}`,
        apikey: serviceKey,
        "Content-Type": mime,
        "Cache-Control": "max-age=31536000, immutable",
      },
      body: bytes,
      signal: AbortSignal.timeout(25_000),
    });
    if (!upload.ok) {
      console.warn(`Auto-post image storage failed with HTTP ${upload.status}`);
      return "";
    }
    return `${supabaseUrl}/storage/v1/object/public/${encodeURIComponent(bucket)}/${objectPath.split("/").map(encodeURIComponent).join("/")}`;
  } catch (error) {
    console.warn(`Auto-post image generation/storage skipped: ${safeError(error)}`);
    return "";
  }
}

/** Admin-managed provider from Firestore `admin_secrets/ai` (OpenAI-compatible), or null. */
async function activeProvider(projectId: string, token: string): Promise<Json | null> {
  const cfg: Json = (await firestoreGet(projectId, "admin_secrets/ai", token).catch(() => null)) ?? {};
  const p = cfg.providers && cfg.activeProviderId ? cfg.providers[cfg.activeProviderId] : null;
  if (!p || p.enabled === false || String(p.apiKey || "").length < 8 || !/^https:\/\//i.test(String(p.baseUrl || ""))) return null;
  // The model chosen on the admin "AI providers" page is the single source of truth.
  const defaults: Record<string, string> = { mistral: "mistral-small-latest", openai: "gpt-4o-mini", gemini: "gemini-2.0-flash", groq: "llama-3.1-8b-instant", openrouter: "openrouter/auto", deepseek: "deepseek-chat", together: "meta-llama/Llama-3.3-70B-Instruct-Turbo" };
  return { name: String(p.name || cfg.activeProviderId), baseUrl: String(p.baseUrl).replace(/\/+$/, ""), apiKey: String(p.apiKey), model: String(p.model || defaults[String(cfg.activeProviderId)] || "") };
}

async function generatePost(config: Json, type: string, localTime: string, provider: Json | null = null, extra: { web?: { title: string; url: string; snippet: string }[]; part?: number; seriesTopic?: string; seriesSummary?: string } = {}): Promise<Json> {
  const apiKey = provider ? String(provider.apiKey) : env("MISTRAL_API_KEY");
  if (!apiKey) throw new Error("No AI provider: set one in the admin panel or the MISTRAL_API_KEY secret");
  const model = provider ? String(provider.model) : "mistral-small-latest";
  const endpoint = provider ? `${provider.baseUrl}/chat/completions` : "https://api.mistral.ai/v1/chat/completions";
  const language = String(config.language || "Bengali");
  const useEmoji = config.useEmoji !== false;
  const adminPrompt = String(config.prompt || "").slice(0, 1500);
  const withImage = config.withImage === true;
  const isStory = type === "story";
  const system = [
    "You write posts for the community feed of Convo Chat, a friendly messaging and social app. Sound like a real, warm person, not a bot.",
    `Write in ${language}. ${isStory ? "Write 120 to 220 words with vivid, simple language, and end with a small cliffhanger." : "Keep it under 80 words, natural, never spammy."}`,
    useEmoji ? "Use 2 to 4 fitting emoji naturally inside the text." : "Do not use emoji.",
    "Never invent news, prices, medical or legal claims, or statistics; for news use ONLY the web results given. No politics, religion debates, hate, adult content or personal data.",
    "Reply ONLY with a JSON object: {\"title\": string (max 6 words, may be empty), \"text\": string, \"tags\": [up to 3 short hashtags without #], \"feeling\": string (one word, may be empty)" +
      (type === "poll" ? ", \"options\": [2-4 short strings]" : "") +
      (withImage && type !== "poll" ? ", \"imagePrompt\": string (an English description of a beautiful photo-style picture for this post, no text or letters in the image)" : "") +
      (extra.web?.length ? ", \"link\": string (the URL of the result you used)" : "") +
      (isStory ? ", \"summary\": string (2 sentences: the whole story so far, in English)" : "") + "}.",
  ].join("\n");
  const user = [
    `Post type: ${TYPE_GUIDE[type] ?? TYPE_GUIDE.status}.`,
    `Local time for readers: ${localTime}.`,
    isStory ? `This is PART ${extra.part ?? 1} of a series about: ${extra.seriesTopic || "a heart-warming everyday adventure"}.` + (extra.seriesSummary ? ` Story so far: ${extra.seriesSummary}` : " Start the story.") : "",
    extra.web?.length ? "Fresh web results:\n" + extra.web.map((r, i) => `[${i + 1}] ${r.title} - ${r.snippet} (${r.url})`).join("\n") : "",
    adminPrompt ? `Admin guidance (follow it unless it breaks the rules above):\n${adminPrompt}` : "",
  ].filter(Boolean).join("\n");
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      temperature: 0.9,
      max_tokens: isStory ? 900 : 500,
      // json_object is Mistral/OpenAI specific; other providers get plain text and we extract JSON.
      ...(provider ? {} : { response_format: { type: "json_object" } }),
      messages: [{ role: "system", content: system }, { role: "user", content: user }],
    }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${provider ? provider.name : "Mistral"} request failed (${response.status})`);
  const content = String(body?.choices?.[0]?.message?.content ?? "{}");
  let parsed: Json;
  try {
    parsed = JSON.parse(content);
  } catch (_) {
    const match = content.match(/\{[\s\S]*\}/);
    try { parsed = match ? JSON.parse(match[0]) : { text: content }; } catch (_e) { parsed = { text: content }; }
  }
  const text = String(parsed.text ?? "").trim().slice(0, 1200);
  if (!text) throw new Error("AI returned an empty post");
  return {
    title: String(parsed.title ?? "").trim().slice(0, 80),
    text,
    tags: (Array.isArray(parsed.tags) ? parsed.tags : []).map((t: unknown) => String(t).replace(/^#/, "").trim()).filter(Boolean).slice(0, 3),
    feeling: String(parsed.feeling ?? "").trim().slice(0, 30),
    options: (Array.isArray(parsed.options) ? parsed.options : []).map((o: unknown) => String(o).trim().slice(0, 60)).filter(Boolean).slice(0, 4),
    imagePrompt: String(parsed.imagePrompt ?? "").trim().slice(0, 400),
    link: /^https?:\/\//.test(String(parsed.link ?? "")) ? String(parsed.link).slice(0, 500) : (extra.web?.[0]?.url ?? ""),
    summary: String(parsed.summary ?? "").trim().slice(0, 600),
  };
}

export async function runAutoPost(force = false): Promise<Json> {
  const account = readServiceAccount({ FIREBASE_SERVICE_ACCOUNT: env("FIREBASE_SERVICE_ACCOUNT") });
  if (!account?.project_id || !account?.private_key) throw new Error("FIREBASE_SERVICE_ACCOUNT secret is missing or invalid");
  const projectId = account.project_id;
  const token = await getGoogleAccessToken(account, SCOPE);
  const pollsClosed = await closeExpiredPolls(projectId, token).catch(() => 0);

  const config: Json = (await firestoreGet(projectId, "app_config/auto_post", token)) ?? {};
  const now = Date.now();
  const runNow = Number(config.runNowRequestedAt || 0) > Number(config.lastRunAt || 0);
  if (!config.enabled && !runNow && !force) return { posted: false, reason: "disabled", pollsClosed };
  const adminUid = String(config.adminUid || "");
  if (!adminUid) return { posted: false, reason: "adminUid missing — save the settings once from the admin app", pollsClosed };

  const perDay = clampInt(config.postsPerDay, 1, 48, 3);
  const tz = clampInt(config.tzOffsetMinutes, -720, 840, 360);
  const startHour = clampInt(config.startHour, 0, 23, 8);
  const endHour = clampInt(config.endHour, 0, 23, 23);
  const hour = localHour(now, tz);
  const due = now >= Number(config.nextRunAt || 0);
  if (!runNow && !force && (!due || !insideWindow(hour, startHour, endHour))) {
    return { posted: false, reason: due ? "outside posting hours" : "not due yet", nextRunAt: config.nextRunAt || 0, pollsClosed };
  }
  if (Number(config.runningUntil || 0) > now) return { posted: false, reason: "another run is in progress", pollsClosed };
  await firestorePatch(projectId, "app_config/auto_post", { runningUntil: now + 120_000 }, token);

  // Spread posts across the active window, with a little jitter so they look natural.
  const windowHours = startHour === endHour ? 24 : (endHour - startHour + 24) % 24;
  const interval = Math.max(15 * 60_000, Math.floor((windowHours * 3_600_000) / perDay));
  const jitter = Math.floor((Math.random() - 0.5) * interval * 0.2);
  const nextRunAt = now + interval + jitter;

  try {
    const admin: Json = (await firestoreGet(projectId, `users/${adminUid}`, token)) ?? {};
    const seriesOn = config.seriesEnabled === true && String(config.seriesTopic || "").trim().length > 0;
    let type = seriesOn ? "story" : pickType(String(config.postType || "mixed"));
    const maxParts = clampInt(config.seriesMaxParts, 2, 30, 5);
    let part = clampInt(config.seriesPart, 0, 1000, 0) + 1;
    let seriesSummary = String(config.seriesSummary || "");
    if (part > maxParts) { part = 1; seriesSummary = ""; }
    let web: { title: string; url: string; snippet: string }[] = [];
    if (!seriesOn && (type === "news" || (config.useWebSearch === true && type !== "poll"))) {
      web = await webResults(projectId, token, String(config.searchTopic || "") || (type === "news" ? "latest technology science news today" : `${TYPE_GUIDE[type]} ideas`)).catch(() => []);
      if (!web.length && type === "news") type = "fun_fact";
    }
    const localTime = `${String(hour).padStart(2, "0")}:${String(new Date(now + tz * 60_000).getUTCMinutes()).padStart(2, "0")}`;
    const provider = await activeProvider(projectId, token);
    const generated = await generatePost(config, type, localTime, provider, { web, part, seriesTopic: String(config.seriesTopic || ""), seriesSummary });
    const imageUrl = config.withImage === true && generated.imagePrompt ? await generateCachedPostImage(projectId, token, String(generated.imagePrompt)) : "";
    const postText = generated.link && web.length ? `${generated.text}\n\n${generated.link}` : generated.text;
    const postTitle = type === "story" ? `${String(config.seriesTopic || generated.title).slice(0, 50)} - Part ${part}` : generated.title;
    const id = crypto.randomUUID();
    let pollId = "";
    if (type === "poll" && generated.options.length >= 2) {
      pollId = crypto.randomUUID();
      await firestoreCreate(projectId, "polls", pollId, {
        question: generated.text.slice(0, 200),
        options: generated.options,
        counts: Object.fromEntries(generated.options.map((_: string, i: number) => [String(i), 0])),
        totalVotes: 0,
        createdAt: now,
        endsAt: now + clampInt(config.pollHours, 1, 168, 24) * 3_600_000,
        active: true,
        createdBy: adminUid,
        autoGenerated: true,
      }, token);
    } else {
      await firestoreCreate(projectId, "posts", id, {
        id,
        senderId: adminUid,
        senderName: String(admin.name || "Convo Chat"),
        senderProfilePic: String(admin.profileImageUrl || ""),
        text: postText,
        title: postTitle,
        tags: generated.tags,
        feeling: generated.feeling,
        imageUrl,
        audioUrl: "",
        videoUrl: "",
        imageUrls: imageUrl ? [imageUrl] : [],
        timestamp: now,
        reactions: {},
        comments: [],
        viewsCount: 0,
        private: false,
        isPrivate: false,
        reel: false,
        isReel: false,
        backgroundStyle: imageUrl || config.styledBackground === false || type === "story" || postText.length > 220 ? "glass" : BACKGROUNDS[Math.floor(Math.random() * BACKGROUNDS.length)],
        textAnimation: "none",
        postEverywhere: true,
        autoGenerated: true,
        autoPostType: type,
      }, token);
    }
    await firestorePatch(projectId, "app_config/auto_post", {
      lastRunAt: now,
      nextRunAt,
      lastPostId: pollId || id,
      lastPostType: type,
      lastPostPreview: generated.text.slice(0, 160),
      lastError: "",
      totalPosts: Number(config.totalPosts || 0) + 1,
      runningUntil: 0,
      ...(type === "story" ? { seriesPart: part, seriesSummary: generated.summary || seriesSummary } : {}),
    }, token);
    return { posted: true, type, postId: pollId || id, nextRunAt, pollsClosed };
  } catch (error) {
    await firestorePatch(projectId, "app_config/auto_post", {
      lastRunAt: now,
      lastError: safeError(error),
      nextRunAt: now + 30 * 60_000, // retry in 30 minutes
      runningUntil: 0,
    }, token).catch(() => {});
    throw error;
  }
}

Deno.serve(async (request: Request) => {
  const path = new URL(request.url).pathname.replace(/^.*\/auto-ai-post/, "") || "/";
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders() });
  if (path === "/health" || path === "/") {
    return jsonResponse({
      ok: true,
      service: "auto-ai-post",
      version: VERSION,
      firebaseConfigured: Boolean(env("FIREBASE_SERVICE_ACCOUNT")),
      mistralConfigured: Boolean(env("MISTRAL_API_KEY")),
      cronSecretConfigured: (env("AUTO_POST_CRON_SECRET") || env("PRESENCE_CRON_SECRET")).length >= 24,
    });
  }
  if (path === "/cron" && request.method === "POST") {
    if (!cronAuthorized(request)) return jsonResponse({ error: "Unauthorized" }, 401);
    const force = new URL(request.url).searchParams.get("force") === "1";
    const task = runAutoPost(force).then((result) => console.log("auto-ai-post", JSON.stringify(result)))
      .catch((error) => console.error("auto-ai-post failed:", safeError(error)));
    if (typeof EdgeRuntime !== "undefined" && typeof EdgeRuntime?.waitUntil === "function") {
      EdgeRuntime.waitUntil(task);
      return jsonResponse({ accepted: true }, 202);
    }
    await task;
    return jsonResponse({ accepted: true, finished: true });
  }
  return jsonResponse({ error: "Not found" }, 404);
});
