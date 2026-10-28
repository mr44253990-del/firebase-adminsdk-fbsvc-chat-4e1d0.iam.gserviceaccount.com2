(() => {
  "use strict";
  const cfg = window.CONVO_COMPANION_CONFIG || {};
  const workerUrl = String(cfg.workerUrl || "").replace(/\/+$/, "");
  const $ = (id) => document.getElementById(id);
  const pairPanel = $("pair-panel");
  const chatPanel = $("chat-panel");
  const pairError = $("pair-error");
  const globalError = $("global-error");
  const pairCode = $("pair-code");
  const pairSubmit = $("pair-submit");
  const logoutButton = $("logout");
  const friendList = $("friend-list");
  const messageArea = $("messages");
  const messageText = $("message-text");
  const sendButton = $("send-button");
  const sendForm = $("send-form");
  const header = $("conversation-header");
  let friends = [];
  let selectedFriend = null;
  let refreshTimer = null;
  let lastRendered = "";

  const getSession = () => sessionStorage.getItem("convo_web_pair_session") || "";
  const clearSession = () => {
    sessionStorage.removeItem("convo_web_pair_session");
    sessionStorage.removeItem("convo_web_pair_expiry");
  };
  const showPair = (message = "") => {
    pairPanel.classList.remove("hidden");
    chatPanel.classList.add("hidden");
    logoutButton.classList.add("hidden");
    pairError.textContent = message;
  };
  const showChat = () => {
    pairPanel.classList.add("hidden");
    chatPanel.classList.remove("hidden");
    logoutButton.classList.remove("hidden");
  };
  const setError = (message) => { globalError.textContent = message || ""; };

  async function api(route, data = {}, session = getSession()) {
    if (!workerUrl.startsWith("https://")) throw new Error("Secure Worker URL is not configured in config.js.");
    const headers = { "Content-Type": "application/json" };
    if (session) headers.Authorization = `Bearer ${session}`;
    const response = await fetch(`${workerUrl}/web-pair/${route}`, {
      method: "POST",
      mode: "cors",
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      headers,
      body: JSON.stringify(data)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      if (response.status === 401 || response.status === 403) {
        clearSession();
        showPair("Your browser session expired or was revoked. Pair again from Android.");
      }
      throw new Error(String(payload.error || `Request failed (${response.status})`));
    }
    return payload;
  }

  function escapeText(value) { return String(value ?? ""); }
  function makeAvatar(person) {
    if (person.profileImageUrl && /^https:\/\//i.test(person.profileImageUrl)) {
      const image = document.createElement("img");
      image.className = "avatar";
      image.src = person.profileImageUrl;
      image.alt = "";
      image.loading = "lazy";
      image.referrerPolicy = "no-referrer";
      image.onerror = () => { image.removeAttribute("src"); image.alt = (person.name || "C").slice(0, 1); image.classList.add("placeholder"); };
      return image;
    }
    const label = document.createElement("span");
    label.className = "avatar placeholder";
    label.textContent = (person.name || "C").trim().slice(0, 1).toUpperCase();
    return label;
  }

  function renderFriends() {
    friendList.replaceChildren();
    friends.forEach((person) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `friend${selectedFriend?.uid === person.uid ? " selected" : ""}`;
      button.setAttribute("aria-label", `Open chat with ${escapeText(person.name)}`);
      button.append(makeAvatar(person));
      const label = document.createElement("span");
      label.className = "friend-name";
      const name = document.createElement("strong");
      name.textContent = escapeText(person.name || "Friend");
      const username = document.createElement("span");
      username.textContent = person.username ? `@${escapeText(person.username)}` : "Friend";
      label.append(name, username);
      button.append(label);
      button.addEventListener("click", () => selectFriend(person));
      friendList.append(button);
    });
    if (!friends.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state";
      empty.textContent = "No accepted friends are available to this companion.";
      friendList.append(empty);
    }
  }

  function renderHeader(person) {
    header.replaceChildren();
    header.append(makeAvatar(person));
    const label = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = escapeText(person.name || "Friend");
    const hint = document.createElement("span");
    hint.textContent = person.username ? `@${escapeText(person.username)} · private one-to-one chat` : "Private one-to-one chat";
    label.append(name, hint);
    header.append(label);
  }

  function renderMessages(messages) {
    const signature = messages.map((m) => `${m.messageId}:${m.timestamp}:${m.text}`).join("|");
    if (signature === lastRendered) return;
    lastRendered = signature;
    messageArea.replaceChildren();
    if (!messages.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state";
      empty.textContent = "No text messages in this chat yet. Send a message to start.";
      messageArea.append(empty);
      return;
    }
    messages.forEach((message) => {
      const bubble = document.createElement("div");
      bubble.className = `message${message.senderId === "__self__" ? " mine" : ""}`;
      bubble.textContent = escapeText(message.text || "");
      const time = document.createElement("span");
      time.className = "message-meta";
      const at = Number(message.timestamp || 0);
      time.textContent = at ? new Date(at).toLocaleString() : "";
      bubble.append(time);
      messageArea.append(bubble);
    });
    messageArea.scrollTop = messageArea.scrollHeight;
  }

  async function loadMessages() {
    if (!selectedFriend || !getSession()) return;
    try {
      const result = await api("messages", { peerUid: selectedFriend.uid });
      const list = (result.messages || []).map((item) => ({
        ...item,
        senderId: item.senderId === selectedFriend.uid ? selectedFriend.uid : "__self__"
      }));
      renderMessages(list);
      setError("");
    } catch (error) {
      setError(error.message);
    }
  }

  async function selectFriend(person) {
    selectedFriend = person;
    lastRendered = "";
    renderFriends();
    renderHeader(person);
    messageText.disabled = false;
    sendButton.disabled = false;
    await loadMessages();
    if (refreshTimer) clearInterval(refreshTimer);
    refreshTimer = window.setInterval(loadMessages, 8000);
    messageText.focus();
  }

  async function loadFriends() {
    const result = await api("chats", {});
    friends = result.friends || [];
    renderFriends();
    if (selectedFriend) {
      const stillFriend = friends.find((person) => person.uid === selectedFriend.uid);
      if (stillFriend) await selectFriend(stillFriend);
      else {
        selectedFriend = null;
        messageText.disabled = true;
        sendButton.disabled = true;
      }
    }
  }

  async function enterCompanion() {
    showChat();
    setError("");
    try {
      await loadFriends();
    } catch (error) {
      setError(error.message);
      if (!getSession()) showPair(error.message);
    }
  }

  $("pair-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    pairError.textContent = "";
    const code = pairCode.value.replace(/\D/g, "").slice(0, 10);
    if (!/^\d{10}$/.test(code)) { pairError.textContent = "Enter the complete 10-digit code."; return; }
    pairSubmit.disabled = true;
    pairSubmit.textContent = "Pairing…";
    try {
      const result = await api("redeem", { code }, "");
      if (!result.sessionToken || !Number(result.expiresAt)) throw new Error("The server returned an incomplete session.");
      sessionStorage.setItem("convo_web_pair_session", result.sessionToken);
      sessionStorage.setItem("convo_web_pair_expiry", String(result.expiresAt));
      pairCode.value = "";
      await enterCompanion();
    } catch (error) {
      pairError.textContent = error.message;
    } finally {
      pairSubmit.disabled = false;
      pairSubmit.textContent = "Pair this browser";
    }
  });

  $("refresh-friends").addEventListener("click", async () => {
    try { await loadFriends(); setError(""); }
    catch (error) { setError(error.message); }
  });

  sendForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedFriend || !messageText.value.trim()) return;
    const text = messageText.value.trim().slice(0, 5000);
    sendButton.disabled = true;
    try {
      await api("send", { peerUid: selectedFriend.uid, text });
      messageText.value = "";
      await loadMessages();
      setError("");
    } catch (error) {
      setError(error.message);
    } finally {
      sendButton.disabled = !selectedFriend;
      messageText.focus();
    }
  });

  messageText.addEventListener("input", () => {
    messageText.style.height = "auto";
    messageText.style.height = `${Math.min(messageText.scrollHeight, 130)}px`;
  });
  messageText.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      sendForm.requestSubmit();
    }
  });

  logoutButton.addEventListener("click", async () => {
    try { if (getSession()) await api("logout", {}); }
    catch (_) { /* Local sign-out still removes the browser's bearer token. */ }
    if (refreshTimer) clearInterval(refreshTimer);
    refreshTimer = null;
    clearSession();
    selectedFriend = null;
    friends = [];
    lastRendered = "";
    showPair("You have signed out of this browser.");
  });

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && getSession() && selectedFriend) loadMessages();
  });

  const token = getSession();
  const expiresAt = Number(sessionStorage.getItem("convo_web_pair_expiry") || 0);
  if (token && expiresAt > Date.now()) enterCompanion();
  else {
    if (token) clearSession();
    showPair("");
  }
})();
