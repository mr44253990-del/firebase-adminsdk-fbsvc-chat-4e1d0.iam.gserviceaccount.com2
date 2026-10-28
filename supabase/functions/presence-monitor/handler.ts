/**
 * Convo Chat Presence Monitor — Supabase Edge Function.
 *
 * Routes (base: https://<project-ref>.supabase.co/functions/v1/presence-monitor):
 *   GET  /health              public, shows whether secrets are configured
 *   POST /cron                Supabase Cron only (x-cron-secret header), every 5 minutes
 *   POST /presence/heartbeat  signed-in app user (Firebase ID token)
 *   GET  /admin/users         admin dashboard (Firebase ID token of the admin)
 *   POST /admin/probe         admin "Run probe now"
 *
 * Deploy with JWT verification off (the app sends Firebase tokens, not Supabase tokens;
 * every route checks its own credential):
 *   supabase functions deploy presence-monitor --no-verify-jwt
 */
import {
  authenticateAdmin,
  authenticateUser,
  clampInt,
  classifyPresence,
  corsHeaders,
  firestoreGet,
  firestoreListAll,
  firestorePatch,
  getGoogleAccessToken,
  jsonResponse,
  normalizePresenceRoute,
  ONLINE_LEASE_MS,
  readServiceAccount,
  runPresenceProbe,
  safeError,
  VERSION,
  asMillis,
} from "./presence.js";

declare const EdgeRuntime: { waitUntil?: (promise: Promise<unknown>) => void } | undefined;

function hasBackgroundTasks(): boolean {
  return typeof EdgeRuntime !== "undefined" && typeof EdgeRuntime?.waitUntil === "function";
}

const AUTH_SCOPE = "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging";

// deno-lint-ignore no-explicit-any
type Env = Record<string, any>;

function readEnv(): Env {
  return {
    FIREBASE_SERVICE_ACCOUNT: Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "",
    PRESENCE_CRON_SECRET: Deno.env.get("PRESENCE_CRON_SECRET") ?? "",
    ADMIN_EMAIL: Deno.env.get("ADMIN_EMAIL") ?? "",
  };
}

/** Constant-time comparison so the cron secret cannot be guessed by timing. */
export function safeEqual(a: string, b: string): boolean {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  return diff === 0;
}

export function isCronAuthorized(request: Request, env: Env): boolean {
  const expected = String(env.PRESENCE_CRON_SECRET || "");
  if (expected.length < 24) return false; // refuse weak or missing secrets
  const provided = request.headers.get("x-cron-secret") ?? "";
  return safeEqual(provided, expected);
}

function runInBackground(task: Promise<unknown>) {
  const safeTask = task.catch((error) => console.error("presence probe failed:", safeError(error)));
  if (hasBackgroundTasks()) EdgeRuntime?.waitUntil?.(safeTask);
}

