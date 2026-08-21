/**
 * Convo Chat Cloudflare Worker
 * Handles FCM v1 Push Notification Delivery with Zero-Dependencies.
 *
 * This worker accepts a POST request with the target FCM token, title, and body,
 * signs an OAuth2 JWT using the Web Crypto API, exchanges it for a Google Access Token,
 * and calls the Firebase Cloud Messaging v1 API.
 *
 * Configure the secret env variable:
 * - FIREBASE_SERVICE_ACCOUNT: The entire JSON string of your Firebase Service Account Private Key.
 */

export default {
  async scheduled(controller, env, ctx) {
    // Configure a Cron Trigger for */5 * * * * in the Cloudflare dashboard.
    // Cleanup and reachability probes share the existing Firebase service account;
    // no new binding or secret is required.
    ctx.waitUntil((async () => {
      await expireStalePresence(env);
      await sendPresenceProbes(env);
    })());
  },
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    // Enable CORS
    if (request.method === "OPTIONS") {
      return new Response("OK", {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Media-Tags, X-Media-Title",
        },
      });
    }

    if ((request.method === "POST" || request.method === "PUT") && path === "/media/upload") {
      try {
      const auth = await authenticateCaller(request, env);
      if (!auth.ok) return auth.response;
      if (!env.MEDIA_BUCKET || typeof env.MEDIA_BUCKET.put !== "function" || !env.R2_PUBLIC_BASE_URL) {
        return jsonResponse({ error: "MEDIA_BUCKET binding or R2_PUBLIC_BASE_URL is missing" }, 503);
      }
      const contentType = request.headers.get("Content-Type") || "application/octet-stream";
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > 95 * 1024 * 1024) return jsonResponse({ error: "Media exceeds 95 MB limit" }, 413);
      const requestedKind = url.searchParams.get("kind");
      const isAdmin = auth.caller.email?.toLowerCase().replace(/\.+$/, "") === "mr4425390@gmail.com";
      if (requestedKind === "update" && !isAdmin) return jsonResponse({ error: "Only the admin can upload app updates" }, 403);
      const kind = requestedKind === "reel" ? "reels" : requestedKind === "thumbnail" ? "thumbnails" : requestedKind === "update" ? "updates" : "posts";
      const extension = (url.searchParams.get("extension") || mimeExtension(contentType)).replace(/[^a-z0-9]/gi, "").slice(0, 8) || "bin";
      const key = `${kind}/${auth.caller.sub}/${Date.now()}-${crypto.randomUUID()}.${extension}`;
      const expiresAt = kind === "updates" ? 0 : Date.now() + 10 * 24 * 60 * 60 * 1000;
      await env.MEDIA_BUCKET.put(key, request.body, {
        httpMetadata: { contentType, cacheControl: "public, max-age=3600" },
        customMetadata: {
          ownerUid: auth.caller.sub,
          kind,
          expiresAt: String(expiresAt),
          title: (request.headers.get("X-Media-Title") || "").slice(0, 200),
          tags: (request.headers.get("X-Media-Tags") || "").slice(0, 500)
        }
      });
      const publicBase = env.R2_PUBLIC_BASE_URL.replace(/\/$/, "");
      return jsonResponse({ success: true, key, publicUrl: `${publicBase}/${key}`, expiresAt, kind }, 201);
      } catch (error) {
        return jsonResponse({
          success: false,
          error: "R2 upload failed",
          errorCode: "R2_UPLOAD_EXCEPTION",
          details: error?.message || String(error),
          bindingType: typeof env.MEDIA_BUCKET,
          hasPublicBaseUrl: Boolean(env.R2_PUBLIC_BASE_URL)
        }, 500);
      }
    }

    if (request.method === "GET" && path.startsWith("/post/")) {
      const postId = decodeURIComponent(path.slice(6));
      if (!/^[A-Za-z0-9_-]{4,160}$/.test(postId)) return htmlResponse("Invalid post link", 400);
      try {
        const post = await firestoreGet(env, `posts/${postId}`);
        if (!post) return htmlResponse("Post not found", 404);
        const title = escapeHtml(post.title || `${post.senderName || "Someone"} on Convo Chat`);
        const description = escapeHtml(String(post.text || "Open this post in Convo Chat").slice(0,240));
        const image = escapeHtml(post.imageUrl || (Array.isArray(post.imageUrls) ? post.imageUrls[0] : "") || "");
        const video = escapeHtml(post.videoUrl || "");
        const appLink = `convochat://post/${encodeURIComponent(postId)}`;
        const downloadRaw = env.APP_DOWNLOAD_URL || new URL(request.url).origin;
        const download = escapeHtml(downloadRaw);
        const intentLink = `intent://post/${encodeURIComponent(postId)}#Intent;scheme=convochat;package=com.ebchat;S.browser_fallback_url=${encodeURIComponent(downloadRaw)};end`;
        const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><meta name="description" content="${description}"><meta property="og:site_name" content="Convo Chat"><meta property="og:type" content="article"><meta property="og:title" content="${title}"><meta property="og:description" content="${description}">${image ? `<meta property="og:image" content="${image}">` : ""}<style>body{margin:0;background:#070814;color:white;font-family:system-ui;display:grid;place-items:center;min-height:100vh}.c{max-width:620px;margin:20px;padding:28px;border-radius:28px;background:linear-gradient(145deg,#211c45,#08192b);border:1px solid #806fff77}img,video{width:100%;max-height:420px;object-fit:cover;border-radius:20px;background:#000}a{display:block;margin-top:12px;padding:14px;border-radius:999px;text-align:center;color:white;text-decoration:none;font-weight:800;background:linear-gradient(90deg,#6757ff,#ef3fac)}.s{background:#ffffff18}</style></head><body><main class="c">${video ? `<video id="postVideo" controls preload="metadata" poster="${image}"><source src="${video}"></video>` : image ? `<img src="${image}">` : ""}<h1>${title}</h1><p>${description}</p><a id="openApp" href="${intentLink}">Open application</a><a class="s" href="${download}">Download application</a></main><script>document.addEventListener("DOMContentLoaded",()=>{setTimeout(()=>{location.href=${JSON.stringify(intentLink)}},180);const v=document.getElementById("postVideo");if(v)v.addEventListener("play",e=>{if(!v.dataset.asked){e.preventDefault();v.pause();v.dataset.asked="1";if(confirm("For the best experience, open this video in Convo Chat. Open the app now?"))location.href=${JSON.stringify(intentLink)};else v.play()}})})</script></body></html>`;
        return new Response(html,{headers:{"Content-Type":"text/html; charset=utf-8","Cache-Control":"public,max-age=300"}});
      } catch(e) { return htmlResponse("Could not load this post",500); }
    }

    // Public profile landing page used by generated QR codes and shared profile links.
    // The Android app still owns the actual profile view; this route provides a safe
    // web preview and an explicit deep-link button instead of returning the Worker
    // health response for /profile?uid=... .
    if (request.method === "GET" && path === "/profile") {
      const uid = String(url.searchParams.get("uid") || "").trim();
      if (!/^[A-Za-z0-9_-]{6,180}$/.test(uid)) return htmlResponse("Invalid profile link", 400);
      try {
        const profile = await firestoreGet(env, `users/${uid}`);
        if (!profile) return htmlResponse("Profile not found", 404);
        if (profile.profileHidden === true || profile.profileVisibility === "hidden") {
          return htmlResponse("This profile is not publicly discoverable", 404);
        }
        const name = escapeHtml(String(profile.name || profile.username || "Convo Chat user").slice(0, 120));
        const username = escapeHtml(String(profile.username || "").slice(0, 80));
        const bio = escapeHtml(String(profile.bio || "Open this profile in Convo Chat").slice(0, 320));
        const image = escapeHtml(String(profile.profileImageUrl || profile.image || ""));
        const appLink = `convochat://profile?uid=${encodeURIComponent(uid)}`;
        const fallback = escapeHtml(env.APP_DOWNLOAD_URL || new URL(request.url).origin);
        const intentLink = `intent://profile?uid=${encodeURIComponent(uid)}#Intent;scheme=convochat;package=com.ebchat;S.browser_fallback_url=${encodeURIComponent(env.APP_DOWNLOAD_URL || new URL(request.url).origin)};end`;
        const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${name} — Convo Chat</title><meta name="description" content="${bio}"><meta property="og:site_name" content="Convo Chat"><meta property="og:type" content="profile"><meta property="og:title" content="${name}${username ? ` (@${username})` : ""}"><meta property="og:description" content="${bio}">${image ? `<meta property="og:image" content="${image}">` : ""}<style>body{margin:0;background:#070814;color:#fff;font-family:system-ui;display:grid;place-items:center;min-height:100vh}.card{width:min(620px,calc(100% - 40px));padding:30px;border-radius:30px;background:linear-gradient(145deg,#211c45,#08192b);border:1px solid #806fff77;box-sizing:border-box;text-align:center;position:relative;overflow:hidden;animation:cardIn .72s cubic-bezier(.2,.8,.2,1) both;box-shadow:0 18px 70px #09001f99}.card:before{content:"";position:absolute;inset:-60% -20%;background:linear-gradient(110deg,transparent 42%,#ffffff22 50%,transparent 58%);transform:translateX(-70%) rotate(8deg);animation:sheen 4.8s ease-in-out infinite;pointer-events:none}.card>*{position:relative;z-index:1}.avatar{width:112px;height:112px;border-radius:50%;object-fit:cover;background:#ffffff18;border:4px solid #ffffff44;animation:avatarPulse 2.8s ease-in-out infinite}.name{font-size:28px;font-weight:800;margin:18px 0 4px}.user{color:#b8b6d8}.bio{color:#e4e1ff;line-height:1.55}.btn{display:block;margin-top:16px;padding:15px;border-radius:999px;text-decoration:none;color:white;font-weight:800;background:linear-gradient(90deg,#6757ff,#ef3fac);transition:transform .2s ease,filter .2s ease}.btn:hover{transform:translateY(-2px);filter:brightness(1.12)}.secondary{background:#ffffff18}@keyframes cardIn{from{opacity:0;transform:translateY(18px) scale(.98)}to{opacity:1;transform:translateY(0) scale(1)}}@keyframes sheen{0%,35%{transform:translateX(-75%) rotate(8deg)}70%,100%{transform:translateX(75%) rotate(8deg)}}@keyframes avatarPulse{0%,100%{box-shadow:0 0 0 0 #7ee7ff22}50%{box-shadow:0 0 0 12px #7ee7ff00}}@media (prefers-reduced-motion:reduce){.card,.avatar,.card:before{animation:none}.btn{transition:none}}</style></head><body><main class="card">${image ? `<img class="avatar" src="${image}" alt="${name}">` : `<div class="avatar" aria-hidden="true"></div>`}<div class="name">${name}</div>${username ? `<div class="user">@${username}</div>` : ""}<p class="bio">${bio}</p><a class="btn" href="${intentLink}">Open profile in Convo Chat</a><a class="btn secondary" href="${fallback}">Download or open Convo Chat</a></main><script>setTimeout(()=>{location.href=${JSON.stringify(appLink)}},350)</script></body></html>`;
        return new Response(html, { status: 200, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "public,max-age=120", "Access-Control-Allow-Origin": "*" } });
      } catch (error) {
        return htmlResponse("Could not load this profile", 500);
      }
    }

    // Lightweight, cacheable Open Graph preview proxy for chat links.
    // Only public http(s) URLs are accepted; responses are capped to avoid SSRF-style abuse.
    if (request.method === "GET" && path === "/link-preview") {
      const rawTarget = String(url.searchParams.get("url") || "").trim();
      let target;
      try { target = new URL(rawTarget); } catch (_) { return jsonResponse({ error: "Invalid URL" }, 400); }
      if (!["http:", "https:"].includes(target.protocol) || target.username || target.password) {
        return jsonResponse({ error: "Only public http(s) URLs are supported" }, 400);
      }
      try {
        const response = await fetch(target.toString(), {
          headers: { "User-Agent": "ConvoChatLinkPreview/1.0", "Accept": "text/html,application/xhtml+xml" },
          redirect: "follow",
          cf: { cacheTtl: 600, cacheEverything: true }
        });
        if (!response.ok) return jsonResponse({ url: target.toString(), title: target.hostname, description: "Open link", image: "" });
        const contentType = response.headers.get("content-type") || "";
        if (!contentType.includes("text/html")) return jsonResponse({ url: target.toString(), title: target.hostname, description: contentType.split(";")[0], image: "" });
        const html = (await response.text()).slice(0, 180000);
        const meta = (name) => {
          const escaped = name.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&");
          const re = new RegExp(`<meta[^>]+(?:property|name)=["']${escaped}["'][^>]+content=["']([^"']*)["'][^>]*>|<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']${escaped}["'][^>]*>`, "i");
          const m = html.match(re); return m ? (m[1] || m[2] || "").trim() : "";
        };
        const titleMatch = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
        const title = decodeHtml(meta("og:title") || meta("twitter:title") || (titleMatch?.[1] || "").trim() || target.hostname).slice(0, 180);
        const description = decodeHtml(meta("og:description") || meta("twitter:description") || meta("description") || "Open link").slice(0, 320);
        const imageMeta = (name) => meta(name);
        let image = imageMeta("og:image") || imageMeta("og:image:secure_url") || imageMeta("twitter:image") || imageMeta("twitter:image:src") || imageMeta("image_src");
        if (!image) {
          const linkImage = html.match(/<link[^>]+rel=["'][^"']*image_src[^"']*["'][^>]+href=["']([^"']+)["'][^>]*>/i)
            || html.match(/<link[^>]+href=["']([^"']+)["'][^>]+rel=["'][^"']*image_src[^"']*["'][^>]*>/i);
          image = linkImage?.[1] || "";
        }
        if (!image) {
          const firstImage = html.match(/<img[^>]+(?:src|data-src)=["']([^"']+)["'][^>]*>/i);
          image = firstImage?.[1] || "";
        }
        try { if (image) image = new URL(decodeHtml(image), target).toString(); } catch (_) { image = ""; }
        return jsonResponse({ url: target.toString(), title, description, image, site: target.hostname }, 200, 600);
      } catch (_) {
        return jsonResponse({ url: target.toString(), title: target.hostname, description: "Open link", image: "" }, 200, 60);
      }
    }

    // Presence is an authenticated, short-lived Firestore lease. The Android client
    // still writes its primary RTDB status heartbeat; this endpoint is the Worker-side
    // mirror used by web/profile integrations and diagnostics. This is the only
    // heartbeat route; the authenticated branch below must never duplicate it.
    if (request.method === "POST" && path === "/presence/heartbeat") {
      const auth = await authenticateCaller(request, env);
      if (!auth.ok) return auth.response;
      try {
        const body = await request.json().catch(() => ({}));
        const now = Date.now();
        const active = body.active !== false;
        const onlineUntil = active
          ? Math.max(Number(body.onlineUntil) || 0, now + 5 * 60 * 1000)
          : 0;
        const state = {
          uid: auth.caller.sub,
          isOnline: active,
          foreground: body.foreground !== false,
          lastActive: now,
          onlineUntil,
          offlineSince: active ? null : now,
          source: "worker-heartbeat"
        };
        await firestoreSet(env, `presence/${auth.caller.sub}`, state);
        return jsonResponse({ ok: true, ...state }, 200);
      } catch (error) {
        return jsonResponse({ ok: false, error: error?.message || "Presence write failed" }, 500);
      }
    }

    if (request.method === "POST" && path === "/presence/me") {
      const auth = await authenticateCaller(request, env);
      if (!auth.ok) return auth.response;
      try {
        const state = await firestoreGet(env, `presence/${auth.caller.sub}`);
        if (!state) return jsonResponse({ ok: true, uid: auth.caller.sub, isOnline: false, offlineSince: null }, 200);
        const now = Date.now();
        const isOnline = state.isOnline === true && Number(state.onlineUntil || 0) > now;
        return jsonResponse({ ok: true, ...state, isOnline }, 200);
      } catch (error) {
        return jsonResponse({ ok: false, error: error?.message || "Presence read failed" }, 500);
      }
    }

    if (request.method === "GET") {
      let projectId = null;
      let serviceAccountConfigured = false;
      try {
        const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
        projectId = account.project_id || null;
        serviceAccountConfigured = Boolean(account.private_key && account.client_email && projectId);
      } catch (_) {}
      return new Response(JSON.stringify({
        ok: serviceAccountConfigured,
        service: "Convo Chat Direct FCM Gateway",
        version: "4.5.0",
        projectId,
        serviceAccountConfigured,
        turnConfigured: Boolean(env.TURN_TOKEN_ID && env.TURN_API_TOKEN),
        sfuConfigured: Boolean(env.CALLS_APP_ID && env.CALLS_APP_TOKEN),
        aiConfigured: Boolean(env.MISTRAL_API_KEY),
        r2Configured: Boolean(env.MEDIA_BUCKET && typeof env.MEDIA_BUCKET.put === "function" && typeof env.MEDIA_BUCKET.list === "function" && env.R2_PUBLIC_BASE_URL),
        mediaRetentionDays: 10,
        authenticatedCallsRequired: true,
        timestamp: Date.now()
      }), {
        status: serviceAccountConfigured ? 200 : 503,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Cache-Control": "no-store" }
      });
    }

    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Only GET health checks and POST API requests are supported" }), {
        status: 405,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }

    try {
      const payload = await request.json();
      const { token, title, body, senderId, senderName, senderProfileUrl, notificationType, targetId, timestamp } = payload;
      // Preserve non-sensitive call/message metadata so Android can route actionable
      // incoming calls. The target FCM token is intentionally never forwarded.
      const forwardedData = Object.fromEntries(
        Object.entries(payload)
          .filter(([key, value]) => !["token", "title", "body"].includes(key) && value !== undefined && value !== null)
          .map(([key, value]) => [key, String(value)])
      );

      // 1. Parse Firebase Service Account Key
      if (!env.FIREBASE_SERVICE_ACCOUNT) {
        return new Response(JSON.stringify({ error: "Server configuration missing: FIREBASE_SERVICE_ACCOUNT env is not set." }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
      const projectId = serviceAccount.project_id;
      const clientEmail = serviceAccount.client_email;
      const privateKeyPem = serviceAccount.private_key;

      if (!projectId || !clientEmail || !privateKeyPem) {
        return new Response(JSON.stringify({ error: "Invalid FIREBASE_SERVICE_ACCOUNT format. Must contain project_id, client_email, and private_key." }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // Authenticate the Android caller with its Firebase ID token. This prevents
      // an exposed Worker URL from becoming an unauthenticated push relay.
      const caller = await verifyFirebaseIdToken(request, projectId);
      if (!caller || (senderId && caller.sub !== senderId)) {
        return new Response(JSON.stringify({ error: "Unauthorized Firebase caller" }), {
          status: 401,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      if (path === "/sessions/register") {
        const id=String(payload.sessionId||"").replace(/[^A-Za-z0-9_-]/g,"").slice(0,100); if(!id)return jsonResponse({error:"sessionId required"},400);
        const ip=request.headers.get("CF-Connecting-IP")||""; const masked=ip.includes(":")?`${ip.split(":").slice(0,3).join(":")}::`:ip.split(".").map((x,i)=>i===3?"x":x).join("."); const cf=request.cf||{};
        const record={sessionId:id,uid:caller.sub,deviceName:String(payload.deviceName||"Android device").slice(0,120),androidVersion:String(payload.androidVersion||""),appVersion:String(payload.appVersion||""),firstSeenAt:Number(payload.firstSeenAt||Date.now()),lastSeenAt:Date.now(),active:true,maskedIp:masked,city:String(cf.city||"Unknown"),region:String(cf.region||""),country:String(cf.country||""),latitude:Number(cf.latitude||0),longitude:Number(cf.longitude||0)};
        await firestoreSet(env,`user_sessions/${caller.sub}/devices/${id}`,record); return jsonResponse({success:true,session:record});
      }
      if(path==="/sessions/list") return jsonResponse({success:true,sessions:(await firestoreList(env,`user_sessions/${caller.sub}/devices`)).sort((a,b)=>(b.lastSeenAt||0)-(a.lastSeenAt||0))});
      if(path==="/sessions/delete") { const id=String(payload.sessionId||"").replace(/[^A-Za-z0-9_-]/g,"").slice(0,100); if(!id)return jsonResponse({error:"sessionId required"},400); const existing=await firestoreGet(env,`user_sessions/${caller.sub}/devices/${id}`); if(!existing)return jsonResponse({success:true,deleted:false}); await firestoreDelete(env,`user_sessions/${caller.sub}/devices/${id}`); return jsonResponse({success:true,deleted:true,sessionId:id}); }
      if(path==="/sessions/clear") { const sessions=await firestoreList(env,`user_sessions/${caller.sub}/devices`); await Promise.all(sessions.map(x=>x.sessionId?firestoreDelete(env,`user_sessions/${caller.sub}/devices/${String(x.sessionId).replace(/[^A-Za-z0-9_-]/g,"").slice(0,100)}`):null)); return jsonResponse({success:true,deleted:sessions.length}); }
      if(path==="/sessions/revoke-all") { const auth=await updateFirebaseAccount(env,caller.sub,{validSince:String(Math.floor(Date.now()/1000))}); const sessions=await firestoreList(env,`user_sessions/${caller.sub}/devices`); await Promise.all(sessions.map(x=>firestoreSet(env,`user_sessions/${caller.sub}/devices/${x.sessionId}`,{...x,active:false,revokedAt:Date.now()}))); return jsonResponse({success:true,authRevoked:auth.ok}); }
      if(path==="/reports/create") { const id=crypto.randomUUID(); const report={id,reporterId:caller.sub,reporterEmail:caller.email||"",targetUid:String(payload.targetUid||""),category:String(payload.category||"other").slice(0,60),description:String(payload.description||"").slice(0,3000),status:"pending",createdAt:Date.now()}; await firestoreSet(env,`user_reports/${id}`,report); return jsonResponse({success:true,report}); }
      if(path==="/admin/reports/list") { if(caller.email!=="mr4425390@gmail.com")return jsonResponse({error:"Admin only"},403); return jsonResponse({success:true,reports:await firestoreList(env,"user_reports")}); }
      if(path==="/admin/user/ban"||path==="/admin/user/unban") { if(caller.email!=="mr4425390@gmail.com")return jsonResponse({error:"Admin only"},403); const uid=String(payload.uid||""); if(!uid)return jsonResponse({error:"uid required"},400); const disabled=path.endsWith("/ban"); const auth=await updateFirebaseAccount(env,uid,{disabled}); await firestoreMerge(env,`users/${uid}`,{banned:disabled,banReason:String(payload.reason||"").slice(0,500),bannedAt:disabled?Date.now():0}); return jsonResponse({success:auth.ok,disabled,upstreamStatus:auth.status},auth.ok?200:502); }

      if (path === "/ai/chat") {
        if (!env.MISTRAL_API_KEY) return jsonResponse({ error: "MISTRAL_API_KEY Worker secret is not configured" }, 503);
        const allowedModels = new Set(["mistral-small-latest", "mistral-medium-latest", "mistral-large-latest", "open-mistral-nemo"]);
        const model = allowedModels.has(String(payload.model || "")) ? String(payload.model) : "mistral-small-latest";
        const message = String(payload.message || "").trim().slice(0, 4000);
        if (!message) return jsonResponse({ error: "Message is required" }, 400);
        const memory = Array.isArray(payload.memory) ? payload.memory.slice(-20).map(item => String(item).slice(0, 1000)) : [];
        const system = `You are Convo Chat Assistant. Be concise, safe, and helpful. Never claim an app action succeeded unless the Android client confirms it. Never reveal secrets. ${String(payload.systemPrompt || "").slice(0, 1200)}`;
        const upstream = await fetch("https://api.mistral.ai/v1/chat/completions", {
          method: "POST",
          headers: { "Authorization": `Bearer ${env.MISTRAL_API_KEY}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            model,
            temperature: 0.35,
            max_tokens: 900,
            stream: payload.stream === true,
            messages: [
              { role: "system", content: system },
              ...memory.map(content => ({ role: content.startsWith("user:") ? "user" : "assistant", content: content.replace(/^(user|assistant):\s*/, "") })),
              { role: "user", content: message }
            ]
          })
        });
        if (payload.stream === true && upstream.ok) {
          return new Response(upstream.body, { status: 200, headers: { "Content-Type": "text/event-stream", "Cache-Control": "no-cache", "Access-Control-Allow-Origin": "*" } });
        }
        const result = await upstream.json();
        if (!upstream.ok) return jsonResponse({ error: result?.message || "Mistral request failed", upstreamStatus: upstream.status }, 502);
        return jsonResponse({ success: true, model, reply: result?.choices?.[0]?.message?.content || "" });
      }

      if (path === "/media/delete") {
        if (!env.MEDIA_BUCKET || typeof env.MEDIA_BUCKET.delete !== "function") return jsonResponse({ error: "MEDIA_BUCKET must be an R2 bucket binding, not a variable or secret" }, 503);
        const key = String(payload.key || "");
        if (!key.startsWith("posts/") && !key.startsWith("reels/") && !key.startsWith("thumbnails/") && !key.startsWith("updates/")) return jsonResponse({ error: "Invalid media key" }, 400);
        const object = await env.MEDIA_BUCKET.head(key);
        if (!object) return jsonResponse({ success: true, alreadyDeleted: true });
        const isAdmin = caller.email === "mr4425390@gmail.com";
        if (!isAdmin && object.customMetadata?.ownerUid !== caller.sub) return jsonResponse({ error: "Not allowed to delete this media" }, 403);
        await env.MEDIA_BUCKET.delete(key);
        return jsonResponse({ success: true, key });
      }

      if (path === "/media/reels/list" || path === "/media/reels/search") {
        if (!env.MEDIA_BUCKET || typeof env.MEDIA_BUCKET.list !== "function" || !env.R2_PUBLIC_BASE_URL) return jsonResponse({ error: "R2 media binding is not configured" }, 503);
        const limit = Math.min(Math.max(Number(payload.limit || 20), 1), 50);
        const listed = await env.MEDIA_BUCKET.list({ prefix: "reels/", limit: 100, cursor: payload.cursor, include: ["customMetadata", "httpMetadata"] });
        const query = String(payload.query || "").trim().toLowerCase();
        const now = Date.now();
        const publicBase = env.R2_PUBLIC_BASE_URL.replace(/\/$/, "");
        const items = listed.objects
          .filter(object => Number(object.customMetadata?.expiresAt || 0) > now)
          .filter(object => !query || `${object.customMetadata?.title || ""} ${object.customMetadata?.tags || ""}`.toLowerCase().includes(query))
          .slice(0, limit)
          .map(object => ({
            key: object.key,
            publicUrl: `${publicBase}/${object.key}`,
            size: object.size,
            uploaded: object.uploaded,
            contentType: object.httpMetadata?.contentType || "video/mp4",
            title: object.customMetadata?.title || "",
            tags: (object.customMetadata?.tags || "").split(",").filter(Boolean),
            expiresAt: Number(object.customMetadata?.expiresAt || 0)
          }));
        return jsonResponse({ success: true, items, cursor: listed.truncated ? listed.cursor : null });
      }

      if (path === "/turn-credentials") {
        if (!env.TURN_TOKEN_ID || !env.TURN_API_TOKEN) {
          return new Response(JSON.stringify({ error: "TURN_TOKEN_ID or TURN_API_TOKEN secret is missing" }), {
            status: 503,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }
        const turnResponse = await fetch(
          `https://rtc.live.cloudflare.com/v1/turn/keys/${env.TURN_TOKEN_ID}/credentials/generate-ice-servers`,
          {
            method: "POST",
            headers: { "Authorization": `Bearer ${env.TURN_API_TOKEN}`, "Content-Type": "application/json" },
            body: JSON.stringify({ ttl: 3600, customIdentifier: caller.sub })
          }
        );
        const turnResult = await turnResponse.json();
        return new Response(JSON.stringify(turnResult), {
          status: turnResponse.status,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Cache-Control": "no-store" }
        });
      }

      if (path.startsWith("/sfu/")) {
        if (!env.CALLS_APP_ID || !env.CALLS_APP_TOKEN) {
          return new Response(JSON.stringify({ error: "CALLS_APP_ID or CALLS_APP_TOKEN secret is missing" }), {
            status: 503,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }
        const relative = path.slice(4); // remove /sfu
        const allowed = [
          { pattern: /^\/sessions\/new$/, method: "POST" },
          { pattern: /^\/sessions\/[^/]+\/tracks\/new$/, method: "POST" },
          { pattern: /^\/sessions\/[^/]+\/renegotiate$/, method: "PUT" },
          { pattern: /^\/sessions\/[^/]+\/tracks\/close$/, method: "PUT" }
        ];
        const route = allowed.find(item => item.pattern.test(relative));
        if (!route) {
          return new Response(JSON.stringify({ error: "Unsupported SFU operation" }), {
            status: 404,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }
        const upstream = await fetch(`https://rtc.live.cloudflare.com/v1/apps/${env.CALLS_APP_ID}${relative}`, {
          method: route.method,
          headers: { "Authorization": `Bearer ${env.CALLS_APP_TOKEN}`, "Content-Type": "application/json" },
          body: JSON.stringify(payload.sfu || {})
        });
        const result = await upstream.text();
        return new Response(result, {
          status: upstream.status,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Cache-Control": "no-store" }
        });
      }

      if (!token || !title || !body) {
        return new Response(JSON.stringify({ error: "Missing required fields: token, title, body" }), {
          status: 400,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // 2. Fetch Google OAuth2 Access Token using Web Crypto API
      const accessToken = await getGoogleAccessToken(clientEmail, privateKeyPem);

      // 3. Send Notification via FCM v1 API
      const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
      
      const fcmMessage = {
        message: {
          token: token,
          // Data-only high-priority payload: Android always routes this through
          // MyFirebaseMessagingService so message/request/activity styles stay distinct.
            data: {
              title: title || "Convo Chat",
              body: body || "You have a new update",
              senderId: senderId || "",
              senderName: senderName || "",
              senderProfileUrl: senderProfileUrl || "",
              notificationType: notificationType || "message",
              targetId: targetId || "",
              sentAt: String(timestamp || Date.now()),
              ...forwardedData
            },
          android: {
            priority: "high"
          }
        }
      };

      const fcmResponse = await fetch(fcmUrl, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(fcmMessage)
      });

      const fcmResult = await fcmResponse.json();

      const fcmError = fcmResult?.error;
      return new Response(JSON.stringify({
        success: fcmResponse.ok,
        status: fcmResponse.status,
        messageId: fcmResponse.ok ? fcmResult?.name : null,
        error: fcmResponse.ok ? null : (fcmError?.message || "FCM rejected the request"),
        errorStatus: fcmError?.status || null,
        requiredPermission: fcmResponse.status === 403 ? "cloudmessaging.messages.create" : null,
        response: fcmResult
      }), {
        status: fcmResponse.status,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });

    } catch (err) {
      return new Response(JSON.stringify({ error: err.message, stack: err.stack }), {
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }
  }
};

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Cache-Control": "no-store" }
  });
}

function mimeExtension(contentType) {
  if (contentType.includes("mp4")) return "mp4";
  if (contentType.includes("webm")) return "webm";
  if (contentType.includes("png")) return "png";
  if (contentType.includes("webp")) return "webp";
  if (contentType.includes("gif")) return "gif";
  if (contentType.includes("jpeg") || contentType.includes("jpg")) return "jpg";
  return "bin";
}

async function authenticateCaller(request, env) {
  try {
    const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
    if (!account.project_id) return { ok: false, response: jsonResponse({ error: "FIREBASE_SERVICE_ACCOUNT is missing" }, 503) };
    const caller = await verifyFirebaseIdToken(request, account.project_id);
    if (!caller) return { ok: false, response: jsonResponse({ error: "Unauthorized Firebase caller" }, 401) };
    return { ok: true, caller, account };
  } catch (error) {
    return { ok: false, response: jsonResponse({ error: error.message || "Authentication failed" }, 500) };
  }
}

/** Verify a Firebase Auth ID token using Google's rotating Secure Token JWKs. */
async function verifyFirebaseIdToken(request, projectId) {
  try {
    const authorization = request.headers.get("Authorization") || "";
    if (!authorization.startsWith("Bearer ")) return null;
    const jwt = authorization.slice(7);
    const parts = jwt.split(".");
    if (parts.length !== 3) return null;

    const header = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
    const now = Math.floor(Date.now() / 1000);
    if (header.alg !== "RS256" || !header.kid || payload.aud !== projectId ||
        payload.iss !== `https://securetoken.google.com/${projectId}` ||
        payload.exp <= now || payload.iat > now + 60 || !payload.sub) return null;

    const jwksResponse = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    if (!jwksResponse.ok) return null;
    const jwks = await jwksResponse.json();
    const jwk = jwks.keys?.find(key => key.kid === header.kid);
    if (!jwk) return null;
    const cryptoKey = await crypto.subtle.importKey(
      "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
    );
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      cryptoKey,
      base64UrlToBytes(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    return valid ? payload : null;
  } catch (_) {
    return null;
  }
}

function base64UrlToBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

function decodeHtml(value) {
  return String(value || "").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
}

function escapeHtml(value) {return String(value || "").replace(/[&<>"']/g,c=>c==="&"?"&amp;":c==="<"?"&lt;":c===">"?"&gt;":c==='"'?"&quot;":"&#39;");}
function htmlResponse(m,status=200){return new Response(`<!doctype html><meta name="viewport" content="width=device-width"><body style="background:#080914;color:white;font-family:system-ui;display:grid;place-items:center;height:100vh"><h2>${escapeHtml(m)}</h2></body>`,{status,headers:{"Content-Type":"text/html; charset=utf-8","Access-Control-Allow-Origin":"*","Cache-Control":"no-store"}});}
function toFV(v){if(v==null)return{nullValue:null};if(typeof v==="string")return{stringValue:v};if(typeof v==="boolean")return{booleanValue:v};if(typeof v==="number")return Number.isInteger(v)?{integerValue:String(v)}:{doubleValue:v};if(Array.isArray(v))return{arrayValue:{values:v.map(toFV)}};if(typeof v==="object")return{mapValue:{fields:Object.fromEntries(Object.entries(v).map(([k,x])=>[k,toFV(x)]))}};return{stringValue:String(v)};}
function fromFV(v){if(!v)return null;if("stringValue"in v)return v.stringValue;if("integerValue"in v)return Number(v.integerValue);if("doubleValue"in v)return v.doubleValue;if("booleanValue"in v)return v.booleanValue;if("timestampValue"in v)return Date.parse(v.timestampValue);if("nullValue"in v)return null;if(v.arrayValue)return(v.arrayValue.values||[]).map(fromFV);if(v.mapValue)return Object.fromEntries(Object.entries(v.mapValue.fields||{}).map(([k,x])=>[k,fromFV(x)]));return null;}
async function fsAuth(env){const a=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT||"{}");return{a,t:await getGoogleAccessToken(a.client_email,a.private_key,"https://www.googleapis.com/auth/datastore")};}
async function firestoreGet(env,path){const{a,t}=await fsAuth(env);const r=await fetch(`https://firestore.googleapis.com/v1/projects/${a.project_id}/databases/(default)/documents/${path}`,{headers:{Authorization:`Bearer ${t}`}});if(r.status===404)return null;if(!r.ok)throw Error(`Firestore ${r.status}`);const d=await r.json();return Object.fromEntries(Object.entries(d.fields||{}).map(([k,v])=>[k,fromFV(v)]));}
async function firestoreSet(env,path,data){const{a,t}=await fsAuth(env);const fields=Object.fromEntries(Object.entries(data).map(([k,v])=>[k,toFV(v)]));const r=await fetch(`https://firestore.googleapis.com/v1/projects/${a.project_id}/databases/(default)/documents/${path}`,{method:"PATCH",headers:{Authorization:`Bearer ${t}`,"Content-Type":"application/json"},body:JSON.stringify({fields})});if(!r.ok)throw Error(`Firestore write ${r.status}`);return r.json();}
async function firestoreMerge(env,path,data){return firestoreSet(env,path,{...((await firestoreGet(env,path))||{}),...data});}
async function firestoreDelete(env,path){const{a,t}=await fsAuth(env);const r=await fetch(`https://firestore.googleapis.com/v1/projects/${a.project_id}/databases/(default)/documents/${path}`,{method:"DELETE",headers:{Authorization:`Bearer ${t}`}});if(r.status===404)return true;if(!r.ok)throw Error(`Firestore delete ${r.status}`);return true;}
async function firestoreList(env,path){const{a,t}=await fsAuth(env);const documents=[];let pageToken="";do{const query=new URLSearchParams({pageSize:"1000"});if(pageToken)query.set("pageToken",pageToken);const r=await fetch(`https://firestore.googleapis.com/v1/projects/${a.project_id}/databases/(default)/documents/${path}?${query}`,{headers:{Authorization:`Bearer ${t}`}});if(!r.ok)throw Error(`Firestore list ${r.status}`);const d=await r.json();documents.push(...(d.documents||[]));pageToken=String(d.nextPageToken||"");}while(pageToken);return documents.map(x=>({...Object.fromEntries(Object.entries(x.fields||{}).map(([k,v])=>[k,fromFV(v)])),__name:String(x.name||"").split("/").pop()}));}
async function sendPresenceProbes(env) {
  try {
    const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
    if (!account.project_id || !account.client_email || !account.private_key) return;
    const users = await firestoreList(env, "users");
    const tokens = [...new Set(users.map(user => String(user.fcmToken || "").trim()).filter(Boolean))];
    if (!tokens.length) return;
    const accessToken = await getGoogleAccessToken(account.client_email, account.private_key);
    const endpoint = `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`;
    const sentAt = String(Date.now());
    const results = await Promise.allSettled(tokens.map(async token => {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token,
            data: {
              title: "",
              body: "",
              notificationType: "presence_probe",
              sentAt
            },
            android: { priority: "HIGH", ttl: "60s" }
          }
        })
      });
      if (!response.ok) throw new Error(`FCM ${response.status}`);
      return response;
    }));
    const rejected = results.filter(result => result.status === "rejected").length;
    if (rejected) console.warn(`Presence probes failed at FCM: ${rejected}/${tokens.length}`);
  } catch (_) {
    // A probe failure must not prevent the scheduled stale-lease cleanup.
  }
}

async function expireStalePresence(env){
  try {
    const now = Date.now();
    const records = await firestoreList(env, "presence");
    await Promise.all(records.filter(record => {
      const leaseUntil = Number(record.onlineUntil || 0) || (Number(record.lastActive || 0) + 5 * 60 * 1000);
      return record.isOnline === true && leaseUntil <= now;
    }).map(record => {
      const uid = String(record.uid || record.__name || "").replace(/[^A-Za-z0-9_-]/g, "");
      if (!uid) return null;
      const { __name, ...presence } = record;
      return firestoreSet(env, `presence/${uid}`, {...presence, isOnline: false, offlineSince: Number(presence.offlineSince || now), leaseExpiredAt: now});
    }));
  } catch (_) {
    // Scheduled cleanup is best-effort; authenticated client heartbeats remain authoritative.
  }
}
async function updateFirebaseAccount(env,uid,changes){const a=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT||"{}");const t=await getGoogleAccessToken(a.client_email,a.private_key,"https://www.googleapis.com/auth/identitytoolkit");const r=await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${a.project_id}/accounts:update`,{method:"POST",headers:{Authorization:`Bearer ${t}`,"Content-Type":"application/json"},body:JSON.stringify({localId:uid,...changes})});return{ok:r.ok,status:r.status};}

/**
 * Signs a RS256 JWT claim and exchanges it for a Google OAuth2 access token.
 */
async function getGoogleAccessToken(clientEmail, privateKeyPem, scope = "https://www.googleapis.com/auth/firebase.messaging") {
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600; // 1 hour token validity

  const header = {
    alg: "RS256",
    typ: "JWT"
  };

  const claim = {
    iss: clientEmail,
    scope: scope,
    aud: "https://oauth2.googleapis.com/token",
    exp: exp,
    iat: iat
  };

  // Base64Url helper encoding
  const base64UrlEncode = (obj) => {
    const str = typeof obj === "string" ? obj : JSON.stringify(obj);
    const base64 = btoa(unescape(encodeURIComponent(str)));
    return base64.replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  };

  const headerEncoded = base64UrlEncode(header);
  const claimEncoded = base64UrlEncode(claim);
  const dataToSign = `${headerEncoded}.${claimEncoded}`;

  // Sign with private key using crypto.subtle
  const signature = await signRS256(dataToSign, privateKeyPem);
  const jwt = `${dataToSign}.${signature}`;

  // Request OAuth2 access token
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenResult = await tokenResponse.json();
  if (!tokenResponse.ok) {
    throw new Error(`Google OAuth error: ${JSON.stringify(tokenResult)}`);
  }

  return tokenResult.access_token;
}

/**
 * Sign data with RS256 Web Crypto API
 */
async function signRS256(data, pemKey) {
  // Extract binary PEM payload
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  
  let rawPem = pemKey.replace(/\r/g, "").replace(/\n/g, "");
  if (rawPem.includes(pemHeader)) {
    rawPem = rawPem.substring(rawPem.indexOf(pemHeader) + pemHeader.length, rawPem.indexOf(pemFooter));
  }

  // Convert Base64 back to binary array
  const binaryString = atob(rawPem);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  // Import private key in PKCS#8 format
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: { name: "SHA-256" }
    },
    false,
    ["sign"]
  );

  // Sign data
  const encoder = new TextEncoder();
  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    encoder.encode(data)
  );

  // Convert signature back to Base64Url
  const signatureBytes = new Uint8Array(signatureBuffer);
  let signatureString = "";
  for (let i = 0; i < signatureBytes.byteLength; i++) {
    signatureString += String.fromCharCode(signatureBytes[i]);
  }
  
  return btoa(signatureString)
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}
