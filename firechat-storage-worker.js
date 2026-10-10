/**
 * Convo Chat unified Supabase Storage Worker.
 *
 * Secrets (Cloudflare Worker secrets; never app config):
 *   FIREBASE_SERVICE_ACCOUNT       Firebase service-account JSON with Firestore access
 *   SUPABASE_SERVICE_ROLE_KEY      Supabase service_role/secret key
 *
 * Non-secret routing config is read live from Firestore app_config/storage. This means
 * changing the Supabase Project URL/provider in Admin Settings does not require editing
 * or redeploying this Worker. Android sends Firebase ID tokens only; the Supabase
 * service-role credential stays server-side.
 */

const VERSION = "1.0.0";
const ADMIN_EMAIL = "mr4425390@gmail.com";
const AUTH_SCOPE = "https://www.googleapis.com/auth/datastore";
const MAX_UPLOAD_BYTES = 95 * 1024 * 1024;
const BUCKET_DEFINITIONS = Object.freeze([
  { field: "bucketAvatars", defaultId: "avatars", purpose: "Profile, cover, and group photos" },
  { field: "bucketChatImages", defaultId: "chat_images", purpose: "Chat attachments, stories, and payment proofs" },
  { field: "bucketVoiceNotes", defaultId: "voice_notes", purpose: "Voice messages" },
  { field: "bucketPosts", defaultId: "posts", purpose: "Post images and videos" },
  { field: "bucketReels", defaultId: "reels", purpose: "Reel videos" },
  { field: "bucketThumbnails", defaultId: "thumbnails", purpose: "Post/reel thumbnails" },
  { field: "bucketUpdates", defaultId: "updates", purpose: "Signed APK updates" }
]);
const MEDIA_KIND_BUCKET_FIELD = Object.freeze({
  post: "bucketPosts",
  reel: "bucketReels",
  thumbnail: "bucketThumbnails",
  update: "bucketUpdates"
});
const BUCKET_ID_PATTERN = /^[a-z0-9][a-z0-9_-]{1,62}$/;

