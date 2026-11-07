/* Convo Chat Web — API client.
 * Everything goes through the Convo Cloudflare Worker with an opaque, short-lived
 * web session token issued by the Android pairing flow. No Firebase key, no cookie,
 * no third-party origin. The Worker enforces friendship, blocking and account state. */
(function () {
  "use strict";

  var WORKER = "https://convo-chat-companion.mr4425390.workers.dev";
  var SESSION_KEY = "convo.web.session";

  function readSession() {
    try {
      var raw = localStorage.getItem(SESSION_KEY);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (!parsed || !parsed.token) return null;
      // expiresAt === 0 (or missing) means the session has no time limit.
      if (Number(parsed.expiresAt) > 0 && Date.now() > Number(parsed.expiresAt)) return null;
      return parsed;
    } catch (_) { return null; }
  }

  function saveSession(session) {
    try { localStorage.setItem(SESSION_KEY, JSON.stringify(session)); } catch (_) {}
  }

  function clearSession() {
    try { localStorage.removeItem(SESSION_KEY); } catch (_) {}
  }

  /** Fired when the Worker rejects the stored token so the UI can return to pairing. */
  function onExpired() {
    clearSession();
    window.dispatchEvent(new CustomEvent("convo:session-expired"));
  }

  function friendlyError(status, payload) {
    var message = (payload && (payload.error || payload.message)) || "";
    if (status === 401 || status === 403) return message || "This browser session is not valid any more.";
    if (status === 429) return message || "Slow down a moment and try again.";
    if (status >= 500) return message || "The Convo service is busy. Try again in a few seconds.";
    return message || "Request failed (" + status + ").";
  }

  async function request(route, body, options) {
    options = options || {};
    var session = options.token ? { token: options.token } : readSession();
    var headers = { "Content-Type": "application/json" };
    if (session && session.token) headers.Authorization = "Bearer " + session.token;
    var response;
    try {
      response = await fetch(WORKER + "/web-pair/" + route, {
        method: "POST",
        mode: "cors",
        cache: "no-store",
        credentials: "omit",
        referrerPolicy: "no-referrer",
        headers: headers,
        body: JSON.stringify(body || {})
      });
    } catch (networkError) {
      throw new Error("Cannot reach Convo Chat servers. Check your connection.");
    }
    var payload = {};
    try { payload = await response.json(); } catch (_) { payload = {}; }
    if (!response.ok || payload.success === false) {
      if (response.status === 401 && !options.token) onExpired();
      var error = new Error(friendlyError(response.status, payload));
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }

  // Lightweight upload helper for unlimited file sharing (R2 via Worker)
  async function uploadRequest(route, formData) {
    var session = readSession();
    var headers = {};
    if (session && session.token) headers.Authorization = "Bearer " + session.token;
    var response;
    try {
      response = await fetch(WORKER + "/web-pair/" + route, {
        method: "POST",
        mode: "cors",
        cache: "no-store",
        credentials: "omit",
        referrerPolicy: "no-referrer",
        headers: headers,
        body: formData
      });
    } catch (e) {
      throw new Error("Cannot reach Convo Chat servers. Check your connection.");
    }
    var payload = {};
    try { payload = await response.json(); } catch (_) { payload = {}; }
    if (!response.ok || payload.success === false) {
      if (response.status === 401) onExpired();
      var error = new Error(friendlyError(response.status, payload));
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }

  var Api = {
    workerUrl: WORKER,
    session: readSession,
    saveSession: saveSession,
    clearSession: clearSession,
    hasSession: function () { return Boolean(readSession()); },
    sessionExpiry: function () { var s = readSession(); return s ? Number(s.expiresAt) : 0; },

    /** Create a new account with email + password only (same accounts as the app). */
    async signup(name, email, password) {
      var payload = await request("signup", {
        name: String(name || "").trim(),
        email: String(email || "").trim(),
        password: String(password || "")
      }, { token: "none" });
      var session = {
        token: payload.sessionToken,
        expiresAt: Number(payload.expiresAt || 0),
        pairedAt: Date.now(),
        via: "email",
        email: String(email || "").trim(),
        name: String(name || "").trim()
      };
      saveSession(session);
      return session;
    },

    /** Exchange the 10-digit code shown in the Android app for a browser session. */
    async redeem(code) {
      var payload = await request("redeem", { code: String(code || "").replace(/\D/g, "") }, { token: "none" });
      var session = {
        token: payload.sessionToken,
        expiresAt: Number(payload.expiresAt || 0),
        pairedAt: Date.now(),
        via: "pair"
      };
      saveSession(session);
      return session;
    },

    /** Sign in with the same email + password as the Android app. The login is saved in
     *  this browser (localStorage) so it stays signed in with no time limit. */
    async login(email, password) {
      var payload = await request("login", {
        email: String(email || "").trim(),
        password: String(password || "")
      }, { token: "none" });
      var session = {
        token: payload.sessionToken,
        expiresAt: Number(payload.expiresAt || 0),
        pairedAt: Date.now(),
        via: "email",
        email: String(email || "").trim()
      };
      saveSession(session);
      return session;
    },

    // ── QR Device Login (v4.6.0) ──────────────────────────────────────────
    // Desktop generates a QR that the phone scans. Phone writes auth to Firestore,
    // web polls until sessionToken appears.
    qrCreate() { return request("qr/create", {}, { token: "none" }); },
    qrPoll(qrId) { return request("qr/poll", { qrId: qrId }, { token: "none" }); },
    // The poll returns { status: "pending"|"scanned"|"authenticated"|"expired", sessionToken?, expiresAt? }
    // When authenticated, web saves session automatically:
    async qrPollAndSave(qrId) {
      var res = await request("qr/poll", { qrId: qrId }, { token: "none" });
      if (res.status === "authenticated" && res.sessionToken) {
        var session = {
          token: res.sessionToken,
          expiresAt: Number(res.expiresAt || 0),
          pairedAt: Date.now(),
          via: "qr"
        };
        saveSession(session);
      }
      return res;
    },

    // ── File upload (unlimited, chunked via Worker → R2) ─────────────────
    async uploadFile(file, onProgress) {
      // Try Worker R2 path first (no MB limit). Falls back to base64 for tiny files.
      var fd = new FormData();
      fd.append("file", file, file.name);
      fd.append("fileName", file.name);
      fd.append("mimeType", file.type || "application/octet-stream");
      // Upload with progress via XHR wrapper
      return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open("POST", WORKER + "/web-pair/upload");
        var s = readSession();
        if (s && s.token) xhr.setRequestHeader("Authorization", "Bearer " + s.token);
        else xhr.setRequestHeader("Authorization", "Bearer none");
        if (onProgress) xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(Math.round(e.loaded * 100 / e.total)); };
        xhr.onload = function () {
          try {
            var payload = JSON.parse(xhr.responseText || "{}");
            if (xhr.status >= 200 && xhr.status < 300 && payload.success !== false) resolve(payload);
            else {
              var msg = (payload && (payload.error || payload.message)) || "Upload failed (" + xhr.status + ")";
              var err = new Error(msg); err.status = xhr.status; err.payload = payload; reject(err);
              if (xhr.status === 401) onExpired();
            }
          } catch (e) { reject(new Error("Upload failed: invalid server response")); }
        };
        xhr.onerror = function () { reject(new Error("Cannot reach Convo Chat servers. Check your connection.")); };
        xhr.send(fd);
      });
    },

    // Preview helper — asks Worker for a signed preview URL (if R2 object requires auth)
    async filePreview(fileId) { return request("file/preview", { fileId: fileId }); },

    bootstrap() { return request("bootstrap"); },
    chats() { return request("chats"); },
    messages(peerUid, before) { return request("messages", { peerUid: peerUid, before: before || 0 }); },
    send(peerUid, text, extra) {
      return request("send", Object.assign({ peerUid: peerUid, text: text }, extra || {}));
    },
    react(peerUid, messageId, reaction) { return request("react", { peerUid: peerUid, messageId: messageId, reaction: reaction || "" }); },
    read(peerUid, upTo) { return request("read", { peerUid: peerUid, upTo: upTo || 0 }); },
    typing(peerUid) { return request("typing", { peerUid: peerUid }); },
    presence() { return request("presence"); },
    profile() { return request("profile"); },
    find(query) { return request("find", { query: String(query || "") }); },
    friendRequests() { return request("friend-requests"); },
    friendRequest(targetUid) { return request("friend-request", { targetUid: targetUid }); },
    friendRespond(requestId, accept) { return request("friend-respond", { requestId: requestId, accept: accept === true }); },
    saveProfile(patch) { return request("profile/save", patch); },
    uploadAvatar(dataUrl) { return request("profile/avatar", { image: dataUrl }); },
    profileLink() { return request("profile/link"); },
    settings() { return request("settings"); },
    saveSettings(patch) { return request("settings/save", patch); },
    devices() { return request("devices"); },
    logout() { return request("logout").catch(function () { return {}; }); },
    logoutEverywhere() { return request("logout-all"); },
    exportData() { return request("export"); }
  };

  window.ConvoApi = Api;
})();