export async function handleRequest(request: Request, env: Env = readEnv()): Promise<Response> {
  const url = new URL(request.url);
  const path = normalizePresenceRoute(url.pathname);

  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders() });

  if (request.method === "GET" && path === "/health") {
    const account = readServiceAccount(env);
    const configured = Boolean(account?.project_id && account?.client_email && account?.private_key);
    const cronSecretConfigured = String(env.PRESENCE_CRON_SECRET || "").length >= 24;
    return jsonResponse({
      ok: configured,
      service: "Convo Chat Presence Monitor",
      platform: "supabase-edge-function",
      version: VERSION,
      deliveryMode: "per-device-token",
      probeIntervalMinutes: 5,
      scheduler: "supabase-cron (pg_cron + pg_net)",
      cronSecretConfigured,
      userCollection: "users",
      timestamp: Date.now(),
    }, configured ? 200 : 503);
  }

  if (request.method === "POST" && path === "/cron") {
    if (!isCronAuthorized(request, env)) return jsonResponse({ error: "Unauthorized scheduler" }, 401);
    const probe = runPresenceProbe(env, { trigger: "cron" });
    if (hasBackgroundTasks()) {
      // pg_net only waits a few seconds; answer now and finish the probe in the background.
      runInBackground(probe);
      return jsonResponse({ ok: true, accepted: true, mode: "background" }, 202);
    }
    try {
      return jsonResponse({ ok: true, ...(await probe) });
    } catch (error) {
      return jsonResponse({ ok: false, error: safeError(error) }, 502);
    }
  }

  if (request.method === "POST" && path === "/presence/heartbeat") {
    // deno-lint-ignore no-explicit-any
    const auth: any = await authenticateUser(request, env);
    if (!auth.ok) return auth.response;
    try {
      const body = await request.json().catch(() => ({}));
      const now = Date.now();
      const active = body.active !== false;
      const foreground = active && body.foreground !== false;
      const state = {
        uid: auth.caller.sub,
        isOnline: active,
        foreground,
        lastActive: now,
        onlineUntil: active ? now + ONLINE_LEASE_MS : 0,
        offlineSince: active ? 0 : now,
        source: "presence-worker-heartbeat",
      };
      await firestorePatch(auth.account.project_id, `presence/${auth.caller.sub}`, state, auth.token);
      return jsonResponse({ ok: true, status: classifyPresence(state, now).status, onlineUntil: state.onlineUntil });
    } catch (error) {
      return jsonResponse({ ok: false, error: safeError(error) }, 500);
    }
  }

  if (request.method === "GET" && path === "/admin/users") {
    // deno-lint-ignore no-explicit-any
    const auth: any = await authenticateAdmin(request, env);
    if (!auth.ok) return auth.response;
    try {
      const limit = clampInt(url.searchParams.get("limit"), 1, 100, 50);
      const cursor = clampInt(url.searchParams.get("cursor"), 0, 1_000_000_000, 0);
      const query = String(url.searchParams.get("q") || "").trim().toLowerCase().slice(0, 100);
      const token = await getGoogleAccessToken(auth.account, AUTH_SCOPE);
      const [profiles, presences, probeMeta] = await Promise.all([
        firestoreListAll(auth.account.project_id, "users", token),
        firestoreListAll(auth.account.project_id, "presence", token),
        firestoreGet(auth.account.project_id, "presence_monitor/state", token),
      ]);
      // deno-lint-ignore no-explicit-any
      const presenceByUid = new Map(presences.map((item: any) => [item.__id, item]));
      const now = Date.now();
      // deno-lint-ignore no-explicit-any
      const allUsers = profiles.map((profile: any) => {
        const uid = String(profile.uid || profile.__id || "");
        const state = classifyPresence(presenceByUid.get(uid) || {}, now);
        return {
          uid,
          name: String(profile.name || profile.username || "Convo Chat user").slice(0, 120),
          username: String(profile.username || "").slice(0, 80),
          status: state.status,
          isActive: state.isActive,
          foreground: state.foreground,
          lastActive: state.lastActive,
          lastProbeReceivedAt: state.lastProbeReceivedAt,
          hasFcmToken: typeof profile.fcmToken === "string" && profile.fcmToken.length > 0,
        };
      // deno-lint-ignore no-explicit-any
      }).sort((a: any, b: any) => Number(b.isActive) - Number(a.isActive) || b.lastActive - a.lastActive || a.name.localeCompare(b.name));
      // deno-lint-ignore no-explicit-any
      const filtered = allUsers.filter((user: any) =>
        !query || user.name.toLowerCase().includes(query) || user.username.toLowerCase().includes(query) || user.uid.toLowerCase().includes(query)
      );
      return jsonResponse({
        ok: true,
        version: VERSION,
        platform: "supabase-edge-function",
        totalUsers: profiles.length,
        // deno-lint-ignore no-explicit-any
        activeUsers: allUsers.filter((u: any) => u.isActive).length,
        // deno-lint-ignore no-explicit-any
        foregroundUsers: allUsers.filter((u: any) => u.foreground).length,
        // deno-lint-ignore no-explicit-any
        reachableUsers: allUsers.filter((u: any) => u.status === "reachable").length,
        // deno-lint-ignore no-explicit-any
        registeredDevices: profiles.filter((p: any) => typeof p.fcmToken === "string" && p.fcmToken.length > 0).length,
        lastProbeAt: asMillis(probeMeta?.lastProbeAt),
        lastProbeStatus: String(probeMeta?.lastProbeStatus || ""),
        lastProbeTrigger: String(probeMeta?.lastProbeTrigger || ""),
        generatedAt: now,
        query,
        users: filtered.slice(cursor, cursor + limit),
        nextCursor: cursor + limit < filtered.length ? String(cursor + limit) : null,
      });
    } catch (error) {
      return jsonResponse({ ok: false, error: safeError(error) }, 500);
    }
  }

  if (request.method === "POST" && path === "/admin/probe") {
    // deno-lint-ignore no-explicit-any
    const auth: any = await authenticateAdmin(request, env);
    if (!auth.ok) return auth.response;
    try {
      return jsonResponse({ ok: true, ...(await runPresenceProbe(env, { trigger: "admin", force: true })) });
    } catch (error) {
      return jsonResponse({ ok: false, error: safeError(error) }, 502);
    }
  }

  return jsonResponse({ error: "Not found" }, 404);
}