let jwksCache = null;
let jwksExpiresAt = 0;
let googleTokenCache = "";
let googleTokenExpiresAt = 0;
let googleTokenAccount = "";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    if (request.method === "OPTIONS") {
      return new Response("OK", {
        headers: corsHeaders({
          "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization"
        })
      });
    }

    if (request.method === "GET" && (path === "/" || path === "/health")) {
      const account = readServiceAccount(env);
      const firebaseConfigured = Boolean(account?.project_id && account?.client_email && account?.private_key);
      const supabaseSecretConfigured = Boolean(String(env.SUPABASE_SERVICE_ROLE_KEY || "").trim());
      const ok = firebaseConfigured && supabaseSecretConfigured;
      const missing = [];
      if (!firebaseConfigured) missing.push("Set the Cloudflare secret FIREBASE_SERVICE_ACCOUNT to a valid Firebase service-account JSON.");
      if (!supabaseSecretConfigured) missing.push("Set the Cloudflare secret SUPABASE_SERVICE_ROLE_KEY to the Supabase service_role/secret key.");
      return jsonResponse({
        ok,
        service: "Convo Chat Unified Supabase Storage",
        version: VERSION,
        firebaseConfigured,
        supabaseServiceRoleConfigured: supabaseSecretConfigured,
        projectId: account?.project_id || null,
        configDocument: "app_config/storage",
        uploadLimitBytes: MAX_UPLOAD_BYTES,
        timestamp: Date.now(),
        instructions: missing
      }, ok ? 200 : 503);
    }

    if (request.method === "POST" && path === "/admin/storage/test") {
      const auth = await authenticateAdmin(request, env);
      if (!auth.ok) return auth.response;
      try {
        const payload = await request.json().catch(() => ({}));
        const config = await readStorageConfig(auth.account);
        if (!config) {
          return jsonResponse({
            ok: false,
            error: "Storage settings have not been saved yet",
            message: "Save the Supabase provider, Project URL, and Storage Worker URL in Admin Settings first.",
            instructions: ["Open Admin control center → Unified app storage", "Enter the Supabase Project URL and this Worker's HTTPS URL, then tap Save + test"]
          }, 409);
        }
        if (config.provider !== "supabase") {
          return jsonResponse({ ok: false, error: "Supabase is not selected", message: "Enable Supabase in Admin Settings before running the storage setup test." }, 409);
        }
        const base = normalizeSupabaseBase(config.supabaseUrl);
        if (!base) {
          return jsonResponse({
            ok: false,
            error: "Invalid Supabase Project URL",
            message: "The saved value must be an HTTPS project URL such as https://your-project.supabase.co, without /storage/v1.",
            instructions: ["Correct the Project URL in Admin Settings and save again"]
          }, 400);
        }
        const serviceKey = getSupabaseServiceKey(env);
        if (!serviceKey) return missingServiceKeyResponse();
        const bucketDefinitions = readConfiguredBuckets(config);
        if (!bucketDefinitions.ok) {
          return jsonResponse({ ok: false, error: bucketDefinitions.error, message: "Bucket IDs in app_config/storage are invalid.", instructions: ["Use seven unique IDs containing 2–63 lowercase letters, digits, underscores, or hyphens", "Save the corrected IDs in Admin Settings and test again"] }, 400);
        }
        const createMissing = typeof payload.createMissingBuckets === "boolean"
          ? payload.createMissingBuckets
          : config.autoCreateBuckets !== false;
        return await testSupabaseStorage(base, serviceKey, createMissing, bucketDefinitions.buckets);
      } catch (error) {
        return jsonResponse({
          ok: false,
          error: safeError(error),
          message: "Storage diagnostics could not finish. Check the Worker secrets and its Firestore service-account access.",
          instructions: ["Confirm FIREBASE_SERVICE_ACCOUNT has Firestore access", "Confirm SUPABASE_SERVICE_ROLE_KEY is set in this Worker", "Review Cloudflare Worker logs for the upstream failure"]
        }, 502);
      }
    }

    if (request.method === "POST" && path === "/storage/upload") {
      const auth = await authenticateCaller(request, env);
      if (!auth.ok) return auth.response;
      try {
        const config = await readStorageConfig(auth.account);
        if (!config || config.provider !== "supabase") {
          return jsonResponse({ ok: false, error: "Supabase is not the active storage provider", message: "An admin must enable Supabase in Admin Settings before uploads can use this Worker." }, 409);
        }
        const base = normalizeSupabaseBase(config.supabaseUrl);
        if (!base) return jsonResponse({ ok: false, error: "Invalid Supabase Project URL in app_config/storage" }, 503);
        const serviceKey = getSupabaseServiceKey(env);
        if (!serviceKey) return missingServiceKeyResponse();

        const configured = readConfiguredBuckets(config);
        if (!configured.ok) return jsonResponse({ ok: false, error: configured.error }, 503);
        const activeBucketIds = new Set(configured.buckets.map(item => item.id));
        const bucket = String(url.searchParams.get("bucket") || "").trim().toLowerCase();
        const kind = String(url.searchParams.get("kind") || "attachment").trim().toLowerCase();
        const requestedName = String(url.searchParams.get("filename") || "file");
        const contentType = String(request.headers.get("Content-Type") || "application/octet-stream").slice(0, 160);
        const contentLength = Number(request.headers.get("Content-Length") || 0);
        if (!activeBucketIds.has(bucket)) {
          return jsonResponse({ ok: false, error: "Unknown storage bucket", message: `Configured buckets: ${[...activeBucketIds].join(", ")}.`, instructions: ["Update the bucket IDs in Admin Settings and run Save + test"] }, 400);
        }
        if (!request.body) return jsonResponse({ ok: false, error: "Upload body is empty" }, 400);
        if (Number.isFinite(contentLength) && contentLength > MAX_UPLOAD_BYTES) {
          return jsonResponse({ ok: false, error: "File exceeds the 95 MB Worker limit", maxBytes: MAX_UPLOAD_BYTES }, 413);
        }
        const kindField = MEDIA_KIND_BUCKET_FIELD[kind];
        const expectedBucket = kindField ? String(config[kindField] || BUCKET_DEFINITIONS.find(item => item.field === kindField)?.defaultId || "") : "";
        if (expectedBucket && bucket !== expectedBucket) {
          return jsonResponse({ ok: false, error: `Upload kind '${kind}' must use the configured '${expectedBucket}' bucket` }, 400);
        }
        const attachmentBuckets = new Set([config.bucketAvatars || "avatars", config.bucketChatImages || "chat_images", config.bucketVoiceNotes || "voice_notes"]);
        if (!expectedBucket && (!new Set(["attachment", "avatar", "story", "voice"]).has(kind) || !attachmentBuckets.has(bucket))) {
          return jsonResponse({ ok: false, error: "Unsupported upload category/bucket combination" }, 400);
        }
        const updatesBucket = String(config.bucketUpdates || "updates");
        const isAdmin = isAdminCaller(auth.caller, env);
        if ((bucket === updatesBucket || kind === "update") && !isAdmin) {
          return jsonResponse({ ok: false, error: "Only the admin can upload app updates" }, 403);
        }
        if (kind === "update" && !requestedName.toLowerCase().endsWith(".apk")) {
          return jsonResponse({ ok: false, error: "App update uploads must be APK files" }, 400);
        }

        const safeName = safeFileName(requestedName);
        const now = Date.now();
        const objectPath = bucket === updatesBucket
          ? `releases/${now}_${crypto.randomUUID()}_${safeName}`
          : `${auth.caller.sub}/${new Date(now).toISOString().slice(0, 10)}/${now}_${crypto.randomUUID()}_${safeName}`;
        const encodedPath = encodeStoragePath(objectPath);
        const upstream = await fetch(`${base}/storage/v1/object/${encodeURIComponent(bucket)}/${encodedPath}`, {
          method: "POST",
          headers: supabaseHeaders(serviceKey, {
            "Content-Type": contentType,
            "Cache-Control": bucket === updatesBucket ? "public, max-age=300" : "public, max-age=3600",
            "x-upsert": "false"
          }),
          body: request.body
        });
        if (!upstream.ok) {
          const detail = await responseDetail(upstream);
          const message = upstream.status === 404 || upstream.status === 400
            ? `Supabase rejected the upload for bucket '${bucket}'. The bucket may be missing, private, or named incorrectly.`
            : `Supabase upload failed (HTTP ${upstream.status}).`;
          return jsonResponse({
            ok: false,
            error: message,
            message: detail,
            upstreamStatus: upstream.status,
            instructions: ["In Admin Settings, tap Save + test to create/verify the configured buckets", "Ensure the bucket is public for app media", "Confirm the Worker secret SUPABASE_SERVICE_ROLE_KEY is the service_role/secret key"]
          }, 502);
        }
        const expiresAt = ["post", "reel", "thumbnail"].includes(kind) ? now + 10 * 24 * 60 * 60 * 1000 : 0;
        const publicUrl = `${base}/storage/v1/object/public/${encodeURIComponent(bucket)}/${encodedPath}`;
        return jsonResponse({
          ok: true,
          success: true,
          provider: "supabase",
          bucket,
          objectPath,
          key: `supabase:${bucket}/${objectPath}`,
          publicUrl,
          expiresAt,
          kind,
          contentType
        }, 201);
      } catch (error) {
        return jsonResponse({ ok: false, error: "Storage upload could not be completed", message: safeError(error) }, 502);
      }
    }

    if (request.method === "POST" && path === "/storage/delete") {
      const auth = await authenticateCaller(request, env);
      if (!auth.ok) return auth.response;
      try {
        const payload = await request.json().catch(() => ({}));
        const config = await readStorageConfig(auth.account);
        if (!config) return jsonResponse({ ok: false, error: "Storage configuration not found" }, 503);
        const base = normalizeSupabaseBase(config.supabaseUrl);
        if (!base) return jsonResponse({ ok: false, error: "Invalid Supabase Project URL in app_config/storage" }, 503);
        const serviceKey = getSupabaseServiceKey(env);
        if (!serviceKey) return missingServiceKeyResponse();
        const configured = readConfiguredBuckets(config);
        if (!configured.ok) return jsonResponse({ ok: false, error: configured.error }, 503);
        const updatesBucket = String(config.bucketUpdates || "updates");

        const target = parseDeleteTarget(payload, base);
        if (!target || !BUCKET_ID_PATTERN.test(target.bucket) || !validStoragePath(target.objectPath)) {
          return jsonResponse({ ok: false, error: "Invalid Supabase object key or URL" }, 400);
        }
        const isAdmin = isAdminCaller(auth.caller, env);
        const isUpdateObject = target.bucket === updatesBucket || target.bucket === "updates" || target.objectPath.startsWith("releases/");
        if (isUpdateObject && !isAdmin) return jsonResponse({ ok: false, error: "Only the admin can delete app update files" }, 403);
        if (!isUpdateObject && !isAdmin && !target.objectPath.startsWith(`${auth.caller.sub}/`)) {
          return jsonResponse({ ok: false, error: "Not allowed to delete another user's media" }, 403);
        }
        const upstream = await fetch(`${base}/storage/v1/object/${encodeURIComponent(target.bucket)}/${encodeStoragePath(target.objectPath)}`, {
          method: "DELETE",
          headers: supabaseHeaders(serviceKey)
        });
        if (!upstream.ok && upstream.status !== 404) {
          return jsonResponse({ ok: false, error: `Supabase delete failed (HTTP ${upstream.status})`, message: await responseDetail(upstream), upstreamStatus: upstream.status }, 502);
        }
        return jsonResponse({ ok: true, deleted: upstream.status !== 404, alreadyDeleted: upstream.status === 404, bucket: target.bucket, objectPath: target.objectPath });
      } catch (error) {
        return jsonResponse({ ok: false, error: "Storage deletion could not be completed", message: safeError(error) }, 502);
      }
    }

    return jsonResponse({ ok: false, error: "Not found" }, 404);
  }
};

