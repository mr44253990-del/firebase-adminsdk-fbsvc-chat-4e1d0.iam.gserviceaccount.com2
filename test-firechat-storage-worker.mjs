import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { generateKeyPairSync, sign as rsaSign, webcrypto } from "node:crypto";

const source = await readFile(new URL("./firechat-storage-worker.js", import.meta.url), "utf8");
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`;
const { default: storageWorker } = await import(moduleUrl);
globalThis.crypto ??= webcrypto;

const projectId = "convo-test-project";
const serviceRoleKey = "storage-worker-test-secret";
const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const firebasePublicJwk = { ...publicKey.export({ format: "jwk" }), kid: "test-firebase-key", alg: "RS256", use: "sig" };
const firebasePrivateKey = privateKey.export({ format: "pem", type: "pkcs8" }).toString();
const env = {
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify({ project_id: projectId, client_email: "worker@test-project.iam.gserviceaccount.com", private_key: firebasePrivateKey }),
  SUPABASE_SERVICE_ROLE_KEY: serviceRoleKey
};
const requiredBuckets = ["avatars", "chat_images", "voice_notes", "feed_posts", "reels", "thumbnails", "apk_releases"];
const bucketState = new Map(requiredBuckets.filter(id => id !== "apk_releases").map(id => [id, { id, name: id, public: true }]));
const upstreamCalls = [];
const originalFetch = globalThis.fetch;

globalThis.fetch = async (input, init = {}) => {
  const url = new URL(typeof input === "string" ? input : input.url);
  const method = String(init.method || "GET").toUpperCase();
  upstreamCalls.push({ url: url.toString(), method, headers: new Headers(init.headers || {}) });

  if (url.hostname === "www.googleapis.com" && url.pathname.includes("/service_accounts/v1/jwk/")) {
    return Response.json({ keys: [firebasePublicJwk] });
  }
  if (url.hostname === "oauth2.googleapis.com" && url.pathname === "/token") {
    return Response.json({ access_token: "test-google-access-token", expires_in: 3600 });
  }
  if (url.hostname === "firestore.googleapis.com" && url.pathname.endsWith("/documents/app_config/storage")) {
    return Response.json({
      fields: {
        provider: { stringValue: "supabase" },
        supabaseUrl: { stringValue: "https://demo-project.supabase.co" },
        storageWorkerUrl: { stringValue: "https://convo-storage.test.workers.dev" },
        bucketPosts: { stringValue: "feed_posts" },
        bucketUpdates: { stringValue: "apk_releases" },
        autoCreateBuckets: { booleanValue: true }
      }
    });
  }
  if (url.hostname === "demo-project.supabase.co") {
    if (url.pathname === "/storage/v1/bucket" && method === "GET") return Response.json([...bucketState.values()]);
    if (url.pathname === "/storage/v1/bucket" && method === "POST") {
      const body = JSON.parse(init.body.toString());
      const bucket = { id: body.id, name: body.name, public: body.public === true };
      bucketState.set(bucket.id, bucket);
      return Response.json(bucket, { status: 200 });
    }
    if (url.pathname.startsWith("/storage/v1/object/")) return new Response(null, { status: method === "DELETE" ? 200 : 201 });
  }
  throw new Error(`Unexpected mocked fetch: ${method} ${url}`);
};

function base64url(value) {
  return Buffer.from(typeof value === "string" ? value : JSON.stringify(value)).toString("base64url");
}
function firebaseIdToken({ uid, email }) {
  const header = base64url({ alg: "RS256", typ: "JWT", kid: "test-firebase-key" });
  const now = Math.floor(Date.now() / 1000);
  const payload = base64url({
    aud: projectId,
    iss: `https://securetoken.google.com/${projectId}`,
    sub: uid,
    email,
    iat: now,
    exp: now + 3600
  });
  const input = `${header}.${payload}`;
  const signature = rsaSign("RSA-SHA256", Buffer.from(input), privateKey).toString("base64url");
  return `${input}.${signature}`;
}
function request(path, { token, method = "POST", body, headers = {} } = {}) {
  return new Request(`https://convo-storage.test.workers.dev${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body && !(body instanceof Uint8Array) ? { "Content-Type": "application/json" } : {}),
      ...headers
    },
    body: body == null ? undefined : body instanceof Uint8Array ? body : JSON.stringify(body)
  });
}

try {
  const health = await storageWorker.fetch(request("/health", { method: "GET" }), env);
  const healthBody = await health.json();
  assert.equal(health.status, 200);
  assert.equal(healthBody.ok, true);
  assert.equal(healthBody.supabaseServiceRoleConfigured, true);
  assert.equal(JSON.stringify(healthBody).includes(serviceRoleKey), false, "health must never disclose the Supabase secret");
  const missingSecretHealth = await storageWorker.fetch(request("/health", { method: "GET" }), { FIREBASE_SERVICE_ACCOUNT: env.FIREBASE_SERVICE_ACCOUNT });
  const missingSecretBody = await missingSecretHealth.json();
  assert.equal(missingSecretHealth.status, 503);
  assert.ok(missingSecretBody.instructions.some(step => step.includes("SUPABASE_SERVICE_ROLE_KEY")), "missing secret diagnostics must be actionable");

  const unauthorized = await storageWorker.fetch(request("/storage/upload?bucket=chat_images&filename=test.jpg", { body: new Uint8Array([1, 2]) }), env);
  assert.equal(unauthorized.status, 401, "uploads must require a Firebase ID token");

  const userToken = firebaseIdToken({ uid: "user-123", email: "user@example.com" });
  const adminToken = firebaseIdToken({ uid: "admin-uid", email: "mr4425390@gmail.com" });

  const tested = await storageWorker.fetch(request("/admin/storage/test", { token: adminToken, body: { createMissingBuckets: true } }), env);
  const testedBody = await tested.json();
  assert.equal(tested.status, 200);
  assert.equal(testedBody.ok, true);
  assert.equal(testedBody.readyBuckets, 7);
  assert.equal(testedBody.buckets.find(bucket => bucket.id === "apk_releases").created, true);
  assert.equal(testedBody.buckets.every(bucket => bucket.write && bucket.delete && bucket.ready), true);
  assert.equal(JSON.stringify(testedBody).includes(serviceRoleKey), false, "diagnostics must never disclose the Supabase secret");
  assert.equal(bucketState.has("apk_releases"), true, "setup test creates the configured update bucket");

  const uploaded = await storageWorker.fetch(request("/storage/upload?bucket=chat_images&filename=chat%20photo.jpg&kind=attachment", {
    token: userToken,
    body: new Uint8Array([1, 2, 3]),
    headers: { "Content-Type": "image/jpeg" }
  }), env);
  const uploadedBody = await uploaded.json();
  assert.equal(uploaded.status, 201);
  assert.equal(uploadedBody.ok, true);
  assert.equal(JSON.stringify(uploadedBody).includes(serviceRoleKey), false, "upload responses must never disclose the Supabase secret");
  assert.match(uploadedBody.key, /^supabase:chat_images\/user-123\//);
  assert.match(uploadedBody.publicUrl, /^https:\/\/demo-project\.supabase\.co\/storage\/v1\/object\/public\/chat_images\//);
  assert.ok(upstreamCalls.some(call => call.headers.get("Authorization") === `Bearer ${serviceRoleKey}`), "Worker uses its server-side service-role secret for Supabase");

  const postUpload = await storageWorker.fetch(request("/storage/upload?bucket=feed_posts&filename=post.jpg&kind=post", {
    token: userToken,
    body: new Uint8Array([4, 5, 6]),
    headers: { "Content-Type": "image/jpeg" }
  }), env);
  const postBody = await postUpload.json();
  assert.equal(postUpload.status, 201);
  assert.match(postBody.key, /^supabase:feed_posts\/user-123\//, "the Worker reads the saved custom bucket ID");
  assert.ok(postBody.expiresAt > Date.now(), "post expiration metadata remains enabled");

  const nonAdminUpdate = await storageWorker.fetch(request("/storage/upload?bucket=apk_releases&filename=release.apk&kind=update", {
    token: userToken,
    body: new Uint8Array([1]),
    headers: { "Content-Type": "application/vnd.android.package-archive" }
  }), env);
  assert.equal(nonAdminUpdate.status, 403, "APK uploads must be admin-only");

  const otherUsersObject = await storageWorker.fetch(request("/storage/delete", {
    token: userToken,
    body: { key: "supabase:chat_images/someone-else/private.jpg" }
  }), env);
  assert.equal(otherUsersObject.status, 403, "users cannot delete another user's objects");

  const deleted = await storageWorker.fetch(request("/storage/delete", {
    token: userToken,
    body: { key: "supabase:chat_images/user-123/2026-10-10/upload.jpg" }
  }), env);
  const deletedBody = await deleted.json();
  assert.equal(deleted.status, 200);
  assert.equal(deletedBody.ok, true);

  const missingAdmin = await storageWorker.fetch(request("/admin/storage/test", { token: userToken, body: {} }), env);
  assert.equal(missingAdmin.status, 403, "diagnostics are admin-only");

  console.log("Storage Worker checks passed: secrets stay server-side, admin bucket setup works, uploads authenticate, APK upload is admin-only, and object deletion is owner-checked.");
} finally {
  globalThis.fetch = originalFetch;
}
