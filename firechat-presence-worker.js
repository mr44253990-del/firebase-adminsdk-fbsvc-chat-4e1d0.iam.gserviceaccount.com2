/**
 * Convo Chat Presence Worker — Cloudflare-only presence scheduling and admin API.
 *
 * Configure FIREBASE_SERVICE_ACCOUNT as an encrypted Cloudflare Worker secret and
 * deploy a Cron trigger that runs every five minutes. Each run reads
 * users/{uid}.fcmToken and
 * sends a silent, normal-priority data-only FCM probe to each distinct device.
 * Devices acknowledge delivery in presence/{uid}; this Worker reconciles the
 * persisted online/reachable/offline status from that acknowledgement and lease.
 * Raw FCM tokens are never returned by an API response or written to probe logs.
 */

const VERSION = "2.0.0";
const ADMIN_EMAIL = "mr4425390@gmail.com";
const AUTH_SCOPE = "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging";
const ACTIVE_GRACE_MS = 12 * 60 * 1000;
const ONLINE_LEASE_MS = 5 * 60 * 1000;
const FCM_SEND_CONCURRENCY = 8;

export function normalizePresenceRoute(pathname) {
  const normalized = String(pathname || "/").replace(/\/+$/, "") || "/";
  const routes = ["/health", "/presence/heartbeat", "/admin/users", "/admin/probe"];
  return routes.find(route => normalized === route || normalized.endsWith(route)) || normalized;
}

export function classifyPresence(presence = {}, now = Date.now()) {
  const lastActive = asMillis(presence.lastActive);
  const lastPushReceivedAt = asMillis(presence.lastPushReceivedAt);
  const lastProbeReceivedAt = asMillis(presence.lastProbeReceivedAt);
  const lastSignalAt = Math.max(lastActive, lastPushReceivedAt, lastProbeReceivedAt);
  const onlineUntil = asMillis(presence.onlineUntil);
  const leaseActive = presence.isOnline === true && onlineUntil > now;
  const explicitlyOffline = presence.source === "presence-worker-heartbeat" && presence.isOnline === false;
  const acknowledgementAt = Math.max(lastPushReceivedAt, lastProbeReceivedAt);
  const offlineSince = asMillis(presence.offlineSince);
  const recentSignal = explicitlyOffline
    ? acknowledgementAt > offlineSince && now - acknowledgementAt <= ACTIVE_GRACE_MS
    : lastSignalAt > 0 && now - lastSignalAt <= ACTIVE_GRACE_MS;
  const foreground = leaseActive && presence.foreground === true;
  const reachable = leaseActive || recentSignal;
  return {
    status: foreground ? "online" : reachable ? "reachable" : "offline",
    isActive: reachable,
    foreground,
    lastActive: lastSignalAt,
    lastProbeReceivedAt,
    onlineUntil
  };
}

export function buildPresenceProbeMessage(token, probeId, sentAt) {
  return {
    message: {
      token,
      data: {
        notificationType: "presence_probe",
        probeId: String(probeId),
        sentAt: String(sentAt)
      },
      android: { priority: "NORMAL", ttl: "60s" }
    }
  };
}

let jwksCache = null;
let jwksExpiresAt = 0;