async function testSupabaseStorage(base, serviceKey, createMissing, bucketDefinitions) {
  const listResponse = await fetch(`${base}/storage/v1/bucket`, { headers: supabaseHeaders(serviceKey) });
  if (!listResponse.ok) {
    const status = listResponse.status;
    const detail = await responseDetail(listResponse);
    const badKey = status === 401 || status === 403;
    return jsonResponse({
      ok: false,
      error: badKey ? "Supabase rejected the Worker service-role key" : `Could not list Supabase buckets (HTTP ${status})`,
      message: detail,
      projectHost: new URL(base).host,
      upstreamStatus: status,
      instructions: badKey
        ? ["In Supabase Dashboard → Project Settings → API, copy the service_role/secret key", "Set it as Cloudflare Worker secret SUPABASE_SERVICE_ROLE_KEY (not in Android, Firestore, or Admin Settings)", "Redeploy/restart the Worker and test again"]
        : ["Check the Supabase Project URL", "Confirm the project is active and Storage is enabled", "Review the Cloudflare Worker logs"]
    }, 502);
  }
  let listed = await listResponse.json().catch(() => []);
  if (!Array.isArray(listed)) listed = [];
  const bucketChecks = [];
  const instructions = [];

  for (const definition of bucketDefinitions) {
    let bucket = listed.find(item => String(item?.id || item?.name || "") === definition.id) || null;
    let created = false;
    if (!bucket && createMissing) {
      const createResponse = await fetch(`${base}/storage/v1/bucket`, {
        method: "POST",
        headers: supabaseHeaders(serviceKey, { "Content-Type": "application/json" }),
        body: JSON.stringify({ id: definition.id, name: definition.id, public: true })
      });
      if (createResponse.ok) {
        bucket = await createResponse.json().catch(() => ({ id: definition.id, public: true }));
        created = true;
      } else if (createResponse.status === 409) {
        const refreshed = await fetch(`${base}/storage/v1/bucket`, { headers: supabaseHeaders(serviceKey) });
        const refreshedBuckets = refreshed.ok ? await refreshed.json().catch(() => []) : [];
        bucket = Array.isArray(refreshedBuckets) ? refreshedBuckets.find(item => String(item?.id || item?.name || "") === definition.id) || null : null;
      } else {
        const detail = await responseDetail(createResponse);
        bucketChecks.push({
          id: definition.id,
          purpose: definition.purpose,
          exists: false,
          created: false,
          public: false,
          write: false,
          delete: false,
          ready: false,
          message: `Bucket creation failed (HTTP ${createResponse.status}): ${detail}`
        });
        continue;
      }
    }
    if (!bucket) {
      bucketChecks.push({
        id: definition.id,
        purpose: definition.purpose,
        exists: false,
        created: false,
        public: false,
        write: false,
        delete: false,
        ready: false,
        message: createMissing ? "Bucket creation did not return a bucket; retry the setup test" : "Bucket is missing"
      });
      instructions.push(createMissing
        ? `Bucket '${definition.id}' was not created; check the service-role key and Supabase Storage settings.`
        : `Create a public bucket named '${definition.id}' in Supabase Dashboard → Storage, or enable automatic bucket creation.`);
      continue;
    }

    const isPublic = bucket.public === true;
    if (!isPublic) {
      bucketChecks.push({
        id: definition.id,
        purpose: definition.purpose,
        exists: true,
        created,
        public: false,
        write: false,
        delete: false,
        ready: false,
        message: "Bucket exists but is private; app media requires a public bucket"
      });
      instructions.push(`Change '${definition.id}' to Public in Supabase Dashboard → Storage → bucket settings, then test again.`);
      continue;
    }

    const objectPath = `__worker_health__/check-${Date.now()}-${crypto.randomUUID()}.txt`;
    const objectUrl = `${base}/storage/v1/object/${encodeURIComponent(definition.id)}/${encodeStoragePath(objectPath)}`;
    const uploadResponse = await fetch(objectUrl, {
      method: "POST",
      headers: supabaseHeaders(serviceKey, { "Content-Type": "text/plain", "Cache-Control": "no-store", "x-upsert": "false" }),
      body: new TextEncoder().encode("Convo Chat storage health check")
    });
    if (!uploadResponse.ok) {
      const detail = await responseDetail(uploadResponse);
      bucketChecks.push({
        id: definition.id, purpose: definition.purpose, exists: true, created, public: true,
        write: false, delete: false, ready: false,
        message: `Upload smoke test failed (HTTP ${uploadResponse.status}): ${detail}`
      });
      instructions.push(`Check that the Worker key has Storage write permission for '${definition.id}'.`);
      continue;
    }
    const deleteResponse = await fetch(objectUrl, { method: "DELETE", headers: supabaseHeaders(serviceKey) });
    if (!deleteResponse.ok && deleteResponse.status !== 404) {
      const detail = await responseDetail(deleteResponse);
      bucketChecks.push({
        id: definition.id, purpose: definition.purpose, exists: true, created, public: true,
        write: true, delete: false, ready: false,
        message: `Upload worked, but cleanup failed (HTTP ${deleteResponse.status}): ${detail}`
      });
      instructions.push(`Check delete permission for '${definition.id}' or remove '${objectPath}' manually from Supabase Storage.`);
      continue;
    }
    bucketChecks.push({
      id: definition.id, purpose: definition.purpose, exists: true, created, public: true,
      write: true, delete: true, ready: true,
      message: created ? "Created; upload/delete check passed" : "Upload/delete check passed"
    });
  }

  const readyCount = bucketChecks.filter(item => item.ready).length;
  const ok = readyCount === bucketDefinitions.length;
  if (!ok && instructions.length === 0) instructions.push("Open the bucket details below, correct the reported issue, and tap Save + test again.");
  return jsonResponse({
    ok,
    success: ok,
    service: "Convo Chat Unified Supabase Storage",
    version: VERSION,
    provider: "supabase",
    projectHost: new URL(base).host,
    autoCreatedMissingBuckets: createMissing,
    readyBuckets: readyCount,
    totalBuckets: bucketDefinitions.length,
    buckets: bucketChecks,
    message: ok
      ? `Supabase is ready: ${readyCount}/${bucketDefinitions.length} public buckets passed upload and delete checks.`
      : `Storage setup needs attention: ${readyCount}/${bucketDefinitions.length} buckets are ready.`,
    instructions
  }, ok ? 200 : 503);
}

