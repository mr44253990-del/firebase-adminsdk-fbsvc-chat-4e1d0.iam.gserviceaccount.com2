/* Convo Chat Web — shared UI primitives: icons, toasts, modals, sheets, avatars. */
(function () {
  "use strict";
  var S = window.ConvoStore;

  var paths = {
    chats: '<path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.5 9.5 0 0 1-2.9-.4L4 21l1.6-4.4A8.4 8.4 0 0 1 12.5 3a8.4 8.4 0 0 1 8.5 8.5Z"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-3.6-3.6"/>',
    settings: '<circle cx="12" cy="12" r="3.1"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    send: '<path d="M4.5 11.5 20 4l-6.4 16-2.4-6.6-6.7-1.9Z"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    check: '<path d="M5 13l4.5 4.5L19 7"/>',
    checks: '<path d="M1.5 12.5l4 4L15 7"/><path d="M8.5 12.5l4 4L22 7"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    alert: '<circle cx="12" cy="12" r="8.5"/><path d="M12 8v5M12 16.2v.1"/>',
    more: '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
    smile: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 14.2a4.2 4.2 0 0 0 7 0"/><path d="M9 9.6v.1M15 9.6v.1"/>',
    mic: '<rect x="9" y="3" width="6" height="10" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    image: '<rect x="3" y="4.5" width="18" height="15" rx="3"/><circle cx="8.6" cy="10" r="1.6"/><path d="m4 17 4.8-4.4 3.4 3 3-2.6L20 17"/>',
    file: '<path d="M14 3.5H7.5A2 2 0 0 0 5.5 5.5v13a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V8Z"/><path d="M14 3.5V8h4.5"/>',
    reply: '<path d="M9 7 4.5 11.5 9 16"/><path d="M4.5 11.5h8A6.5 6.5 0 0 1 19 18v2"/>',
    copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="2.4"/><path d="M15.5 5.5h-9a2 2 0 0 0-2 2v9"/>',
    trash: '<path d="M4.5 7h15"/><path d="M9 7V4.8h6V7"/><path d="M6.5 7l1 12.2A1.8 1.8 0 0 0 9.3 21h5.4a1.8 1.8 0 0 0 1.8-1.8L17.5 7"/>',
    moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4 5.2 18.8"/>',
    bell: '<path d="M18 8.5a6 6 0 1 0-12 0c0 6-2 7.5-2 7.5h16s-2-1.5-2-7.5Z"/><path d="M10.3 19.5a2 2 0 0 0 3.4 0"/>',
    bellOff: '<path d="M18 8.5a6 6 0 0 0-8.5-5.4M6 9.5c0 6-2 7.5-2 7.5h12"/><path d="M10.3 19.5a2 2 0 0 0 3.4 0M3 3l18 18"/>',
    lock: '<rect x="4.8" y="10.5" width="14.4" height="10" rx="2.6"/><path d="M8.2 10.5V8a3.8 3.8 0 0 1 7.6 0v2.5"/>',
    logout: '<path d="M14.5 8V5.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2V16"/><path d="M9.5 12h11M17 8.5l3.5 3.5L17 15.5"/>',
    device: '<rect x="3" y="4.5" width="13" height="10" rx="2"/><path d="M6.5 19h7"/><rect x="17" y="9" width="4.5" height="9" rx="1.4"/>',
    palette: '<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.4 0 2-1 2-1.9 0-1.6-1.6-1.7-1.6-3.2 0-1 .8-1.7 2-1.7h2.1A5 5 0 0 0 21 8.8C21 5.7 17 3.5 12 3.5Z"/><circle cx="8" cy="9.8" r="1.2"/><circle cx="12" cy="7.6" r="1.2"/><circle cx="16" cy="9.8" r="1.2"/>',
    shield: '<path d="M12 3.5 20 6v5.5c0 5-3.4 8-8 9.5-4.6-1.5-8-4.5-8-9.5V6Z"/><path d="m8.8 12 2.4 2.4 4.4-4.6"/>',
    info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11.2v5.4M12 7.8v.1"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4.5V11h-6.3"/>',
    download: '<path d="M12 4v11M7.5 11l4.5 4.5L16.5 11"/><path d="M4.5 19.5h15"/>',
    pin: '<path d="M9 4.5h6l-1 5 4 4.5H6l4-4.5-1-5Z"/><path d="M12 14v6"/>',
    star: '<path d="m12 4 2.5 5.2 5.6.8-4.1 4 1 5.6-5-2.7-5 2.7 1-5.6-4.1-4 5.6-.8Z"/>',
    music: '<circle cx="7" cy="17.5" r="2.6"/><circle cx="18" cy="15.5" r="2.6"/><path d="M9.6 17.5V7.5l11-2v10"/>',
    camera: '<rect x="3" y="6.5" width="18" height="13" rx="3"/><circle cx="12" cy="13" r="3.6"/><path d="M8.5 6.5 10 4h4l1.5 2.5"/>',
    play: '<path d="M8 5.5 18.5 12 8 18.5Z"/>',
    pause: '<path d="M9 5.5v13M15 5.5v13"/>',
    wifi: '<path d="M3.5 9.5a13 13 0 0 1 17 0M6.8 13a8.5 8.5 0 0 1 10.4 0M10 16.4a4 4 0 0 1 4 0"/><circle cx="12" cy="19.5" r=".9"/>',
    qr: '<rect x="3.5" y="3.5" width="6.5" height="6.5" rx="1.4"/><rect x="14" y="3.5" width="6.5" height="6.5" rx="1.4"/><rect x="3.5" y="14" width="6.5" height="6.5" rx="1.4"/><path d="M14 14h2.5v2.5H14zM18.5 14H21v3M14 18.5h3V21"/>',
    history: '<path d="M3.8 12a8.2 8.2 0 1 0 2.6-6"/><path d="M3.5 4.5V9H8"/><path d="M12 8v4.4l3 1.8"/>',
    keyboard: '<rect x="2.5" y="6.5" width="19" height="11" rx="2.6"/><path d="M6 10h.1M9.5 10h.1M13 10h.1M16.5 10h.1M6 13.5h.1M9.5 13.5h.1M13 13.5h.1M16.5 13.5h.1M8 16.5h8"/>',
    emoji: '<circle cx="12" cy="12" r="8.5"/><path d="M8.6 14.4a4.3 4.3 0 0 0 6.8 0"/><circle cx="9.2" cy="9.8" r=".9"/><circle cx="14.8" cy="9.8" r=".9"/>',
    gif: '<rect x="2.5" y="6" width="19" height="12" rx="3"/><path d="M8.8 10.5H7.4A1.6 1.6 0 0 0 5.8 12v1.4a1.6 1.6 0 0 0 1.6 1.6h1.4v-1.6M11.8 10.5v4.5M14.4 15V10.5h2.9M14.4 12.8h2.2"/>',
    flame: '<path d="M12 3.5c3.6 3 6 5.6 6 9a6 6 0 0 1-12 0c0-3.4 2.4-6 6-9Z"/><path d="M12 12c1.4 1.4 2 2.4 2 3.4a2 2 0 1 1-4 0c0-1 .6-2 2-3.4Z"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5a14 14 0 0 1 0 17 14 14 0 0 1 0-17Z"/>',
    forward: '<path d="M15 7l4.5 4.5L15 16"/><path d="M19.5 11.5h-8A6.5 6.5 0 0 0 5 18v2"/>',
    edit: '<path d="M15.5 5.5 18.5 8.5 9 18H6v-3Z"/><path d="M13.5 7.5 16.5 10.5"/>',
    arrowDown: '<path d="M12 5v14M6.5 13.5 12 19l5.5-5.5"/>',
    eye: '<path d="M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
    users: '<circle cx="9" cy="8.5" r="3.4"/><path d="M2.8 20a6.2 6.2 0 0 1 12.4 0"/><path d="M16 5.4a3.4 3.4 0 0 1 0 6.5M17.5 14.4A5.6 5.6 0 0 1 21.2 20"/>',
    sparkle: '<path d="M12 3.5l1.8 4.7L18.5 10l-4.7 1.8L12 16.5l-1.8-4.7L5.5 10l4.7-1.8Z"/><path d="M18.5 15.5l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9Z"/>',
    upload: '<path d="M12 20V8.5M7.5 13 12 8.5 16.5 13"/><path d="M4.5 4.5h15"/>',
    share: '<circle cx="17.5" cy="6" r="2.6"/><circle cx="6.5" cy="12" r="2.6"/><circle cx="17.5" cy="18" r="2.6"/><path d="M8.8 10.8 15.2 7.2M8.8 13.2l6.4 3.6"/>',
    link: '<path d="M10.2 13.8a4 4 0 0 0 5.7 0l2.6-2.6a4 4 0 0 0-5.7-5.7L11.6 6.7"/><path d="M13.8 10.2a4 4 0 0 0-5.7 0l-2.6 2.6a4 4 0 0 0 5.7 5.7l1.2-1.2"/>',
    calendar: '<rect x="3.5" y="5.5" width="17" height="15" rx="3"/><path d="M3.5 10h17M8.5 3.5v4M15.5 3.5v4"/>'
  };

  function icon(name, cls) {
    var body = paths[name] || paths.info;
    return '<svg class="ic ' + (cls || "") + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + body + "</svg>";
  }

  function avatar(person, size) {
    var name = (person && (person.name || person.senderName)) || "Convo user";
    var url = person && (person.profileImageUrl || person.avatarUrl);
    var online = person && person.online;
    var style = "width:" + size + "px;height:" + size + "px;font-size:" + Math.max(10, Math.round(size * 0.36)) + "px";
    var ring = online ? " avatar-online" : "";
    if (url && /^https:\/\//i.test(url)) {
      return '<span class="avatar avatar-img' + ring + '" style="' + style + '">' +
        '<img src="' + S.escapeHtml(url) + '" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">' +
        '</span>';
    }
    return '<span class="avatar' + ring + '" style="' + style + ';background:linear-gradient(145deg,' +
      S.avatarColor(name) + ',' + S.avatarColor(name + "x") + ')">' + S.escapeHtml(S.initials(name)) + '</span>';
  }

  /* --------------------------------------------------------------- toast */
  function toast(message, kind, action) {
    var host = document.getElementById("toasts");
    if (!host) return;
    var node = document.createElement("div");
    node.className = "toast toast-" + (kind || "info");
    node.innerHTML = '<span class="toast-ic">' + icon(kind === "error" ? "alert" : kind === "success" ? "check" : "info") +
      '</span><span class="toast-msg"></span>';
    node.querySelector(".toast-msg").textContent = message;
    if (action && action.label) {
      var button = document.createElement("button");
      button.className = "toast-action";
      button.textContent = action.label;
      button.addEventListener("click", function () { action.onClick(); remove(); });
      node.appendChild(button);
    }
    host.appendChild(node);
    requestAnimationFrame(function () { node.classList.add("in"); });
    var timer = setTimeout(remove, kind === "error" ? 6000 : 3400);
    function remove() {
      clearTimeout(timer);
      node.classList.remove("in");
      setTimeout(function () { node.remove(); }, 260);
    }
    node.addEventListener("click", function (e) { if (e.target === node) remove(); });
    return remove;
  }

  /* --------------------------------------------------------------- modal */
  function modal(options) {
    var host = document.getElementById("modal-host");
    host.hidden = false;
    host.innerHTML = "";
    var wrap = document.createElement("div");
    wrap.className = "modal";
    wrap.innerHTML =
      '<div class="modal-card ' + (options.wide ? "wide" : "") + '" role="dialog" aria-modal="true">' +
        '<div class="modal-head">' +
          '<div><h2 class="modal-title"></h2><p class="modal-sub"></p></div>' +
          '<button class="icon-btn modal-close" aria-label="Close">' + icon("close") + '</button>' +
        '</div>' +
        '<div class="modal-body"></div>' +
        '<div class="modal-foot"></div>' +
      '</div>';
    wrap.querySelector(".modal-title").textContent = options.title || "";
    var sub = wrap.querySelector(".modal-sub");
    if (options.subtitle) sub.textContent = options.subtitle; else sub.remove();
    var body = wrap.querySelector(".modal-body");
    if (typeof options.body === "string") body.innerHTML = options.body;
    else if (options.body) body.appendChild(options.body);
    var foot = wrap.querySelector(".modal-foot");
    (options.actions || []).forEach(function (action) {
      var button = document.createElement("button");
      button.className = "btn " + (action.variant || "ghost");
      button.innerHTML = (action.icon ? icon(action.icon) : "") + "<span>" + S.escapeHtml(action.label) + "</span>";
      button.addEventListener("click", function () {
        if (action.onClick) {
          var result = action.onClick(wrap);
          if (result === false) return;
        }
        if (action.keepOpen !== true) close();
      });
      foot.appendChild(button);
    });
    if (!(options.actions || []).length) foot.remove();
    host.appendChild(wrap);
    requestAnimationFrame(function () { wrap.classList.add("in"); });

    function close() {
      wrap.classList.remove("in");
      setTimeout(function () {
        host.hidden = true;
        host.innerHTML = "";
        if (options.onClose) options.onClose();
      }, 200);
    }
    wrap.querySelector(".modal-close").addEventListener("click", close);
    wrap.addEventListener("mousedown", function (event) {
      if (event.target === wrap && options.dismissible !== false) close();
    });
    document.addEventListener("keydown", function esc(event) {
      if (event.key === "Escape") { close(); document.removeEventListener("keydown", esc); }
    });
    return { close: close, root: wrap, body: body };
  }

  function confirm(options) {
    return new Promise(function (resolve) {
      var settled = false;
      modal({
        title: options.title,
        subtitle: options.subtitle || "",
        body: '<p class="confirm-text">' + S.escapeHtml(options.message || "") + '</p>',
        actions: [
          { label: options.cancelLabel || "Cancel", variant: "ghost", onClick: function () { settled = true; resolve(false); } },
          { label: options.confirmLabel || "Confirm", variant: options.danger ? "danger" : "primary", onClick: function () { settled = true; resolve(true); } }
        ],
        onClose: function () { if (!settled) resolve(false); }
      });
    });
  }

  function prompt(options) {
    return new Promise(function (resolve) {
      var settled = false;
      var field = document.createElement("div");
      field.className = "field";
      field.innerHTML = '<label>' + S.escapeHtml(options.label || "Value") + '</label>' +
        '<input class="input" type="' + (options.type || "text") + '" value="' + S.escapeHtml(options.value || "") + '" ' +
        (options.placeholder ? 'placeholder="' + S.escapeHtml(options.placeholder) + '"' : "") + '>' +
        (options.help ? '<p class="help">' + S.escapeHtml(options.help) + '</p>' : "");
      modal({
        title: options.title,
        subtitle: options.subtitle || "",
        body: field,
        actions: [
          { label: "Cancel", variant: "ghost", onClick: function () { settled = true; resolve(null); } },
          {
            label: options.confirmLabel || "Save", variant: "primary", onClick: function (root) {
              var input = root.querySelector(".input");
              settled = true; resolve(input ? input.value : "");
            }
          }
        ],
        onClose: function () { if (!settled) resolve(null); }
      });
      setTimeout(function () { var input = document.querySelector(".modal .input"); if (input) input.focus(); }, 120);
    });
  }

  /**
   * Multi-field form modal. Resolves with an object of values, or null if cancelled.
   * fields: [{ key, label, value, placeholder, help, type, multiline, max }]
   */
  function formModal(options) {
    return new Promise(function (resolve) {
      var settled = false;
      var body = document.createElement("div");
      body.className = "field-stack";
      (options.fields || []).forEach(function (field) {
        var row = document.createElement("div");
        row.className = "field";
        if (field.multiline) {
          row.innerHTML = '<label>' + S.escapeHtml(field.label) + '</label>' +
            '<textarea class="input" rows="' + (field.rows || 3) + '" maxlength="' + (field.max || 400) + '" ' +
            (field.placeholder ? 'placeholder="' + S.escapeHtml(field.placeholder) + '"' : "") + '></textarea>' +
            (field.help ? '<p class="help">' + S.escapeHtml(field.help) + '</p>' : "");
          row.querySelector("textarea").value = field.value || "";
        } else {
          row.innerHTML = '<label>' + S.escapeHtml(field.label) + '</label>' +
            '<input class="input" type="' + (field.type || "text") + '" maxlength="' + (field.max || 200) + '" ' +
            (field.placeholder ? 'placeholder="' + S.escapeHtml(field.placeholder) + '"' : "") + '>' +
            (field.help ? '<p class="help">' + S.escapeHtml(field.help) + '</p>' : "");
          row.querySelector("input").value = field.value || "";
        }
        row.dataset.key = field.key;
        body.appendChild(row);
      });
      modal({
        title: options.title,
        subtitle: options.subtitle || "",
        body: body,
        actions: [
          { label: "Cancel", variant: "ghost", onClick: function () { settled = true; resolve(null); } },
          {
            label: options.confirmLabel || "Save", variant: "primary", onClick: function (root) {
              var values = {};
              root.querySelectorAll(".field").forEach(function (row) {
                var input = row.querySelector("input,textarea");
                values[row.dataset.key] = input ? input.value : "";
              });
              settled = true;
              resolve(values);
            }
          }
        ],
        onClose: function () { if (!settled) resolve(null); }
      });
    });
  }

  /* --------------------------------------------------------------- sheet */
  function sheet(options) {
    var host = document.getElementById("sheet-host");
    host.hidden = false;
    host.innerHTML = "";
    var wrap = document.createElement("div");
    wrap.className = "sheet";
    wrap.innerHTML = '<div class="sheet-card" role="dialog" aria-modal="true">' +
      '<div class="sheet-grip"></div>' +
      '<div class="sheet-head"><h3></h3><button class="icon-btn sheet-close" aria-label="Close">' + icon("close") + '</button></div>' +
      '<div class="sheet-body"></div></div>';
    wrap.querySelector("h3").textContent = options.title || "";
    var body = wrap.querySelector(".sheet-body");
    if (typeof options.body === "string") body.innerHTML = options.body;
    else if (options.body) body.appendChild(options.body);
    host.appendChild(wrap);
    requestAnimationFrame(function () { wrap.classList.add("in"); });
    function close() {
      wrap.classList.remove("in");
      setTimeout(function () { host.hidden = true; host.innerHTML = ""; if (options.onClose) options.onClose(); }, 220);
    }
    wrap.querySelector(".sheet-close").addEventListener("click", close);
    wrap.addEventListener("mousedown", function (event) { if (event.target === wrap) close(); });
    return { close: close, body: body };
  }

  function sheetMenu(items, title) {
    var list = document.createElement("div");
    list.className = "menu-list";
    items.forEach(function (item) {
      if (item.separator) { var sep = document.createElement("div"); sep.className = "menu-sep"; list.appendChild(sep); return; }
      var button = document.createElement("button");
      button.className = "menu-item" + (item.danger ? " danger" : "");
      button.innerHTML = icon(item.icon || "info") + '<span>' + S.escapeHtml(item.label) + '</span>' +
        (item.hint ? '<em>' + S.escapeHtml(item.hint) + '</em>' : "");
      button.addEventListener("click", function () { handle.close(); if (item.onClick) setTimeout(item.onClick, 60); });
      list.appendChild(button);
    });
    var handle = sheet({ title: title || "", body: list });
    return handle;
  }

  /* ------------------------------------------------------------ utility */
  function fmtSize(bytes) {
    var value = Number(bytes || 0);
    if (!value) return "";
    var units = ["B", "KB", "MB", "GB"];
    var index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
    return (value < 10 && index > 0 ? value.toFixed(1) : Math.round(value)) + " " + units[index];
  }

  function skeleton(count, cls) {
    var out = "";
    for (var i = 0; i < count; i++) out += '<div class="skeleton ' + (cls || "") + '"></div>';
    return out;
  }

  function emptyState(opts) {
    return '<div class="empty">' +
      '<div class="empty-art">' + icon(opts.icon || "chats") + '</div>' +
      '<h3>' + S.escapeHtml(opts.title || "") + '</h3>' +
      '<p>' + S.escapeHtml(opts.body || "") + '</p>' +
      (opts.action ? '<button class="btn primary" data-empty-action>' + S.escapeHtml(opts.action) + '</button>' : "") +
      '</div>';
  }

  window.ConvoUi = {
    icon: icon,
    avatar: avatar,
    toast: toast,
    modal: modal,
    confirm: confirm,
    prompt: prompt,
    formModal: formModal,
    sheet: sheet,
    sheetMenu: sheetMenu,
    fmtSize: fmtSize,
    skeleton: skeleton,
    emptyState: emptyState
  };
})();
