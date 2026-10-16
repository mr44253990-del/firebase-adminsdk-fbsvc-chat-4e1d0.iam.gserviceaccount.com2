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
import {
  clampInt,
  corsHeaders,
  firestoreGet,
  firestorePatch,
  getGoogleAccessToken,
  jsonResponse,
  readServiceAccount,
  safeError,
} from "../presence-monitor/presence.js";

declare const EdgeRuntime: { waitUntil?: (promise: Promise<unknown>) => void } | undefined;

const VERSION = "1.0.0";
const SCOPE = "https://www.googleapis.com/auth/datastore";
const DAY_MS = 24 * 60 * 60 * 1000;
const POST_TYPES = ["status", "tip", "quote", "question", "fun_fact", "motivation", "poll"] as const;
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
  const pool = POST_TYPES.filter((t) => t !== "poll");
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
};

/** Admin-managed provider from Firestore `admin_secrets/ai` (OpenAI-compatible), or null. */
async function activeProvider(projectId: string, token: string): Promise<Json | null> {
  const cfg: Json = (await firestoreGet(projectId, "admin_secrets/ai", token).catch(() => null)) ?? {};
  const p = cfg.providers && cfg.activeProviderId ? cfg.providers[cfg.activeProviderId] : null;
  if (!p || p.enabled === false || String(p.apiKey || "").length < 8 || !/^https:\/\//i.test(String(p.baseUrl || ""))) return null;
  return { name: String(p.name || cfg.activeProviderId), baseUrl: String(p.baseUrl).replace(/\/+$/, ""), apiKey: String(p.apiKey), model: String(p.model || "") };
}

async function generatePost(config: Json, type: string, localTime: string, provider: Json | null = null): Promise<Json> {
  const apiKey = provider ? String(provider.apiKey) : env("MISTRAL_API_KEY");
  if (!apiKey) throw new Error("No AI provider: set one in the admin panel or the MISTRAL_API_KEY secret");
  const model = provider ? String(provider.model || config.model || "") : String(config.model || "mistral-small-latest");
  const endpoint = provider ? `${provider.baseUrl}/chat/completions` : "https://api.mistral.ai/v1/chat/completions";
  const language = String(config.language || "Bengali");
  const useEmoji = config.useEmoji !== false;
  const adminPrompt = String(config.prompt || "").slice(0, 1500);
  const system = [
    "You write short posts for the community feed of Convo Chat, a friendly messaging and social app.",
    `Write in ${language}. Keep it under 70 words, warm and natural, never spammy.`,
    useEmoji ? "Use 2 to 4 fitting emoji naturally inside the text." : "Do not use emoji.",
    "Never invent news, prices, medical or legal claims, or statistics. No politics, religion debates, hate, adult content or personal data.",
    "Reply ONLY with a JSON object: {\"title\": string (max 6 words, may be empty), \"text\": string, \"tags\": [up to 3 short hashtags without #], \"feeling\": string (one word, may be empty)" +
      (type === "poll" ? ", \"options\": [2-4 short strings]" : "") + "}.",
  ].join("\n");
  const user = [
    `Post type: ${TYPE_GUIDE[type] ?? TYPE_GUIDE.status}.`,
    `Local time for readers: ${localTime}.`,
    adminPrompt ? `Admin guidance (follow it unless it breaks the rules above):\n${adminPrompt}` : "",
  ].filter(Boolean).join("\n");
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      temperature: 0.9,
      max_tokens: 400,
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
    const type = pickType(String(config.postType || "mixed"));
    const localTime = `${String(hour).padStart(2, "0")}:${String(new Date(now + tz * 60_000).getUTCMinutes()).padStart(2, "0")}`;
    const provider = await activeProvider(projectId, token);
    const generated = await generatePost(config, type, localTime, provider);
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
        text: generated.text,
        title: generated.title,
        tags: generated.tags,
        feeling: generated.feeling,
        imageUrl: "",
        audioUrl: "",
        videoUrl: "",
        imageUrls: [],
        timestamp: now,
        reactions: {},
        comments: [],
        viewsCount: 0,
        private: false,
        isPrivate: false,
        reel: false,
        isReel: false,
        backgroundStyle: config.styledBackground === false ? "glass" : BACKGROUNDS[Math.floor(Math.random() * BACKGROUNDS.length)],
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
