import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./cloudflare-worker.js", import.meta.url), "utf8");
const module = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const worker = module.default;
const origin = "https://convo-chat-companion.pages.dev";
const env = { WEB_COMPANION_ORIGIN: origin };
const ctx = { waitUntil() {} };

const deniedOrigin = await worker.fetch(new Request("https://worker.test/web-pair/redeem", {
  method: "POST",
  headers: { Origin: "https://attacker.example", "Content-Type": "application/json" },
  body: JSON.stringify({ code: "1234567890" }),
}), env, ctx);
assert.equal(deniedOrigin.status, 403, "unapproved browser origin must be rejected");
assert.equal(deniedOrigin.headers.get("Access-Control-Allow-Origin"), null, "unapproved origin must not receive CORS permission");

const invalidCode = await worker.fetch(new Request("https://worker.test/web-pair/redeem", {
  method: "POST",
  headers: { Origin: origin, "Content-Type": "application/json" },
  body: JSON.stringify({ code: "not-a-code" }),
}), env, ctx);
assert.equal(invalidCode.status, 400, "malformed codes must be rejected before any database access");
assert.equal(invalidCode.headers.get("Access-Control-Allow-Origin"), origin, "configured Pages origin receives exact CORS permission");

const noSecret = await worker.fetch(new Request("https://worker.test/web-pair/create", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ optIn: true }),
}), {}, ctx);
assert.equal(noSecret.status, 503, "pairing must fail closed if the Firebase service account is absent");

const preflight = await worker.fetch(new Request("https://worker.test/web-pair/redeem", {
  method: "OPTIONS",
  headers: { Origin: origin, "Access-Control-Request-Method": "POST" },
}), env, ctx);
assert.equal(preflight.status, 200, "CORS preflight should be accepted");

console.log("Web companion Worker smoke checks passed (CORS origin allowlist, malformed code, fail-closed secret check, preflight).");
