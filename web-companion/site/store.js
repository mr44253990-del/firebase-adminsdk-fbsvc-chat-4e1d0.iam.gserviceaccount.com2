/* Convo Chat Web — local state, settings and small helpers. */
(function () {
  "use strict";

  var SETTINGS_KEY = "convo.web.settings";
  var CACHE_KEY = "convo.web.cache";

  var defaultSettings = {
    theme: "midnight",          // midnight | aurora | daylight | amoled
    density: "comfortable",     // comfortable | compact
    fontScale: 1,
    wallpaper: "aurora",        // none | aurora | mesh | dunes | graphite | photo
    wallpaperUrl: "",
    bubbleStyle: "gradient",    // gradient | glass | solid | outline
    bubbleTint: "violet",       // violet | ocean | sunset | mint | graphite
    cornerStyle: "rounded",     // rounded | soft | bubble
    animations: true,
    sendOnEnter: true,
    sendSound: true,
    notify: false,
    pushEnabled: false,
    autoLockMinutes: 15,
    lockOnHide: false,
    hidePreview: false,
    readReceipts: true,
    typingIndicator: true,
    showOnline: true,
    lastSeenVisible: true,
    mediaAutoplay: true,
    reduceMotion: false,
    downloadQuality: "original",
    language: "en",
    time24: false,
    soundsQuickReaction: "👍"
  };

  var settings = Object.assign({}, defaultSettings);

  function loadSettings() {
    try {
      var stored = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
      settings = Object.assign({}, defaultSettings, stored || {});
    } catch (_) { settings = Object.assign({}, defaultSettings); }
    applySettings();
    return settings;
  }

  function persist() {
    try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); } catch (_) {}
    applySettings();
  }

  function applySettings() {
    var root = document.documentElement;
    root.setAttribute("data-theme", settings.theme);
    root.setAttribute("data-density", settings.density);
    root.setAttribute("data-bubbles", settings.bubbleStyle);
    root.setAttribute("data-tint", settings.bubbleTint);
    root.setAttribute("data-corners", settings.cornerStyle);
    root.setAttribute("data-motion", settings.animations && !settings.reduceMotion ? "on" : "off");
    root.style.setProperty("--font-scale", String(settings.fontScale || 1));
  }

  function setSetting(key, value) {
    settings[key] = value;
    persist();
    window.dispatchEvent(new CustomEvent("convo:settings", { detail: { key: key, value: value } }));
  }

  function resetSettings() {
    settings = Object.assign({}, defaultSettings);
    persist();
    window.dispatchEvent(new CustomEvent("convo:settings", { detail: { key: "*", value: null } }));
  }

  function cacheRead() {
    try { return JSON.parse(localStorage.getItem(CACHE_KEY) || "{}") || {}; } catch (_) { return {}; }
  }
  function cacheWrite(patch) {
    try {
      var next = Object.assign(cacheRead(), patch || {});
      localStorage.setItem(CACHE_KEY, JSON.stringify(next));
      return next;
    } catch (_) { return {}; }
  }
  function cacheClear() {
    try { localStorage.removeItem(CACHE_KEY); } catch (_) {}
  }

  /* ---------------------------------------------------------------- formatting */
  function pad(n) { return n < 10 ? "0" + n : String(n); }

  function clock(ts, use24) {
    if (!ts) return "";
    var d = new Date(Number(ts));
    var h = d.getHours();
    var m = pad(d.getMinutes());
    if (use24) return pad(h) + ":" + m;
    var suffix = h >= 12 ? "pm" : "am";
    var hh = h % 12; if (hh === 0) hh = 12;
    return hh + ":" + m + " " + suffix;
  }

  function dayKey(ts) {
    var d = new Date(Number(ts));
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }

  function dayLabel(ts) {
    var d = new Date(Number(ts));
    var today = new Date();
    var yesterday = new Date(Date.now() - 86400000);
    if (dayKey(d) === dayKey(today)) return "Today";
    if (dayKey(d) === dayKey(yesterday)) return "Yesterday";
    var opts = { day: "numeric", month: "short" };
    if (d.getFullYear() !== today.getFullYear()) opts.year = "numeric";
    return d.toLocaleDateString(undefined, opts);
  }

  function relativeTime(ts) {
    if (!ts) return "";
    var diff = Date.now() - Number(ts);
    if (diff < 45000) return "now";
    if (diff < 3600000) return Math.max(1, Math.round(diff / 60000)) + "m";
    if (dayKey(ts) === dayKey(Date.now())) return clock(ts, false).replace(/\s?(am|pm)$/i, "");
    if (dayKey(ts) === dayKey(Date.now() - 86400000)) return "Yesterday";
    if (diff < 7 * 86400000) return new Date(Number(ts)).toLocaleDateString(undefined, { weekday: "short" });
    var d = new Date(Number(ts));
    return d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
  }

  function listLabel(ts) {
    if (!ts) return "";
    if (dayKey(ts) === dayKey(Date.now())) return clock(ts, settings.time24);
    if (dayKey(ts) === dayKey(Date.now() - 86400000)) return "Yesterday";
    var diff = Date.now() - Number(ts);
    if (diff < 7 * 86400000) return new Date(Number(ts)).toLocaleDateString(undefined, { weekday: "short" });
    return new Date(Number(ts)).toLocaleDateString(undefined, { day: "2-digit", month: "2-digit", year: "2-digit" });
  }

  function initials(name) {
    var parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return "?";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  function avatarColor(seed) {
    var text = String(seed || "convo");
    var hash = 0;
    for (var i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) % 360;
    return "hsl(" + hash + " 72% 58%)";
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  /** Very small markdown-ish renderer: bold, italic, strike, code, links. */
  function richText(value) {
    var html = escapeHtml(value);
    html = html.replace(/```([\s\S]+?)```/g, function (_, code) { return "<pre class=\"codeblock\">" + code + "</pre>"; });
    html = html.replace(/`([^`\n]+)`/g, "<code>$1</code>");
    html = html.replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
    html = html.replace(/(^|[\s(])\*([^*\n]+)\*(?=[\s).,!?]|$)/g, "$1<em>$2</em>");
    html = html.replace(/~~([^~\n]+)~~/g, "<del>$1</del>");
    html = html.replace(/(https?:\/\/[^\s<]+)/g, function (url) {
      return "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">" + url.replace(/^https?:\/\//, "").slice(0, 60) + "</a>";
    });
    html = html.replace(/\n/g, "<br>");
    return html;
  }

  function preview(text) {
    var value = String(text || "");
    if (value === "[Message body stored separately]") return "Message stored securely";
    value = value.replace(/https?:\/\/\S+/g, "link");
    value = value.replace(/[\r\n]+/g, " ").trim();
    return value.length > 90 ? value.slice(0, 90) + "…" : value;
  }

  function isEmojiOnly(text) {
    var value = String(text || "").trim();
    if (!value || value.length > 12) return false;
    return /^(\p{Extended_Pictographic}|\p{Emoji_Component}|\u200d|\ufe0f|\s)+$/u.test(value);
  }

  function beep(kind) {
    if (!settings.sendSound) return;
    try {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      var ctx = new Ctx();
      var gain = ctx.createGain();
      gain.connect(ctx.destination);
      var osc = ctx.createOscillator();
      osc.type = "sine";
      var base = kind === "recv" ? 520 : 760;
      osc.frequency.setValueAtTime(base, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(base * 1.35, ctx.currentTime + .09);
      gain.gain.setValueAtTime(.0001, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(.08, ctx.currentTime + .02);
      gain.gain.exponentialRampToValueAtTime(.0001, ctx.currentTime + .18);
      osc.connect(gain);
      osc.start();
      osc.stop(ctx.currentTime + .2);
      setTimeout(function () { try { ctx.close(); } catch (_) {} }, 400);
    } catch (_) {}
  }

  var emojiSet = ["👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🙏", "🥰", "🎉", "💯", "✅"];

  window.ConvoStore = {
    settings: function () { return settings; },
    loadSettings: loadSettings,
    setSetting: setSetting,
    resetSettings: resetSettings,
    defaults: defaultSettings,
    reactions: emojiSet,
    cacheRead: cacheRead,
    cacheWrite: cacheWrite,
    cacheClear: cacheClear,
    clock: clock,
    dayKey: dayKey,
    dayLabel: dayLabel,
    relativeTime: relativeTime,
    listLabel: listLabel,
    initials: initials,
    avatarColor: avatarColor,
    escapeHtml: escapeHtml,
    richText: richText,
    preview: preview,
    isEmojiOnly: isEmojiOnly,
    beep: beep
  };
})();
