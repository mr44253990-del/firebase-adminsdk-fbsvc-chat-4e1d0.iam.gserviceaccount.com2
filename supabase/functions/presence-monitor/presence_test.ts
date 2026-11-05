import { assertEquals } from "jsr:@std/assert@1";
import { handleRequest, isCronAuthorized, safeEqual } from "./handler.ts";
import { buildPresenceProbeMessage, classifyPresence, normalizePresenceRoute } from "./presence.js";

Deno.test("routes resolve under the Supabase function prefix", () => {
  assertEquals(normalizePresenceRoute("/presence-monitor/health"), "/health");
  assertEquals(normalizePresenceRoute("/functions/v1/presence-monitor/cron/"), "/cron");
  assertEquals(normalizePresenceRoute("/presence-monitor/admin/users"), "/admin/users");
});

Deno.test("probe is data-only, normal priority, 60s TTL", () => {
  const message = buildPresenceProbeMessage("token-1", "p1", 1000).message;
  assertEquals(message.data.notificationType, "presence_probe");
  assertEquals(message.android.priority, "NORMAL");
  assertEquals(message.android.ttl, "60s");
  assertEquals("notification" in message, false);
});

Deno.test("presence needs a device acknowledgement, not just a send", () => {
  const now = 10_000_000;
  assertEquals(classifyPresence({}, now).status, "offline");
  assertEquals(classifyPresence({ lastProbeSentAt: now - 1000 }, now).status, "offline");
  assertEquals(classifyPresence({ lastProbeReceivedAt: now - 60_000 }, now).status, "reachable");
  assertEquals(classifyPresence({ isOnline: true, foreground: true, onlineUntil: now + 1000 }, now).status, "online");
});

Deno.test("cron secret is required and compared safely", () => {
  const env = { PRESENCE_CRON_SECRET: "x".repeat(32) };
  const ok = new Request("https://a/presence-monitor/cron", { method: "POST", headers: { "x-cron-secret": "x".repeat(32) } });
  const bad = new Request("https://a/presence-monitor/cron", { method: "POST", headers: { "x-cron-secret": "y".repeat(32) } });
  assertEquals(isCronAuthorized(ok, env), true);
  assertEquals(isCronAuthorized(bad, env), false);
  assertEquals(isCronAuthorized(ok, { PRESENCE_CRON_SECRET: "short" }), false);
  assertEquals(safeEqual("abc", "abcd"), false);
});

Deno.test("unauthorized cron and admin calls are rejected", async () => {
  const env = { PRESENCE_CRON_SECRET: "z".repeat(32), FIREBASE_SERVICE_ACCOUNT: "{}" };
  const cron = await handleRequest(new Request("https://a/presence-monitor/cron", { method: "POST" }), env);
  assertEquals(cron.status, 401);
  const admin = await handleRequest(new Request("https://a/presence-monitor/admin/probe", { method: "POST" }), env);
  assertEquals(admin.status, 503);
  const health = await handleRequest(new Request("https://a/presence-monitor/health"), env);
  assertEquals(health.status, 503);
  assertEquals((await health.json()).platform, "supabase-edge-function");
});

Deno.test("cron run sends one probe per device and records state (mocked Google APIs)", async () => {
  const keys = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", keys.privateKey));
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...pkcs8))}\n-----END PRIVATE KEY-----\n`;
  const account = { project_id: "demo", client_email: "svc@demo.iam.gserviceaccount.com", private_key: pem };
  const fcmBodies: string[] = [];
  const writes: string[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = ((input: string | URL | Request, init?: RequestInit) => {
    const url = String(input instanceof Request ? input.url : input);
    const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status }));
    if (url.startsWith("https://oauth2.googleapis.com/token")) return json({ access_token: "t" });
    if (url.includes("/documents/users?")) {
      return json({ documents: [
        { name: "x/users/u1", fields: { fcmToken: { stringValue: "tokA" } } },
        { name: "x/users/u2", fields: { fcmToken: { stringValue: "tokA" } } },
        { name: "x/users/u3", fields: { fcmToken: { stringValue: "tokB" } } },
        { name: "x/users/u4", fields: {} },
      ] });
    }
    if (url.includes("/documents/presence?")) return json({ documents: [] });
    if (url.includes("presence_monitor/state") && (!init || !init.method || init.method === "GET")) return json({}, 404);
    if (url.includes("fcm.googleapis.com")) { fcmBodies.push(String(init?.body)); return json({ name: "ok" }); }
    writes.push(url);
    return json({});
  }) as typeof fetch;
  try {
    const { runPresenceProbe } = await import("./presence.js");
    const result = await runPresenceProbe({ FIREBASE_SERVICE_ACCOUNT: JSON.stringify(account) }, { trigger: "cron" });
    assertEquals(result.devicesTargeted, 2);
    assertEquals(result.acceptedByFcm, 2);
    assertEquals(result.usersWithoutToken, 1);
    assertEquals(fcmBodies.length, 2);
    assertEquals(fcmBodies.every((b) => !b.includes('"notification"')), true);
    assertEquals(writes.some((u) => u.includes("presence_monitor/state")), true);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