export default {
  async scheduled(_controller, env, ctx) {
    ctx.waitUntil(runPresenceProbe(env));
  },

  async fetch(request, env) {
    const url = new URL(request.url);
    const path = normalizePresenceRoute(url.pathname);

    if (request.method === "OPTIONS") {
      return new Response("OK", {
        headers: corsHeaders({
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization"
        })
      });
    }

    if (request.method === "GET" && path === "/health") {
      const account = readServiceAccount(env);
      const configured = Boolean(account?.project_id && account?.client_email && account?.private_key);
      return jsonResponse({
        ok: configured,
        service: "Convo Chat Presence Monitor",
        version: VERSION,
        deliveryMode: "per-device-token",
        probeIntervalMinutes: 5,
        userCollection: "users",
        timestamp: Date.now()
      }, configured ? 200 : 503);
    }

    if (request.method === "POST" && path === "/presence/heartbeat") {
      const auth = await authenticateUser(request, env);
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
          source: "presence-worker-heartbeat"
        };
        await firestorePatch(auth.account.project_id, `presence/${auth.caller.sub}`, state, auth.token);
        return jsonResponse({ ok: true, status: classifyPresence(state, now).status, onlineUntil: state.onlineUntil });
      } catch (error) {
        return jsonResponse({ ok: false, error: safeError(error) }, 500);
      }
    }

    if (request.method === "GET" && path === "/admin/users") {
      const auth = await authenticateAdmin(request, env);
      if (!auth.ok) return auth.response;
      try {
        const limit = clampInt(url.searchParams.get("limit"), 1, 100, 50);
        const cursor = clampInt(url.searchParams.get("cursor"), 0, 1_000_000_000, 0);
        const query = String(url.searchParams.get("q") || "").trim().toLowerCase().slice(0, 100);
        const token = await getGoogleAccessToken(auth.account, AUTH_SCOPE);
        const [profiles, presences, probeMeta] = await Promise.all([
          firestoreListAll(auth.account.project_id, "users", token),
          firestoreListAll(auth.account.project_id, "presence", token),
          firestoreGet(auth.account.project_id, "presence_monitor/state", token)
        ]);

        const presenceByUid = new Map(presences.map(item => [item.__id, item]));
        const now = Date.now();
        const allUsers = profiles.map(profile => {
          const uid = String(profile.uid || profile.__id || "");
          const presence = presenceByUid.get(uid) || {};
          const state = classifyPresence(presence, now);
          return {
            uid,
            name: String(profile.name || profile.username || "Convo Chat user").slice(0, 120),
            username: String(profile.username || "").slice(0, 80),
            status: state.status,
            isActive: state.isActive,
            foreground: state.foreground,
            lastActive: state.lastActive,
            lastProbeReceivedAt: state.lastProbeReceivedAt,
            hasFcmToken: typeof profile.fcmToken === "string" && profile.fcmToken.length > 0
          };
        }).sort((a, b) => Number(b.isActive) - Number(a.isActive) || b.lastActive - a.lastActive || a.name.localeCompare(b.name));
        const filteredUsers = allUsers.filter(user => {
          if (!query) return true;
          return user.name.toLowerCase().includes(query) || user.username.toLowerCase().includes(query) || user.uid.toLowerCase().includes(query);
        });

        const users = filteredUsers.slice(cursor, cursor + limit);
        const lastProbeAt = asMillis(probeMeta?.lastProbeAt);
        return jsonResponse({
          ok: true,
          version: VERSION,
          totalUsers: profiles.length,
          activeUsers: allUsers.filter(user => user.isActive).length,
          foregroundUsers: allUsers.filter(user => user.foreground).length,
          reachableUsers: allUsers.filter(user => user.status === "reachable").length,
          registeredDevices: profiles.filter(profile => typeof profile.fcmToken === "string" && profile.fcmToken.length > 0).length,
          lastProbeAt,
          generatedAt: now,
          query,
          users,
          nextCursor: cursor + limit < filteredUsers.length ? String(cursor + limit) : null
        });
      } catch (error) {
        return jsonResponse({ ok: false, error: safeError(error) }, 500);
      }
    }

    if (request.method === "POST" && path === "/admin/probe") {
      const auth = await authenticateAdmin(request, env);
      if (!auth.ok) return auth.response;
      try {
        const result = await runPresenceProbe(env);
        return jsonResponse({ ok: true, ...result });
      } catch (error) {
        return jsonResponse({ ok: false, error: safeError(error) }, 502);
      }
    }

    return jsonResponse({ error: "Not found" }, 404);
  }
};

