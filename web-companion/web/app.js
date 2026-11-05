(() => {
  "use strict";

  const cfg = window.CONVO_COMPANION_CONFIG || {};
  const workerUrl = String(cfg.workerUrl || "").replace(/\/+$/, "");
  const $ = (id) => document.getElementById(id);

  // Core DOM Elements
  const splashScreen = $("splash-screen");
  const splashProgressBar = $("splash-progress-bar");
  const splashStatus = $("splash-status");

  const authPanel = $("auth-panel");
  const tabCodeBtn = $("tab-code-btn");
  const tabLoginBtn = $("tab-login-btn");
  const tabCodePanel = $("tab-code-panel");
  const tabLoginPanel = $("tab-login-panel");
  const pairForm = $("pair-form");
  const pairCode = $("pair-code");
  const pairRemember = $("pair-remember");
  const pairSubmit = $("pair-submit");
  const emailLoginForm = $("email-login-form");
  const loginEmail = $("login-email");
  const loginPassword = $("login-password");
  const loginRemember = $("login-remember");
  const togglePasswordBtn = $("toggle-password-btn");
  const authError = $("auth-error");

  const chatWorkspace = $("chat-workspace");
  const chatSidebar = $("chat-sidebar");
  const friendListEl = $("friend-list");
  const friendSearch = $("friend-search");
  const clearSearchBtn = $("clear-search-btn");
  const myNameEl = $("my-name");
  const myUsernameEl = $("my-username");
  const myAvatarImg = $("my-avatar-img");
  const myAvatarFallback = $("my-avatar-fallback");
  const themeToggleBtn = $("theme-toggle-btn");
  const openSettingsBtn = $("open-settings-btn");
  const logoutBtn = $("logout-btn");

  const noChatState = $("no-chat-state");
  const activeChatContainer = $("active-chat-container");
  const backToListBtn = $("back-to-list-btn");
  const peerNameEl = $("peer-name");
  const peerStatusEl = $("peer-status");
  const peerAvatarImg = $("peer-avatar-img");
  const peerAvatarFallback = $("peer-avatar-fallback");
  const refreshChatBtn = $("refresh-chat-btn");
  const messagesContainer = $("messages-container");

  const chatForm = $("chat-form");
  const chatInput = $("chat-input");
  const chatSendBtn = $("chat-send-btn");
  const toggleEmojiBtn = $("toggle-emoji-btn");
  const emojiPicker = $("emoji-picker");
  const fileInput = $("file-input");
  const replyPreviewBar = $("reply-preview-bar");
  const replyToUser = $("reply-to-user");
  const replyToSnippet = $("reply-to-snippet");
  const cancelReplyBtn = $("cancel-reply-btn");
  const attachmentPreviewBar = $("attachment-preview-bar");
  const attachmentImgPreview = $("attachment-img-preview");
  const attachmentFileIcon = $("attachment-file-icon");
  const attachmentName = $("attachment-name");
  const attachmentSize = $("attachment-size");
  const cancelAttachmentBtn = $("cancel-attachment-btn");

  const settingsModal = $("settings-modal");
  const closeSettingsBtn = $("close-settings-btn");
  const settingsProfileImg = $("settings-profile-img");
  const settingsProfileFallback = $("settings-profile-fallback");
  const settingsProfileName = $("settings-profile-name");
  const settingsProfileUser = $("settings-profile-user");
  const settingsProfileBio = $("settings-profile-bio");
  const backupProfileBtn = $("backup-profile-btn");
  const backupStatusPill = $("backup-status-pill");
  const backupStatusText = $("backup-status-text");

  const activeSessionsList = $("active-sessions-list");
  const refreshSessionsBtn = $("refresh-sessions-btn");
  const revokeAllSessionsBtn = $("revoke-all-sessions-btn");
  const themeSelector = $("theme-selector");
  const cloudflareDeployForm = $("cloudflare-deploy-form");
  const cfTokenInput = $("cf-token");
  const cfAccountIdInput = $("cf-account-id");
  const cfProjectNameInput = $("cf-project-name");
  const deployResultBox = $("deploy-result-box");
  const deployUrlInput = $("deploy-url-input");
  const copyDeployUrlBtn = $("copy-deploy-url-btn");

  const lightboxModal = $("lightbox-modal");
  const lightboxImg = $("lightbox-img");
  const closeLightboxBtn = $("close-lightbox-btn");

  // Application State
  let myProfile = null;
  let friends = [];
  let selectedFriend = null;
  let activeMessages = [];
  let activeReply = null;
  let stagedAttachment = null; // { file, name, size, type, dataUrl }
  let refreshTimer = null;
  let isSending = false;

  // Session Management
  const getSessionToken = () => {
    return sessionStorage.getItem("convo_web_token") || localStorage.getItem("convo_web_token") || "";
  };

  const saveSession = (token, remember = true) => {
    sessionStorage.setItem("convo_web_token", token);
    if (remember) localStorage.setItem("convo_web_token", token);
  };

  const clearSession = () => {
    sessionStorage.removeItem("convo_web_token");
    localStorage.removeItem("convo_web_token");
    sessionStorage.removeItem("convo_web_profile");
    localStorage.removeItem("convo_web_profile");
  };

  const getSavedProfile = () => {
    const raw = sessionStorage.getItem("convo_web_profile") || localStorage.getItem("convo_web_profile");
    try { return raw ? JSON.parse(raw) : null; } catch (_) { return null; }
  };

  const saveProfile = (p, remember = true) => {
    myProfile = p;
    const str = JSON.stringify(p);
    sessionStorage.setItem("convo_web_profile", str);
    if (remember) localStorage.setItem("convo_web_profile", str);
  };

  // Sound Controller
  const playSound = (kind) => {
    if (!$("sounds-enabled")?.checked) return;
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      if (kind === "sent") {
        osc.frequency.setValueAtTime(580, ctx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.08);
        gain.gain.setValueAtTime(0.12, ctx.currentTime);
        gain.gain.linearRampToValueAtTime(0.01, ctx.currentTime + 0.09);
        osc.start();
        osc.stop(ctx.currentTime + 0.1);
      } else if (kind === "recv") {
        osc.frequency.setValueAtTime(800, ctx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(600, ctx.currentTime + 0.12);
        gain.gain.setValueAtTime(0.15, ctx.currentTime);
        gain.gain.linearRampToValueAtTime(0.01, ctx.currentTime + 0.14);
        osc.start();
        osc.stop(ctx.currentTime + 0.15);
      }
    } catch (_) {}
  };

  // API Client
  async function api(route, data = {}, token = getSessionToken()) {
    if (!workerUrl.startsWith("https://") && !workerUrl.startsWith("http://")) {
      throw new Error("Worker URL is not configured in config.js");
    }
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(`${workerUrl}/web-pair/${route}`, {
      method: "POST",
      mode: "cors",
      cache: "no-store",
      headers,
      body: JSON.stringify(data)
    });

    const payload = await res.json().catch(() => ({}));
    if (!res.ok || payload.success === false) {
      if (res.status === 401 || res.status === 403) {
        clearSession();
        showAuthScreen("Your browser session expired or was revoked. Please sign in or pair again.");
      }
      throw new Error(String(payload.error || `Request failed (${res.status})`));
    }
    return payload;
  }

  // UI Navigation Views
  function showAuthScreen(errorMsg = "") {
    authPanel.classList.remove("hidden");
    chatWorkspace.classList.add("hidden");
    if (refreshTimer) clearInterval(refreshTimer);
    if (errorMsg) {
      authError.textContent = errorMsg;
      authError.classList.remove("hidden");
    } else {
      authError.classList.add("hidden");
    }
  }

  function showChatWorkspace() {
    authPanel.classList.add("hidden");
    chatWorkspace.classList.remove("hidden");
    updateMyProfileUI();
    loadFriends();
    if (refreshTimer) clearInterval(refreshTimer);
    refreshTimer = setInterval(() => {
      if (selectedFriend) loadMessages(selectedFriend.uid, true);
    }, 3500);
  }

  function updateMyProfileUI() {
    if (!myProfile) myProfile = getSavedProfile() || { name: "Convo User", username: "user" };
    const name = myProfile.name || "Convo User";
    const username = myProfile.username ? `@${myProfile.username}` : "";
    myNameEl.textContent = name;
    myUsernameEl.textContent = username;
    settingsProfileName.textContent = name;
    settingsProfileUser.textContent = username;
    settingsProfileBio.textContent = myProfile.bio || "Secure verified messaging";

    if (myProfile.profileImageUrl && /^https?:\/\//i.test(myProfile.profileImageUrl)) {
      myAvatarImg.src = myProfile.profileImageUrl;
      myAvatarImg.classList.remove("hidden");
      myAvatarFallback.classList.add("hidden");
      settingsProfileImg.src = myProfile.profileImageUrl;
      settingsProfileImg.classList.remove("hidden");
      settingsProfileFallback.classList.add("hidden");
    } else {
      myAvatarImg.classList.add("hidden");
      myAvatarFallback.classList.remove("hidden");
      myAvatarFallback.textContent = name.slice(0, 1).toUpperCase();
      settingsProfileImg.classList.add("hidden");
      settingsProfileFallback.classList.remove("hidden");
      settingsProfileFallback.textContent = name.slice(0, 1).toUpperCase();
    }
  }

  // Startup & Splash Transition
  async function startup() {
    splashProgressBar.style.width = "40%";
    
    // Check for single-sign-on (SSO) URL parameter: ?autologin=<token>
    const urlParams = new URLSearchParams(window.location.search);
    const autoLoginToken = urlParams.get("autologin");
    if (autoLoginToken) {
      splashStatus.textContent = "Connecting instantly from your phone…";
      splashProgressBar.style.width = "75%";
      try {
        const payload = await api("redeem-auto-login", { token: autoLoginToken }, "");
        saveSession(payload.sessionToken, true);
        if (payload.profile) saveProfile(payload.profile, true);
        // Clear param from URL cleanly
        window.history.replaceState({}, document.title, window.location.pathname);
        splashProgressBar.style.width = "100%";
        setTimeout(() => {
          dismissSplash();
          showChatWorkspace();
        }, 300);
        return;
      } catch (err) {
        console.error("Auto-login failed:", err);
      }
    }

    // Standard session check
    const existingToken = getSessionToken();
    if (existingToken) {
      splashStatus.textContent = "Resuming secure session…";
      splashProgressBar.style.width = "85%";
      try {
        const profilePayload = await api("profile", {}).catch(() => null);
        if (profilePayload && profilePayload.profile) {
          saveProfile(profilePayload.profile);
        }
        splashProgressBar.style.width = "100%";
        setTimeout(() => {
          dismissSplash();
          showChatWorkspace();
        }, 280);
        return;
      } catch (_) {
        clearSession();
      }
    }

    // No session: show auth panel quickly
    splashProgressBar.style.width = "100%";
    setTimeout(() => {
      dismissSplash();
      showAuthScreen();
    }, 350);
  }

  function dismissSplash() {
    splashScreen.classList.add("hidden");
  }

  // Auth Tabs & Handlers
  tabCodeBtn.addEventListener("click", () => {
    tabCodeBtn.classList.add("active");
    tabLoginBtn.classList.remove("active");
    tabCodePanel.classList.remove("hidden");
    tabLoginPanel.classList.add("hidden");
    authError.classList.add("hidden");
  });

  tabLoginBtn.addEventListener("click", () => {
    tabLoginBtn.classList.add("active");
    tabCodeBtn.classList.remove("active");
    tabLoginPanel.classList.remove("hidden");
    tabCodePanel.classList.add("hidden");
    authError.classList.add("hidden");
  });

  togglePasswordBtn.addEventListener("click", () => {
    const isPass = loginPassword.type === "password";
    loginPassword.type = isPass ? "text" : "password";
    togglePasswordBtn.textContent = isPass ? "🙈" : "👁";
  });

  pairForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = pairCode.value.replace(/\D/g, "");
    if (code.length !== 10) {
      authError.textContent = "Please enter all 10 digits of the code.";
      authError.classList.remove("hidden");
      return;
    }
    pairSubmit.disabled = true;
    pairSubmit.querySelector(".btn-spinner").classList.remove("hidden");
    authError.classList.add("hidden");
    try {
      const payload = await api("redeem", { code }, "");
      saveSession(payload.sessionToken, pairRemember.checked);
      if (payload.profile) saveProfile(payload.profile, pairRemember.checked);
      showChatWorkspace();
    } catch (err) {
      authError.textContent = err.message || "Pairing failed. Check code and try again.";
      authError.classList.remove("hidden");
    } finally {
      pairSubmit.disabled = false;
      pairSubmit.querySelector(".btn-spinner").classList.add("hidden");
    }
  });

  emailLoginForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = loginEmail.value.trim();
    const password = loginPassword.value;
    const submitBtn = $("login-submit");
    submitBtn.disabled = true;
    submitBtn.querySelector(".btn-spinner").classList.remove("hidden");
    authError.classList.add("hidden");
    try {
      const payload = await api("login", { email, password }, "");
      saveSession(payload.sessionToken, loginRemember.checked);
      if (payload.profile) saveProfile(payload.profile, loginRemember.checked);
      showChatWorkspace();
    } catch (err) {
      authError.textContent = err.message || "Login failed. Verify credentials.";
      authError.classList.remove("hidden");
    } finally {
      submitBtn.disabled = false;
      submitBtn.querySelector(".btn-spinner").classList.add("hidden");
    }
  });

  // Friend / Contact List
  async function loadFriends() {
    try {
      const payload = await api("chats");
      friends = Array.isArray(payload.friends) ? payload.friends : [];
      renderFriendList(friends);
    } catch (err) {
      console.error("Could not load friends:", err);
    }
  }

  function renderFriendList(list) {
    friendListEl.replaceChildren();
    if (!list.length) {
      const empty = document.createElement("div");
      empty.className = "fineprint";
      empty.style.padding = "20px";
      empty.style.textAlign = "center";
      empty.textContent = "No accepted contacts yet. Connect on your mobile app.";
      friendListEl.append(empty);
      return;
    }

    list.forEach((person) => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = `friend-item${selectedFriend?.uid === person.uid ? " active" : ""}`;
      
      const avWrapper = document.createElement("div");
      avWrapper.className = "avatar-wrapper";
      if (person.profileImageUrl && /^https?:\/\//i.test(person.profileImageUrl)) {
        const img = document.createElement("img");
        img.className = "avatar";
        img.src = person.profileImageUrl;
        img.alt = person.name || "";
        avWrapper.append(img);
      } else {
        const fall = document.createElement("span");
        fall.className = "avatar placeholder";
        fall.textContent = (person.name || "C").slice(0, 1).toUpperCase();
        avWrapper.append(fall);
      }
      const dot = document.createElement("span");
      dot.className = "status-dot online";
      avWrapper.append(dot);

      const content = document.createElement("div");
      content.className = "friend-content";
      
      const row = document.createElement("div");
      row.className = "friend-row";
      const name = document.createElement("strong");
      name.textContent = person.name || "Friend";
      const time = document.createElement("span");
      time.className = "friend-time";
      time.textContent = person.lastTime || "";
      row.append(name, time);

      const snippet = document.createElement("span");
      snippet.className = "friend-snippet";
      snippet.textContent = person.username ? `@${person.username}` : "Tap to open chat";

      content.append(row, snippet);
      item.append(avWrapper, content);

      item.addEventListener("click", () => selectFriend(person));
      friendListEl.append(item);
    });
  }

  friendSearch.addEventListener("input", () => {
    const q = friendSearch.value.trim().toLowerCase();
    clearSearchBtn.classList.toggle("hidden", !q);
    if (!q) {
      renderFriendList(friends);
      return;
    }
    const filtered = friends.filter(f => 
      (f.name && f.name.toLowerCase().includes(q)) || 
      (f.username && f.username.toLowerCase().includes(q))
    );
    renderFriendList(filtered);
  });

  clearSearchBtn.addEventListener("click", () => {
    friendSearch.value = "";
    clearSearchBtn.classList.add("hidden");
    renderFriendList(friends);
  });

  // Conversation Management
  function selectFriend(person) {
    selectedFriend = person;
    noChatState.classList.add("hidden");
    activeChatContainer.classList.remove("hidden");
    chatWorkspace.classList.add("in-chat"); // Trigger mobile slide

    peerNameEl.textContent = person.name || "Friend";
    peerStatusEl.textContent = "Online";

    if (person.profileImageUrl && /^https?:\/\//i.test(person.profileImageUrl)) {
      peerAvatarImg.src = person.profileImageUrl;
      peerAvatarImg.classList.remove("hidden");
      peerAvatarFallback.classList.add("hidden");
    } else {
      peerAvatarImg.classList.add("hidden");
      peerAvatarFallback.classList.remove("hidden");
      peerAvatarFallback.textContent = (person.name || "C").slice(0, 1).toUpperCase();
    }

    renderFriendList(friends);
    loadMessages(person.uid, false);
    chatInput.focus();
  }

  backToListBtn.addEventListener("click", () => {
    chatWorkspace.classList.remove("in-chat");
  });

  refreshChatBtn.addEventListener("click", () => {
    if (selectedFriend) loadMessages(selectedFriend.uid, false);
  });

  async function loadMessages(peerUid, silent = false) {
    try {
      const payload = await api("messages", { peerUid });
      const msgs = Array.isArray(payload.messages) ? payload.messages : [];
      if (!silent || JSON.stringify(msgs) !== JSON.stringify(activeMessages)) {
        const isNewIncoming = silent && msgs.length > activeMessages.length && msgs[msgs.length - 1]?.senderId === peerUid;
        activeMessages = msgs;
        renderMessages(msgs);
        if (isNewIncoming) playSound("recv");
      }
    } catch (err) {
      if (!silent) console.error("Message load error:", err);
    }
  }

  function formatTime(timestamp) {
    if (!timestamp) return "";
    const d = new Date(timestamp);
    return d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }

  function renderMessages(messages) {
    messagesContainer.replaceChildren();

    if (!messages.length) {
      const empty = document.createElement("div");
      empty.className = "date-divider";
      empty.innerHTML = "<span>No messages yet. Say hi! 👋</span>";
      messagesContainer.append(empty);
      return;
    }

    let lastDate = "";
    messages.forEach((msg) => {
      const d = new Date(msg.timestamp || Date.now());
      const dateStr = d.toLocaleDateString([], { month: "short", day: "numeric" });
      if (dateStr !== lastDate) {
        lastDate = dateStr;
        const div = document.createElement("div");
        div.className = "date-divider";
        div.innerHTML = `<span>${dateStr}</span>`;
        messagesContainer.append(div);
      }

      const isMine = msg.senderId === myProfile?.uid || (!myProfile?.uid && msg.senderId !== selectedFriend?.uid);
      const row = document.createElement("div");
      row.className = `msg-row ${isMine ? "sent" : "recv"}`;

      const bubble = document.createElement("div");
      bubble.className = "msg-bubble";

      // Sender name on incoming messages
      if (!isMine && msg.senderName) {
        const sender = document.createElement("span");
        sender.className = "msg-sender-name";
        sender.textContent = msg.senderName;
        bubble.append(sender);
      }

      // Inline Reply Quote
      if (msg.replyToText) {
        const quote = document.createElement("div");
        quote.className = "msg-reply-quote";
        quote.innerHTML = `<strong>${escapeHtml(msg.replyToSenderName || "User")}</strong><p>${escapeHtml(msg.replyToText)}</p>`;
        bubble.append(quote);
      }

      // Image Attachment
      if (msg.imageUrl) {
        const img = document.createElement("img");
        img.className = "msg-image-thumb";
        img.src = msg.imageUrl;
        img.alt = "Photo";
        img.loading = "lazy";
        img.addEventListener("click", () => openLightbox(msg.imageUrl));
        bubble.append(img);
      }

      // File / Document Attachment
      if (msg.fileUrl) {
        const fileCard = document.createElement("a");
        fileCard.className = "msg-file-card";
        fileCard.href = msg.fileUrl;
        fileCard.download = msg.fileName || "document";
        fileCard.target = "_blank";
        fileCard.rel = "noopener noreferrer";

        const icon = document.createElement("span");
        icon.className = "msg-file-icon";
        icon.textContent = "📄";

        const info = document.createElement("div");
        info.className = "msg-file-info";
        const fname = document.createElement("strong");
        fname.textContent = msg.fileName || "Document";
        const fsize = document.createElement("span");
        fsize.textContent = msg.fileSize ? `${Math.round(msg.fileSize / 1024)} KB` : "Click to download";
        info.append(fname, fsize);

        const dl = document.createElement("span");
        dl.className = "download-icon";
        dl.textContent = "⬇️";

        fileCard.append(icon, info, dl);
        bubble.append(fileCard);
      }

      // Text Body
      if (msg.text) {
        const bodyText = document.createElement("div");
        bodyText.className = "msg-text";
        bodyText.textContent = msg.text;
        bubble.append(bodyText);
      }

      // Meta: Time & Delivery Status Ticks
      const meta = document.createElement("div");
      meta.className = "msg-meta";
      const timeSpan = document.createElement("span");
      timeSpan.textContent = formatTime(msg.timestamp);
      meta.append(timeSpan);

      if (isMine) {
        const tick = document.createElement("span");
        tick.className = `tick-icon${msg.seenByRecipient ? " tick-seen" : ""}`;
        tick.textContent = msg.seenByRecipient ? "✓✓" : (msg.deliveredToRecipient ? "✓✓" : "✓");
        meta.append(tick);
      }

      bubble.append(meta);
      row.append(bubble);

      // Emoji Reactions Display
      if (msg.reactions && Object.keys(msg.reactions).length) {
        const reactBar = document.createElement("div");
        reactBar.className = "msg-reactions";
        const counts = {};
        Object.values(msg.reactions).forEach(e => counts[e] = (counts[e] || 0) + 1);
        Object.entries(counts).forEach(([emoji, count]) => {
          const chip = document.createElement("button");
          chip.type = "button";
          chip.className = "reaction-chip";
          chip.textContent = count > 1 ? `${emoji} ${count}` : emoji;
          chip.addEventListener("click", () => sendReaction(msg.messageId, emoji));
          reactBar.append(chip);
        });
        row.append(reactBar);
      }

      // Context click / hover to reply
      bubble.addEventListener("contextmenu", (e) => {
        e.preventDefault();
        setReply(msg);
      });

      messagesContainer.append(row);
    });

    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text || "";
    return div.innerHTML;
  }

  // Reactions
  async function sendReaction(messageId, emoji) {
    if (!selectedFriend) return;
    try {
      await api("react", { peerUid: selectedFriend.uid, messageId, emoji });
      loadMessages(selectedFriend.uid, true);
    } catch (err) {
      console.error("Reaction failed:", err);
    }
  }

  // Reply Handling
  function setReply(msg) {
    activeReply = msg;
    replyToUser.textContent = `Replying to ${msg.senderName || "User"}`;
    replyToSnippet.textContent = msg.text || (msg.imageUrl ? "Photo" : "Attachment");
    replyPreviewBar.classList.remove("hidden");
    chatInput.focus();
  }

  cancelReplyBtn.addEventListener("click", () => {
    activeReply = null;
    replyPreviewBar.classList.add("hidden");
  });

  // Attachments Handling
  fileInput.addEventListener("change", (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      stagedAttachment = {
        file,
        name: file.name,
        size: file.size,
        type: file.type,
        dataUrl: event.target.result
      };
      attachmentName.textContent = file.name;
      attachmentSize.textContent = `${Math.round(file.size / 1024)} KB`;
      if (file.type.startsWith("image/")) {
        attachmentImgPreview.src = event.target.result;
        attachmentImgPreview.classList.remove("hidden");
        attachmentFileIcon.classList.add("hidden");
      } else {
        attachmentImgPreview.classList.add("hidden");
        attachmentFileIcon.classList.remove("hidden");
      }
      attachmentPreviewBar.classList.remove("hidden");
      chatSendBtn.disabled = false;
    };
    reader.readAsDataURL(file);
  });

  cancelAttachmentBtn.addEventListener("click", () => {
    stagedAttachment = null;
    fileInput.value = "";
    attachmentPreviewBar.classList.add("hidden");
    chatSendBtn.disabled = !chatInput.value.trim();
  });

  // Send Message
  chatInput.addEventListener("input", () => {
    chatInput.style.height = "auto";
    chatInput.style.height = `${Math.min(chatInput.scrollHeight, 120)}px`;
    chatSendBtn.disabled = !chatInput.value.trim() && !stagedAttachment;
  });

  chatInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (!chatSendBtn.disabled) chatForm.requestSubmit();
    }
  });

  chatForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!selectedFriend || isSending) return;
    const text = chatInput.value.trim();
    if (!text && !stagedAttachment) return;

    isSending = true;
    chatSendBtn.disabled = true;

    const payload = {
      peerUid: selectedFriend.uid,
      text
    };

    if (activeReply) {
      payload.replyToId = activeReply.messageId;
      payload.replyToText = activeReply.text || "Attachment";
      payload.replyToSenderName = activeReply.senderName || "User";
    }

    if (stagedAttachment) {
      if (stagedAttachment.type.startsWith("image/")) {
        payload.imageUrl = stagedAttachment.dataUrl;
      } else {
        payload.fileUrl = stagedAttachment.dataUrl;
        payload.fileName = stagedAttachment.name;
        payload.fileSize = stagedAttachment.size;
      }
    }

    try {
      await api("send", payload);
      chatInput.value = "";
      chatInput.style.height = "auto";
      stagedAttachment = null;
      activeReply = null;
      fileInput.value = "";
      replyPreviewBar.classList.add("hidden");
      attachmentPreviewBar.classList.add("hidden");
      playSound("sent");
      loadMessages(selectedFriend.uid, false);
    } catch (err) {
      alert("Failed to send: " + (err.message || "Unknown error"));
    } finally {
      isSending = false;
      chatSendBtn.disabled = !chatInput.value.trim();
    }
  });

  // Quick Emoji Reactions Popover
  toggleEmojiBtn.addEventListener("click", () => {
    emojiPicker.classList.toggle("hidden");
  });

  document.querySelectorAll(".reaction-item").forEach(btn => {
    btn.addEventListener("click", () => {
      const emoji = btn.getAttribute("data-emoji");
      chatInput.value += emoji;
      chatInput.dispatchEvent(new Event("input"));
      emojiPicker.classList.add("hidden");
      chatInput.focus();
    });
  });

  // Settings Modal & Tabs
  openSettingsBtn.addEventListener("click", () => {
    settingsModal.classList.remove("hidden");
    loadActiveSessions();
  });

  $("open-profile-btn").addEventListener("click", () => {
    settingsModal.classList.remove("hidden");
  });

  closeSettingsBtn.addEventListener("click", () => {
    settingsModal.classList.add("hidden");
  });

  settingsModal.addEventListener("click", (e) => {
    if (e.target === settingsModal) settingsModal.classList.add("hidden");
  });

  document.querySelectorAll(".modal-tab").forEach(tab => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".modal-tab").forEach(t => t.classList.remove("active"));
      document.querySelectorAll(".settings-tab-content").forEach(c => c.classList.add("hidden"));
      tab.classList.add("active");
      const target = $(`settings-tab-${tab.getAttribute("data-tab")}`);
      if (target) target.classList.remove("hidden");
    });
  });

  // Profile Screenshot Cloud Backup
  backupProfileBtn.addEventListener("click", async () => {
    backupProfileBtn.disabled = true;
    backupProfileBtn.querySelector(".btn-spinner").classList.remove("hidden");
    try {
      // Capture a simple screenshot snapshot
      const target = $("profile-screenshot-target");
      const snapshotText = `[Profile Backup] Name: ${myProfile?.name || ""} User: ${myProfile?.username || ""} Time: ${new Date().toISOString()}`;
      const payload = await api("profile-backup", { save: true, screenshotData: snapshotText });
      backupStatusPill.classList.remove("hidden");
      backupStatusText.textContent = `Backed up to Cloud • ${new Date().toLocaleTimeString()}`;
    } catch (err) {
      alert("Cloud backup failed: " + err.message);
    } finally {
      backupProfileBtn.disabled = false;
      backupProfileBtn.querySelector(".btn-spinner").classList.add("hidden");
    }
  });

  // Active Sessions Management ("কে কে লগইন আছে")
  async function loadActiveSessions() {
    activeSessionsList.replaceChildren();
    const loading = document.createElement("div");
    loading.className = "session-item";
    loading.textContent = "Loading web sessions…";
    activeSessionsList.append(loading);

    try {
      const payload = await api("active-sessions");
      activeSessionsList.replaceChildren();
      const sessions = Array.isArray(payload.sessions) ? payload.sessions : [];
      if (!sessions.length) {
        const item = document.createElement("div");
        item.className = "session-item";
        item.textContent = "Only this active browser session.";
        activeSessionsList.append(item);
        return;
      }

      sessions.forEach(s => {
        const row = document.createElement("div");
        row.className = "session-item";

        const meta = document.createElement("div");
        meta.className = "session-device-meta";
        const title = document.createElement("strong");
        title.textContent = `${s.summary || s.browser || "Web Browser"}`;
        if (s.isCurrent) {
          const tag = document.createElement("span");
          tag.className = "current-device-tag";
          tag.textContent = " (This Browser)";
          title.append(tag);
        }
        const sub = document.createElement("span");
        const dateStr = s.createdAt ? new Date(s.createdAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "";
        sub.textContent = `${s.ip ? `IP: ${s.ip} • ` : ""}Connected: ${dateStr}`;
        meta.append(title, sub);

        const actions = document.createElement("div");
        if (!s.isCurrent) {
          const revokeBtn = document.createElement("button");
          revokeBtn.className = "btn danger-btn";
          revokeBtn.style.padding = "6px 12px";
          revokeBtn.style.fontSize = "12px";
          revokeBtn.textContent = "Revoke";
          revokeBtn.addEventListener("click", () => revokeSingleSession(s.sessionHash));
          actions.append(revokeBtn);
        }

        row.append(meta, actions);
        activeSessionsList.append(row);
      });
    } catch (err) {
      activeSessionsList.replaceChildren();
      const errEl = document.createElement("div");
      errEl.className = "session-item";
      errEl.textContent = "Could not load sessions: " + err.message;
      activeSessionsList.append(errEl);
    }
  }

  async function revokeSingleSession(sessionHash) {
    if (!confirm("Revoke this web session?")) return;
    try {
      await api("revoke-session", { sessionHash });
      loadActiveSessions();
    } catch (err) {
      alert("Could not revoke session: " + err.message);
    }
  }

  refreshSessionsBtn.addEventListener("click", loadActiveSessions);

  revokeAllSessionsBtn.addEventListener("click", async () => {
    if (!confirm("Revoke every other web session? You will stay logged in on this browser.")) return;
    try {
      await api("revoke-all");
      loadActiveSessions();
    } catch (err) {
      alert("Failed: " + err.message);
    }
  });

  // Themes & Appearance
  themeSelector.addEventListener("change", (e) => {
    setTheme(e.target.value);
  });

  function setTheme(t) {
    document.body.className = `theme-${t}`;
    localStorage.setItem("convo_web_theme", t);
    themeSelector.value = t;
  }

  themeToggleBtn.addEventListener("click", () => {
    const current = localStorage.getItem("convo_web_theme") || "cyber";
    const next = current === "cyber" ? "aurora" : current === "aurora" ? "amoled" : "cyber";
    setTheme(next);
  });

  document.querySelectorAll(".palette-swatch").forEach(swatch => {
    swatch.addEventListener("click", () => {
      document.querySelectorAll(".palette-swatch").forEach(s => s.classList.remove("active"));
      swatch.classList.add("active");
      const accent = swatch.getAttribute("data-bubble");
      document.body.setAttribute("data-bubble", accent);
      localStorage.setItem("convo_web_bubble", accent);
    });
  });

  // Load Saved Appearance
  const savedTheme = localStorage.getItem("convo_web_theme") || "cyber";
  setTheme(savedTheme);
  const savedBubble = localStorage.getItem("convo_web_bubble");
  if (savedBubble) {
    document.body.setAttribute("data-bubble", savedBubble);
    document.querySelectorAll(".palette-swatch").forEach(s => {
      s.classList.toggle("active", s.getAttribute("data-bubble") === savedBubble);
    });
  }

  // Cloudflare Pages Deployment Form
  cloudflareDeployForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const token = cfTokenInput.value.trim();
    const accountId = cfAccountIdInput.value.trim();
    const projName = cfProjectNameInput.value.trim() || "convo-chat-companion";

    const submitBtn = $("cf-deploy-submit");
    submitBtn.disabled = true;
    submitBtn.querySelector(".btn-spinner").classList.remove("hidden");

    try {
      const res = await fetch(`${workerUrl}/admin/web-deploy`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          cfApiToken: token,
          cfAccountId: accountId,
          projectName: projName
        })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || data.success === false) {
        throw new Error(data.error || "Deployment failed");
      }
      deployResultBox.classList.remove("hidden");
      deployUrlInput.value = data.url || `https://${projName}.pages.dev`;
    } catch (err) {
      alert("Deployment: " + err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.querySelector(".btn-spinner").classList.add("hidden");
    }
  });

  copyDeployUrlBtn.addEventListener("click", () => {
    deployUrlInput.select();
    navigator.clipboard.writeText(deployUrlInput.value);
    copyDeployUrlBtn.textContent = "Copied!";
    setTimeout(() => { copyDeployUrlBtn.textContent = "Copy"; }, 1800);
  });

  // Lightbox
  function openLightbox(src) {
    lightboxImg.src = src;
    lightboxModal.classList.remove("hidden");
  }

  closeLightboxBtn.addEventListener("click", () => {
    lightboxModal.classList.add("hidden");
  });

  lightboxModal.addEventListener("click", (e) => {
    if (e.target === lightboxModal) lightboxModal.classList.add("hidden");
  });

  // Logout
  logoutBtn.addEventListener("click", async () => {
    if (!confirm("Are you sure you want to log out of Convo Web?")) return;
    try { await api("logout"); } catch (_) {}
    clearSession();
    showAuthScreen("You have been signed out.");
  });

  // Start Application
  window.addEventListener("DOMContentLoaded", startup);
})();
