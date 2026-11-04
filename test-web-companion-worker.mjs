import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./cloudflare-worker.js", import.meta.url), "utf8");
const deployedWorkerCopy = await readFile(new URL("./firechat-fcm-worker.js", import.meta.url), "utf8");
const appSource = await readFile(new URL("./web-companion/app.js", import.meta.url), "utf8");
const pagesApp = await readFile(new URL("./web-companion/web/app.js", import.meta.url), "utf8");
const htmlSource = await readFile(new URL("./web-companion/index.html", import.meta.url), "utf8");
const pagesHtml = await readFile(new URL("./web-companion/web/index.html", import.meta.url), "utf8");
const stylesSource = await readFile(new URL("./web-companion/styles.css", import.meta.url), "utf8");
const pagesStyles = await readFile(new URL("./web-companion/web/styles.css", import.meta.url), "utf8");
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

for (const [label, workerSource] of [["cloudflare-worker.js", source], ["firechat-fcm-worker.js", deployedWorkerCopy]]) {
  assert.match(workerSource, /Browser sessions intentionally persist until explicit logout or Android revoke-all/, `${label} must document persistent sessions`);
  const authBody = workerSource.match(/async function authenticateWebCompanionSession\([\s\S]*?\n}\n\nasync function authenticateCaller/);
  assert.ok(authBody, `${label} must keep the companion session authenticator`);
  assert.doesNotMatch(authBody[0], /expiresAt|expires_at|12\s*\*\s*60\s*\*\s*60/, `${label} must not impose an automatic session expiry`);
  assert.match(workerSource, /expiresAt: now \+ 90_000/, `${label} must keep one-use pairing records short-lived`);
}
assert.match(appSource, /localStorage\.setItem\("convo_web_pair_session"/);
assert.match(appSource, /history\.replaceState\(null, "", `\$\{location\.pathname\}\$\{location\.search\}`\)/, "pairing fragment should be removed from browser history");
assert.equal(appSource, pagesApp, "Cloudflare Pages app copy must match source app");
assert.equal(htmlSource, pagesHtml, "Cloudflare Pages HTML copy must match source HTML");
assert.equal(stylesSource, pagesStyles, "Cloudflare Pages styles copy must match source styles");

console.log("Web companion checks passed (CORS, fail-closed pairing, short-lived codes, persistent sessions, safe handoff fragment, synced Pages files).");