async function runPresenceProbe(env) {
  const account = readServiceAccount(env);
  if (!account?.project_id || !account?.client_email || !account?.private_key) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT is not configured");
  }
  const token = await getGoogleAccessToken(account, AUTH_SCOPE);
  const now = Date.now();
  const probeId = crypto.randomUUID();
  const [profiles, presences] = await Promise.all([
    firestoreListAll(account.project_id, "users", token),
    firestoreListAll(account.project_id, "presence", token)
  ]);
  const presenceByUid = new Map(presences.map(item => [item.__id, item]));
  const targetsByToken = new Map();
  let noTokenUsers = 0;
  for (const profile of profiles) {
    const uid = String(profile.uid || profile.__id || "");
    const fcmToken = typeof profile.fcmToken === "string" ? profile.fcmToken.trim() : "";
    if (!uid || !fcmToken) { noTokenUsers++; continue; }
    const existing = targetsByToken.get(fcmToken);
    if (existing) existing.uids.push(uid);
    else targetsByToken.set(fcmToken, { token: fcmToken, uids: [uid] });
  }
  const targets = Array.from(targetsByToken.values());
  const endpoint = `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`;
  const results = await mapWithConcurrency(targets, FCM_SEND_CONCURRENCY, async target => {
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(buildPresenceProbeMessage(target.token, probeId, now))
      });
      const body = await response.json().catch(() => ({}));
      const detailCodes = Array.isArray(body?.error?.details)
        ? body.error.details.map(detail => String(detail?.errorCode || "").toUpperCase()).filter(Boolean)
        : [];
      const fcmCode = String(body?.error?.status || response.status).toUpperCase();
      const invalidToken = [fcmCode, ...detailCodes].some(code => code === "UNREGISTERED" || code === "INVALID_ARGUMENT");
      return {
        uids: target.uids,
        accepted: response.ok,
        invalidToken,
        errorCode: response.ok ? "" : fcmCode
      };
    } catch (_) {
      return { uids: target.uids, accepted: false, invalidToken: false, errorCode: "NETWORK_ERROR" };
    }
  });

  const acceptedUids = new Set();
  let accepted = 0;
  let failed = 0;
  let invalidTokens = 0;
  for (const result of results) {
    if (result.accepted) {
      accepted++;
      result.uids.forEach(uid => acceptedUids.add(uid));
    } else {
      failed++;
      if (result.invalidToken) invalidTokens++;
    }
  }

  const statusCounts = { online: 0, reachable: 0, offline: 0 };
  const updates = [];
  for (const profile of profiles) {
    const uid = String(profile.uid || profile.__id || "");
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(uid)) continue;
    const presence = presenceByUid.get(uid) || {};
    const state = classifyPresence(presence, now);
    statusCounts[state.status]++;
    const fields = {};
    if (presence.presenceStatus !== state.status) {
      fields.presenceStatus = state.status;
      fields.statusUpdatedAt = now;
    }
    if (acceptedUids.has(uid)) {
      fields.lastProbeSentAt = now;
      fields.lastProbeSentId = probeId;
    }
    if (Object.keys(fields).length) updates.push({ uid, fields });
  }
  if (updates.length) await firestoreBatchPatch(account.project_id, updates, token);

  await firestoreSet(account.project_id, "presence_monitor/state", {
    lastProbeAt: now,
    lastProbeId: probeId,
    lastProbeMode: "per-device-token",
    lastProbeStatus: targets.length === 0 ? "no-targets" : failed > 0 ? (accepted > 0 ? "partial" : "failed") : "sent",
    totalProfiles: profiles.length,
    devicesTargeted: targets.length,
    devicesAcceptedByFcm: accepted,
    devicesFailed: failed,
    invalidTokens,
    usersWithoutToken: noTokenUsers,
    statusCounts
  }, token);
  return {
    probeId,
    sentAt: now,
    mode: "per-device-token",
    totalProfiles: profiles.length,
    devicesTargeted: targets.length,
    acceptedByFcm: accepted,
    failed,
    invalidTokens,
    usersWithoutToken: noTokenUsers,
    statusCounts
  };
}

async function mapWithConcurrency(items, concurrency, mapper) {
  const results = new Array(items.length);
  let nextIndex = 0;
  const count = Math.min(Math.max(1, concurrency), items.length);
  await Promise.all(Array.from({ length: count }, async () => {
    while (true) {
      const index = nextIndex++;
      if (index >= items.length) return;
      results[index] = await mapper(items[index], index);
    }
  }));
  return results;
}

async function authenticateUser(request, env) {
  const account = readServiceAccount(env);
  if (!account?.project_id) {
    return { ok: false, response: jsonResponse({ error: "FIREBASE_SERVICE_ACCOUNT is not configured" }, 503) };
  }
  const caller = await verifyFirebaseIdToken(request, account.project_id);
  if (!caller) return { ok: false, response: jsonResponse({ error: "Unauthorized Firebase caller" }, 401) };
  const token = await getGoogleAccessToken(account, AUTH_SCOPE);
  return { ok: true, caller, account, token };
}

async function authenticateAdmin(request, env) {
  const auth = await authenticateUser(request, env);
  if (!auth.ok) return auth;
  const email = String(auth.caller.email || "").trim().toLowerCase().replace(/\.+$/, "");
  const configuredAdmin = String(env.ADMIN_EMAIL || ADMIN_EMAIL).trim().toLowerCase().replace(/\.+$/, "");
  if (email !== configuredAdmin) return { ok: false, response: jsonResponse({ error: "Admin only" }, 403) };
  return auth;
}

async function verifyFirebaseIdToken(request, projectId) {
  try {
    const authorization = request.headers.get("Authorization") || "";
    if (!authorization.startsWith("Bearer ")) return null;
    const parts = authorization.slice(7).split(".");
    if (parts.length !== 3) return null;
    const header = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
    const now = Math.floor(Date.now() / 1000);
    if (header.alg !== "RS256" || !header.kid || payload.aud !== projectId ||
        payload.iss !== `https://securetoken.google.com/${projectId}` ||
        Number(payload.exp) <= now || Number(payload.iat) > now + 60 || !payload.sub) return null;

    const jwks = await getFirebaseJwks();
    const key = jwks.keys?.find(item => item.kid === header.kid);
    if (!key) return null;
    const cryptoKey = await crypto.subtle.importKey(
      "jwk", key, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
    );
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5", cryptoKey, base64UrlToBytes(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    return valid ? payload : null;
  } catch (_) {
    return null;
  }
}