async function authenticateCaller(request, env) {
  const account = readServiceAccount(env);
  if (!account?.project_id || !account?.client_email || !account?.private_key) {
    return { ok: false, response: jsonResponse({ ok: false, error: "FIREBASE_SERVICE_ACCOUNT Worker secret is missing or invalid", instructions: ["Set the Cloudflare Worker secret FIREBASE_SERVICE_ACCOUNT to a valid service-account JSON"] }, 503) };
  }
  const caller = await verifyFirebaseIdToken(request, account.project_id);
  if (!caller) return { ok: false, response: jsonResponse({ ok: false, error: "Unauthorized Firebase caller", instructions: ["Sign in again and retry the request"] }, 401) };
  return { ok: true, caller, account };
}

async function authenticateAdmin(request, env) {
  const auth = await authenticateCaller(request, env);
  if (!auth.ok) return auth;
  if (!isAdminCaller(auth.caller, env)) {
    return { ok: false, response: jsonResponse({ ok: false, error: "Admin only" }, 403) };
  }
  return auth;
}

function isAdminCaller(caller, env) {
  const email = String(caller?.email || "").trim().toLowerCase().replace(/\.+$/, "");
  const configured = String(env.ADMIN_EMAIL || ADMIN_EMAIL).trim().toLowerCase().replace(/\.+$/, "");
  return email === configured;
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

async function readStorageConfig(account) {
  const token = await getGoogleAccessToken(account, AUTH_SCOPE);
  return firestoreGet(account.project_id, "app_config/storage", token);
}

function readConfiguredBuckets(config) {
  const buckets = BUCKET_DEFINITIONS.map(definition => ({
    id: String(config?.[definition.field] || definition.defaultId).trim().toLowerCase(),
    purpose: definition.purpose
  }));
  const ids = buckets.map(bucket => bucket.id);
  if (ids.some(id => !BUCKET_ID_PATTERN.test(id))) return { ok: false, error: "Invalid bucket ID. Use 2–63 lowercase letters, digits, underscores, or hyphens." };
  if (new Set(ids).size !== ids.length) return { ok: false, error: "Every storage category must have a unique bucket ID." };
  return { ok: true, buckets };
}

async function getGoogleAccessToken(account, scope) {
  const now = Math.floor(Date.now() / 1000);
  if (googleTokenCache && googleTokenAccount === account.client_email && googleTokenExpiresAt > now + 60) return googleTokenCache;
  const claim = {
    iss: account.client_email,
    scope,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  };
  const input = `${base64UrlEncode({ alg: "RS256", typ: "JWT" })}.${base64UrlEncode(claim)}`;
  const assertion = `${input}.${await signRs256(input, account.private_key)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion })
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.access_token) throw new Error(`Firebase service token failed (HTTP ${response.status})`);
  googleTokenCache = String(result.access_token);
  googleTokenAccount = account.client_email;
  googleTokenExpiresAt = now + Math.min(3500, Number(result.expires_in || 3600));
  return googleTokenCache;
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

async function firestoreGet(projectId, documentPath, token) {
  const safePath = documentPath.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${safePath}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore config read failed (HTTP ${response.status})`);
  const document = await response.json();
  return Object.fromEntries(Object.entries(document.fields || {}).map(([key, value]) => [key, fromFirestoreValue(value)]));
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

function readServiceAccount(env) {
  try { return JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}"); }
  catch (_) { return null; }
}

function getSupabaseServiceKey(env) {
  return String(env.SUPABASE_SERVICE_ROLE_KEY || "").trim();
}

function missingServiceKeyResponse() {
  return jsonResponse({
    ok: false,
    error: "Supabase Worker secret is missing",
    message: "The app configuration is saved, but the Worker has no server-side Supabase service-role key.",
    instructions: [
      "In Supabase Dashboard → Project Settings → API, copy the service_role/secret key for the same project",
      "In Cloudflare, run: npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY --config wrangler-storage.toml",
      "Paste the key into the Wrangler prompt only; never put it in Android or Firestore",
      "Retry Save + test in Admin Settings"
    ]
  }, 503);
}

function normalizeSupabaseBase(value) {
  try {
    const url = new URL(String(value || "").trim());
    if (url.protocol !== "https:" || !url.hostname || url.username || url.password || url.search || url.hash || !["", "/"].includes(url.pathname)) return "";
    return url.origin;
  } catch (_) { return ""; }
}

function supabaseHeaders(serviceKey, extra = {}) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    ...extra
  };
}

