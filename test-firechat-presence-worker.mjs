import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./firechat-presence-worker.js", import.meta.url), "utf8");
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`;
const { default: worker, buildPresenceProbeMessage, classifyPresence, normalizePresenceRoute } = await import(moduleUrl);

const configuredEnv = {
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify({
    project_id: "smoke-test-project",
    client_email: "presence-worker@example.invalid",
    private_key: "not-used-by-health-smoke-test"
  })
};

let response = await worker.fetch(new Request("https://worker.test/health"), configuredEnv);
assert.equal(response.status, 200);
const health = await response.json();
assert.equal(health.ok, true);
assert.equal(health.probeIntervalMinutes, 5);
assert.equal(health.service, "Convo Chat Presence Monitor");

response = await worker.fetch(new Request("https://worker.test/health"), {});
assert.equal(response.status, 503);

response = await worker.fetch(new Request("https://worker.test/admin/users"), configuredEnv);
assert.equal(response.status, 401);

response = await worker.fetch(new Request("https://worker.test/not-found"), configuredEnv);
assert.equal(response.status, 404);

response = await worker.fetch(new Request("https://worker.test/health", { method: "OPTIONS" }), configuredEnv);
assert.equal(response.status, 200);
assert.equal(response.headers.get("Access-Control-Allow-Origin"), "*");

const now = 1_800_000_000_000;
assert.equal(normalizePresenceRoute("/api/presence/heartbeat"), "/presence/heartbeat");
assert.equal(classifyPresence({ isOnline: true, foreground: true, onlineUntil: now + 60_000 }, now).status, "online");
assert.equal(classifyPresence({ isOnline: true, foreground: false, onlineUntil: now + 60_000 }, now).status, "reachable");
assert.equal(classifyPresence({ isOnline: false, source: "presence-worker-heartbeat", offlineSince: now, lastActive: now }, now).status, "offline");
assert.equal(classifyPresence({ isOnline: false, source: "presence-worker-heartbeat", offlineSince: now - 60_000, lastProbeReceivedAt: now }, now).status, "reachable");
assert.equal(classifyPresence({ isOnline: true, foreground: false, onlineUntil: now - 1, lastActive: now - 20 * 60_000 }, now).status, "offline");

const message = buildPresenceProbeMessage("test-device-token", "probe-1", now).message;
assert.equal(message.token, "test-device-token");
assert.equal(message.data.notificationType, "presence_probe");
assert.equal(message.android.priority, "NORMAL");
assert.equal(message.android.ttl, "60s");
assert.equal(message.notification, undefined, "presence probes are data-only and silent");
assert.equal(message.topic, undefined, "presence probes target individual device tokens, not topics");

console.log("Presence Worker smoke tests passed");