async function getFirebaseJwks() {
  if (jwksCache && Date.now() < jwksExpiresAt) return jwksCache;
  const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com", {
    cf: { cacheTtl: 3600, cacheEverything: true }
  });
  if (!response.ok) throw new Error("Firebase signing keys are unavailable");
  jwksCache = await response.json();
  jwksExpiresAt = Date.now() + 60 * 60 * 1000;
  return jwksCache;
}

async function getGoogleAccessToken(account, scope) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: account.client_email,
    scope,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  };
  const input = `${base64UrlEncode(header)}.${base64UrlEncode(claim)}`;
  const signature = await signRs256(input, account.private_key);
  const assertion = `${input}.${signature}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.access_token) throw new Error("Could not obtain a Firebase service access token");
  return result.access_token;
}

async function signRs256(value, pem) {
  const base64 = String(pem || "")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  if (!base64) throw new Error("Firebase private key is missing");
  const binary = atob(base64);
  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", bytes.buffer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(signature));
}

async function firestoreListAll(projectId, collection, token) {
  const result = [];
  let pageToken = "";
  do {
    const query = new URLSearchParams({ pageSize: "1000" });
    if (pageToken) query.set("pageToken", pageToken);
    const response = await fetch(
      `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${collection}?${query}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!response.ok) throw new Error(`Firestore list failed (${response.status})`);
    const body = await response.json();
    for (const document of body.documents || []) {
      const fields = Object.fromEntries(Object.entries(document.fields || {}).map(([key, value]) => [key, fromFirestoreValue(value)]));
      const id = String(document.name || "").split("/").pop();
      result.push({ ...fields, __id: id });
    }
    pageToken = String(body.nextPageToken || "");
  } while (pageToken);
  return result;
}

async function firestoreGet(projectId, documentPath, token) {
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${documentPath}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore read failed (${response.status})`);
  const document = await response.json();
  return Object.fromEntries(Object.entries(document.fields || {}).map(([key, value]) => [key, fromFirestoreValue(value)]));
}

async function firestorePatch(projectId, documentPath, data, token) {
  const fields = Object.fromEntries(Object.entries(data).map(([key, value]) => [key, toFirestoreValue(value)]));
  const url = new URL(`https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${documentPath}`);
  Object.keys(data).forEach(field => url.searchParams.append("updateMask.fieldPaths", field));
  const response = await fetch(url.toString(), {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields })
  });
  if (!response.ok) throw new Error(`Firestore merge failed (${response.status})`);
  return response.json().catch(() => ({}));
}

async function firestoreBatchPatch(projectId, updates, token) {
  const endpoint = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents:batchWrite`;
  for (let offset = 0; offset < updates.length; offset += 400) {
    const chunk = updates.slice(offset, offset + 400);
    const writes = chunk.map(({ uid, fields }) => ({
      update: {
        name: `projects/${projectId}/databases/(default)/documents/presence/${encodeURIComponent(uid)}`,
        fields: Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, toFirestoreValue(value)]))
      },
      updateMask: { fieldPaths: Object.keys(fields) }
    }));
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ writes })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || (result.status || []).some(status => Number(status.code || 0) !== 0)) {
      throw new Error(`Firestore presence reconciliation failed (${response.status})`);
    }
  }
}

async function firestoreSet(projectId, documentPath, data, token) {
  const fields = Object.fromEntries(Object.entries(data).map(([key, value]) => [key, toFirestoreValue(value)]));
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${documentPath}`,
    {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ fields })
    }
  );
  if (!response.ok) throw new Error(`Firestore write failed (${response.status})`);
}

function fromFirestoreValue(value) {
  if (!value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("timestampValue" in value) return Date.parse(value.timestampValue);
  if ("nullValue" in value) return null;
  if (value.arrayValue) return (value.arrayValue.values || []).map(fromFirestoreValue);
  if (value.mapValue) return Object.fromEntries(Object.entries(value.mapValue.fields || {}).map(([key, item]) => [key, fromFirestoreValue(item)]));
  return null;
}

function toFirestoreValue(value) {
  if (value == null) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } };
  if (typeof value === "object") return { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([key, item]) => [key, toFirestoreValue(item)])) } };
  return { stringValue: String(value) };
}

function readServiceAccount(env) {
  try {
    return JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
  } catch (_) {
    return null;
  }
}

function asMillis(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) return numeric;
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function clampInt(value, minimum, maximum, fallback) {
  const number = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(number) ? Math.min(maximum, Math.max(minimum, number)) : fallback;
}

function base64UrlEncode(value) {
  const bytes = value instanceof Uint8Array ? value : new TextEncoder().encode(JSON.stringify(value));
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + 0x8000, bytes.length)));
  }
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64UrlToBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

function corsHeaders(extra = {}) {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Cache-Control": "no-store",
    ...extra
  };
}

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: corsHeaders({ "Content-Type": "application/json; charset=utf-8" })
  });
}

function safeError(error) {
  return String(error?.message || "Request failed").slice(0, 240);
}
