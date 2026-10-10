import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./firechat-presence-worker.js", import.meta.url), "utf8");
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`;
const { default: worker } = await import(moduleUrl);

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

console.log("Presence Worker smoke tests passed");