async function responseDetail(response) {
  const text = await response.text().catch(() => "");
  if (!text) return response.statusText || "No details returned";
  try {
    const value = JSON.parse(text);
    return String(value.message || value.msg || value.error_description || value.error || text).slice(0, 300);
  } catch (_) { return text.slice(0, 300); }
}

function safeFileName(value) {
  const basename = String(value || "file").replace(/\\/g, "/").split("/").pop() || "file";
  const safe = basename.normalize("NFKD").replace(/[^A-Za-z0-9._-]/g, "_").replace(/\.{2,}/g, ".").replace(/^\.+/, "").slice(-140);
  return safe || "file";
}

function encodeStoragePath(path) {
  return String(path).split("/").map(segment => encodeURIComponent(segment)).join("/");
}

function parseDeleteTarget(payload, base) {
  const rawKey = String(payload.key || "").trim();
  if (rawKey.startsWith("supabase:")) {
    const value = rawKey.slice("supabase:".length);
    const separator = value.indexOf("/");
    if (separator <= 0) return null;
    return { bucket: value.slice(0, separator), objectPath: value.slice(separator + 1) };
  }
  const rawUrl = String(payload.url || "").trim();
  if (!rawUrl) return null;
  try {
    const url = new URL(rawUrl);
    if (url.origin !== base) return null;
    const marker = "/storage/v1/object/public/";
    if (!url.pathname.startsWith(marker)) return null;
    const path = url.pathname.slice(marker.length);
    const [encodedBucket, ...encodedSegments] = path.split("/");
    if (!encodedBucket || encodedSegments.length === 0) return null;
    const bucket = decodeURIComponent(encodedBucket);
    const objectPath = encodedSegments.map(segment => decodeURIComponent(segment)).join("/");
    return { bucket, objectPath };
  } catch (_) { return null; }
}

function validStoragePath(value) {
  if (!value || value.length > 900 || value.startsWith("/") || value.includes("\\") || value.includes("//")) return false;
  const segments = value.split("/");
  return segments.every(segment => segment && segment !== "." && segment !== ".." && !/[\u0000-\u001f]/.test(segment));
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
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
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
  return String(error?.message || "Request failed").slice(0, 280);
}
