/* Convo Chat Web — screens: chat list, discover, settings, profile. */
(function () {
  "use strict";
  var S = window.ConvoStore;
  var U = window.ConvoUi;

  function esc(v) { return S.escapeHtml(v); }

  function wallpaperClass() {
    var s = S.settings();
    if (s.wallpaperUrl && /^https:\/\//i.test(s.wallpaperUrl)) return "wp-photo";
    return "wp-" + (s.wallpaper || "aurora");
  }

  function chatRow(chat, state) {
    var unread = Number(chat.unread || 0);
    var typing = state.typing[chat.uid];
    var last = chat.lastMessage;
    var previewText = typing ? "" : S.preview(last ? last.text : (chat.mutual ? "Say hello" : "Start a conversation"));
    var time = S.listLabel(chat.lastAt || 0);
    var mutedIcon = chat.muted ? U.icon("bellOff") : "";
    var isGroup = chat.isGroup; return '<button type="button" class="chat-row ' + (isGroup ? "group " : "") + (unread ? "unread " : "") + (state.activePeer === chat.uid ? "active" : "") +
      '" data-chat="' + esc(chat.uid) + '" aria-label="Open chat with ' + esc(chat.name) + '">' +
      U.avatar({ name: chat.name, profileImageUrl: chat.profileImageUrl, online: chat.online }, 48) +
      '<span class="chat-row-main">' +
        '<span class="chat-row-top"><span class="chat-row-name">' + esc(chat.name) + '</span>' +
        '<span class="chat-row-time">' + esc(time) + '</span></span>' +
        '<span class="chat-row-bottom"><span class="chat-row-preview">' +
          (typing
            ? '<span class="typing-dots"><i></i><i></i><i></i></span><span style="color:var(--accent-b);font-weight:700">typing…</span>'
            : esc(previewText)) +
        '</span>' +
        (mutedIcon ? '<span class="dim" aria-label="Muted">' + mutedIcon + '</span>' : "") +
        (unread ? '<span class="badge' + (chat.muted ? " muted" : "") + '">' + (unread > 99 ? "99+" : unread) + '</span>' : "") +
        '</span>' +
      '</span>' +
    '</button>';
  }

  function activeCount(state) {
    return state.chats.filter(function (chat) { return chat.online; }).length;
  }

  /* ------------------------------------------------------------- chats list */
  function renderChatsPane(state) {
    var filter = state.chatFilter || "all";
    var query = (state.searchQuery || "").trim().toLowerCase();
    var list = state.chats.slice();
    if (query) list = list.filter(function (c) {
      return (c.name || "").toLowerCase().indexOf(query) >= 0 || (c.username || "").toLowerCase().indexOf(query) >= 0;
    });
    if (filter === "unread") list = list.filter(function (c) { return Number(c.unread || 0) > 0; });
    if (filter === "online") list = list.filter(function (c) { return c.online; });
    list.sort(function (a, b) {
      var au = Number(a.unread || 0) > 0 ? 1 : 0;
      var bu = Number(b.unread || 0) > 0 ? 1 : 0;
      if (au !== bu) return bu - au;
      return Number(b.lastAt || 0) - Number(a.lastAt || 0);
    });
    var unreadTotal = state.chats.reduce(function (sum, c) { return sum + Number(c.unread || 0); }, 0);
    var body;
    if (state.loadingChats && !state.chats.length) {
      body = U.skeleton(6, "row-sk");
    } else if (!list.length) {
      body = query
        ? U.emptyState({ icon: "search", title: "No match", body: "Nobody here matches “" + query + "”. Try another name or username.", action: "" })
        : filter === "unread"
          ? U.emptyState({ icon: "check", title: "All caught up", body: "No unread messages. Your inbox is clear." })
          : filter === "online"
            ? U.emptyState({ icon: "wifi", title: "Nobody online", body: "Your contacts are offline right now. Messages still get delivered." })
            : U.emptyState({ icon: "chats", title: "No chats yet", body: "Add friends from the Convo Chat app — every accepted friend and joined groups appear here automatically." });
    } else {
      var online = list.filter(function (c) { return c.online; });
      var offline = list.filter(function (c) { return !c.online; });
      body = "";
      if (filter === "all") {
        if (online.length) body += '<div class="list-section-label">Online now · ' + online.length + '</div>' + online.map(function (c) { return chatRow(c, state); }).join("");
        if (offline.length) body += (online.length ? '<div class="list-section-label">Chats</div>' : "") + offline.map(function (c) { return chatRow(c, state); }).join("");
      } else {
        body = list.map(function (c) { return chatRow(c, state); }).join("");
      }
    }
    return '<div class="pane-list">' +
      '<div class="pane-head">' +
        '<div class="grow"><h1>Chats</h1><div class="sub">' +
          (unreadTotal ? unreadTotal + " unread · " : "") + activeCount(state) + " online" +
        '</div></div>' +
        '<div class="pane-actions">' +
          '<button type="button" class="icon-btn" data-action="refresh" title="Refresh" aria-label="Refresh chats">' + U.icon("refresh") + '</button>' +
          '<button type="button" class="icon-btn" data-action="open-search" title="Find people" aria-label="Find people">' + U.icon("search") + '</button>' +
        '</div>' +
      '</div>' +
      '<label class="searchbox">' + U.icon("search") +
        '<input id="chat-search" type="search" placeholder="Search chats" value="' + esc(state.searchQuery || "") + '" aria-label="Search chats">' +
        (state.searchQuery ? '<button type="button" data-action="clear-search" aria-label="Clear search">' + U.icon("close") + '</button>' : "") +
      '</label>' +
      '<div class="chip-row">' +
        [["all", "All"], ["unread", "Unread"], ["online", "Online"]].map(function (pair) {
          return '<button type="button" class="chip ' + (filter === pair[0] ? "active" : "") + '" data-filter="' + pair[0] + '">' + pair[1] + '</button>';
        }).join("") +
      '</div>' +
      '<div class="list-scroll" id="chat-list">' + body + '</div>' +
    '</div>';
  }

  /* -------------------------------------------------------------- chat pane */
  function renderChatPlaceholder() {
    return '<div class="pane-main">' +
      '<div class="chat-wallpaper ' + wallpaperClass() + '" style="' +
        (S.settings().wallpaperUrl && /^https:\/\//i.test(S.settings().wallpaperUrl)
          ? "background-image:url('" + esc(S.settings().wallpaperUrl) + "')" : "") + '"></div>' +
      '<div style="flex:1;display:grid;place-items:center;position:relative;z-index:1">' +
        U.emptyState({
          icon: "chats",
          title: "Pick a conversation",
          body: "Your chats are synced from the Convo Chat app. Everything you send here shows up on your phone too."
        }) +
      '</div>' +
    '</div>';
  }

  /* --------------------------------------------------------------- discover */
  function renderSearchPane(state) {
    var results = state.searchResults || [];
    var people = state.people || [];
    var requests = state.friendRequests || [];
    var q = (state.searchQuery || "").trim();
    var body = "";

    if (requests.length) {
      body += '<div class="list-section-label">Friend requests · ' + requests.length + '</div>' +
        requests.map(function (r) {
          return '<div class="person-row">' +
            U.avatar({ name: r.name, profileImageUrl: r.profileImageUrl }, 46) +
            '<span class="person-main"><strong>' + esc(r.name) + '</strong><span>wants to be your friend</span></span>' +
            '<span class="person-actions">' +
              '<button type="button" class="btn primary small" data-action="friend-accept" data-request="' + esc(r.id) + '">' + U.icon("check") + '<span>Accept</span></button>' +
              '<button type="button" class="btn ghost small" data-action="friend-decline" data-request="' + esc(r.id) + '" aria-label="Decline">' + U.icon("close") + '</button>' +
            '</span></div>';
        }).join("");
    }

    if (!q) {
      var recents = state.chats.slice(0, 6);
      if (recents.length) body += '<div class="list-section-label">Quick access</div>' + recents.map(function (c) { return chatRow(c, state); }).join("");
      body += '<div class="discover-tip">' + U.icon("search") +
        '<div><strong>Add new friends</strong><span>Type a friend’s exact <b>@username</b> or <b>email</b> to find them on Convo Chat and send a request.</span></div></div>';
    } else {
      if (results.length) {
        body += '<div class="list-section-label">Your friends</div>' + results.map(function (person) {
          return '<button type="button" class="chat-row" data-chat="' + esc(person.uid) + '">' +
            U.avatar({ name: person.name, profileImageUrl: person.profileImageUrl, online: person.online }, 46) +
            '<span class="chat-row-main"><span class="chat-row-top"><span class="chat-row-name">' + esc(person.name) + '</span></span>' +
            '<span class="chat-row-bottom"><span class="chat-row-preview">' +
              (person.username ? "@" + esc(person.username) : (person.online ? "Online now" : "Friend")) +
            '</span></span></span>' + U.icon("send") + '</button>';
        }).join("");
      }
      if (state.peopleLoading) body += '<div class="list-section-label">People on Convo Chat</div>' + U.skeleton(2, "row-sk");
      else if (people.length) {
        body += '<div class="list-section-label">People on Convo Chat</div>' + people.map(function (p) {
          var action = p.requested
            ? '<span class="chip-soft">' + U.icon("clock") + '<span>Requested</span></span>'
            : '<button type="button" class="btn primary small" data-action="friend-add" data-uid="' + esc(p.uid) + '">' + U.icon("plus") + '<span>Add friend</span></button>';
          return '<div class="person-row">' +
            U.avatar({ name: p.name, profileImageUrl: p.profileImageUrl }, 46) +
            '<span class="person-main"><strong>' + esc(p.name) + '</strong><span>' + (p.username ? "@" + esc(p.username) : esc(p.status || "")) + '</span></span>' +
            '<span class="person-actions">' + action + '</span></div>';
        }).join("");
      }
      if (!results.length && !people.length && !state.peopleLoading) {
        body += U.emptyState({ icon: "search", title: "No one found", body: "No match for “" + q + "”. Try the exact @username or email address." });
      }
    }
    return '<div class="pane-single">' +
      '<div class="page-head"><h1>Discover</h1><p>Find friends by @username or email, and manage friend requests.</p></div>' +
      '<label class="searchbox">' + U.icon("search") +
        '<input id="people-search" type="search" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="@username or email" value="' + esc(state.searchQuery || "") + '" aria-label="Search people">' +
        (state.searchQuery ? '<button type="button" data-action="clear-search" aria-label="Clear search">' + U.icon("close") + '</button>' : "") +
      '</label>' +
      '<div class="list-scroll">' + body + '</div>' +
    '</div>';
  }

  /* --------------------------------------------------------------- settings */
  function themeCard(preview, selected) {
    return '<button type="button" class="theme-card ' + (selected ? "selected" : "") + '" data-theme-id="' + preview.id + '">' +
      '<span class="theme-preview" style="background:' + preview.bg + '">' +
        '<i style="left:8%;top:16%;width:52%;background:' + preview.inBubble + '"></i>' +
        '<i style="right:8%;bottom:22%;width:44%;background:' + preview.outBubble + '"></i>' +
      '</span><span>' + esc(preview.label) + '</span></button>';
  }

  function group(title, rowsHTML) {
    return '<div class="group"><div class="group-title">' + esc(title) + '</div>' + rowsHTML + '</div>';
  }

  function row(opts) {
    var inner = (opts.icon ? '<span class="row-ic">' + U.icon(opts.icon) + '</span>' : "") +
      '<span class="row-main"><strong>' + esc(opts.title) + '</strong>' +
      (opts.subtitle ? '<span>' + esc(opts.subtitle) + '</span>' : "") + '</span>' +
      (opts.control || (opts.value ? '<span class="row-value">' + esc(opts.value) + U.icon("back") + '</span>' : ""));
    if (opts.toggle) {
      return '<label class="row">' + inner.replace(U.icon("back"), "") +
        '<span class="switch"><input type="checkbox" data-toggle="' + esc(opts.toggle) + '" ' + (opts.checked ? "checked" : "") + '><span></span></span></label>';
    }
    if (opts.onClickAttr) return '<button type="button" class="row clickable ' + (opts.danger ? "danger" : "") + '" ' + opts.onClickAttr + '>' + inner + '</button>';
    return '<div class="row ' + (opts.danger ? "danger" : "") + '">' + inner + '</div>';
  }

  function renderSettingsPane(state, section) {
    var s = S.settings();
    var me = state.me || {};
    var html = '<div class="pane-single scroll-wrap">';
    var title = "Settings";
    var sub = "Appearance, privacy, devices and data";

    if (!section) {
      html += '<div class="page-head"><h1>Settings</h1><p>' + esc(sub) + '</p></div>' +
        '<div class="set-hero">' +
          U.avatar({ name: me.name, profileImageUrl: me.profileImageUrl }, 62) +
          '<div class="who"><h2>' + esc(me.name || "Convo user") + '</h2>' +
          '<p>' + (me.username ? "@" + esc(me.username) : esc(me.email || "Paired browser session")) + '</p></div>' +
          '<button type="button" class="btn ghost small" data-nav="profile">Edit</button>' +
        '</div>' +

        group("Your account", [
          row({ icon: "user", title: "Profile", subtitle: "Name, photo and about", onClickAttr: 'data-nav="profile"' }),
          row({ icon: "device", title: "Devices & web sessions", subtitle: "Phones and browsers using this account", value: state.devices.length ? state.devices.length + " linked" : "1 browser", onClickAttr: 'data-nav="devices"' }),
          row({ icon: "shield", title: "Privacy & security", subtitle: "Receipts, typing, online status, auto-lock", onClickAttr: 'data-nav="privacy"' })
        ].join("")) +

        group("Appearance", [
          row({ icon: "palette", title: "Theme", subtitle: "Midnight, Aurora, Daylight or AMOLED", value: s.theme, onClickAttr: 'data-nav="appearance"' }),
          row({ icon: "chats", title: "Chat bubbles", subtitle: "Bubble style, colour, corners and density", value: s.bubbleStyle, onClickAttr: 'data-nav="bubbles"' }),
          row({ icon: "image", title: "Chat wallpaper", subtitle: "Background for conversations", value: s.wallpaper, onClickAttr: 'data-nav="wallpaper"' }),
          row({ icon: "keyboard", title: "Text size & density", subtitle: "Make the interface comfortable for your eyes", onClickAttr: 'data-nav="text"' })
        ].join("")) +

        group("Notifications", [
          row({ icon: "bell", title: "Browser notifications", subtitle: "Alerts for new messages on this device", toggle: "notify", checked: s.notify }),
          row({ icon: "music", title: "Message sounds", subtitle: "Soft chime when sending and receiving", toggle: "sendSound", checked: s.sendSound }),
          row({ icon: "eye", title: "Hide message previews", subtitle: "Show “New message” instead of the text", toggle: "hidePreview", checked: s.hidePreview })
        ].join("")) +

        group("Chats", [
          row({ icon: "send", title: "Send with Enter", subtitle: "Shift + Enter starts a new line", toggle: "sendOnEnter", checked: s.sendOnEnter }),
          row({ icon: "checks", title: "Read receipts", subtitle: "Let friends know when you read a message", toggle: "readReceipts", checked: s.readReceipts }),
          row({ icon: "sparkle", title: "Typing indicator", subtitle: "Show friends when you are typing", toggle: "typingIndicator", checked: s.typingIndicator }),
          row({ icon: "wifi", title: "Show online status", subtitle: "Appear online while this browser is open", toggle: "showOnline", checked: s.showOnline }),
          row({ icon: "clock", title: "24-hour time", subtitle: "Use 18:30 instead of 6:30 pm", toggle: "time24", checked: s.time24 })
        ].join("")) +

        group("Data & support", [
          row({ icon: "download", title: "Export my chats", subtitle: "Download a JSON copy of this conversation history", onClickAttr: 'data-nav="export"' }),
          row({ icon: "trash", title: "Clear local cache", subtitle: "Remove locally cached messages from this browser", onClickAttr: 'data-nav="clearcache"' }),
          row({ icon: "info", title: "About Convo Chat Web", subtitle: "Version, publisher and safety notes", onClickAttr: 'data-nav="about"' })
        ].join("")) +

        group("Session", [
          row({ icon: "logout", title: "Log out of this browser", subtitle: "End this web session only", onClickAttr: 'data-nav="logout"', danger: false }),
          row({ icon: "lock", title: "Log out everywhere", subtitle: "Invalidate every paired browser at once", onClickAttr: 'data-nav="logoutall"', danger: true })
        ].join("")) +
        '<p class="tiny dim center" style="padding:18px 24px 6px">Convo Chat Web · this browser is signed in to your account. ' +
        'No password is stored here, and the session has no time limit — sign out whenever you like.</p>';
      return html + "</div>";
    }

    var back = '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
      '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
      '<div><h1>' + esc(title) + '</h1><p>' + esc(sub) + '</p></div></div>';

    if (section === "appearance") {
      title = "Theme"; sub = "Pick the mood of your workspace";
      var themes = [
        { id: "midnight", label: "Midnight", bg: "linear-gradient(150deg,#0a0a16,#151233)", inBubble: "rgba(255,255,255,.16)", outBubble: "linear-gradient(120deg,#7b6bff,#a86bff)" },
        { id: "aurora", label: "Aurora", bg: "linear-gradient(150deg,#061219,#0b2c31)", inBubble: "rgba(150,255,235,.18)", outBubble: "linear-gradient(120deg,#0f9f8e,#2bb9a2)" },
        { id: "daylight", label: "Daylight", bg: "linear-gradient(150deg,#f7f7ff,#e7ebfb)", inBubble: "rgba(20,22,50,.10)", outBubble: "linear-gradient(120deg,#6b5bff,#9a5bff)" },
        { id: "amoled", label: "AMOLED", bg: "#000", inBubble: "rgba(255,255,255,.16)", outBubble: "linear-gradient(120deg,#6b5bff,#8b7bff)" }
      ];
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Theme</h1><p>Pick the mood of your workspace</p></div></div>';
      html += group("Colour theme", '<div class="theme-grid">' + themes.map(function (t) { return themeCard(t, s.theme === t.id); }).join("") + '</div>');
      html += group("Comfort", [
        row({ icon: "sparkle", title: "Animations", subtitle: "Smooth transitions and message entrances", toggle: "animations", checked: s.animations }),
        row({ icon: "eye", title: "Reduce motion", subtitle: "Turn off decorative movement", toggle: "reduceMotion", checked: s.reduceMotion })
      ].join(""));
      return html + "</div>";
    }

    if (section === "bubbles") {
      title = "Chat bubbles"; sub = "Choose how your messages look";
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Chat bubbles</h1><p>Choose how your messages look</p></div></div>';
      html += '<div class="bubble-preview" data-bubble-preview>' +
        '<div class="b in">Hey! This is how a received message looks.</div>' +
        '<div class="b out">And this one is yours. Clean and readable.</div>' +
      '</div>';
      html += group("Style", '<div class="swatch-row" style="padding-top:12px">' +
        [["gradient", "Gradient"], ["glass", "Glass"], ["solid", "Solid"], ["outline", "Outline"]].map(function (pair) {
          return '<button type="button" class="chip ' + (s.bubbleStyle === pair[0] ? "active" : "") + '" data-bubble-style="' + pair[0] + '">' + pair[1] + '</button>';
        }).join("") + '</div>');
      html += group("Colour", '<div class="swatch-row">' +
        [["violet", "#7b6bff"], ["ocean", "#2f8bd6"], ["sunset", "#f2653f"], ["mint", "#17b487"], ["graphite", "#4a5064"]].map(function (pair) {
          return '<button type="button" class="swatch ' + (s.bubbleTint === pair[0] ? "selected" : "") + '" data-bubble-tint="' + pair[0] +
            '" style="background:' + pair[1] + '" aria-label="' + pair[0] + '"></button>';
        }).join("") + '</div>');
      html += group("Shape", '<div class="swatch-row" style="padding-top:12px">' +
        [["rounded", "Rounded"], ["soft", "Soft"], ["bubble", "Round"]].map(function (pair) {
          return '<button type="button" class="chip ' + (s.cornerStyle === pair[0] ? "active" : "") + '" data-corners="' + pair[0] + '">' + pair[1] + '</button>';
        }).join("") + '</div>');
      html += group("Layout", [
        row({ icon: "keyboard", title: "Compact density", subtitle: "Tighter rows, more messages on screen", toggle: "compact", checked: s.density === "compact" })
      ].join(""));
      return html + "</div>";
    }

    if (section === "wallpaper") {
      title = "Chat wallpaper"; sub = "Give your conversations personality";
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Chat wallpaper</h1><p>Give your conversations personality</p></div></div>';
      html += '<div class="group"><div class="theme-grid">' +
        [["aurora", "Aurora"], ["mesh", "Mesh"], ["dunes", "Dunes"], ["graphite", "Graphite"], ["none", "None"]].map(function (pair) {
          return '<button type="button" class="theme-card ' + (s.wallpaper === pair[0] && !s.wallpaperUrl ? "selected" : "") + '" data-wallpaper="' + pair[0] + '">' +
            '<span class="theme-preview wp-' + pair[0] + '" style="background-color:var(--panel-2)"></span>' +
            '<span>' + pair[1] + '</span></button>';
        }).join("") + '</div></div>';
      html += group("Photo wallpaper", row({
        icon: "image", title: s.wallpaperUrl ? "Change photo wallpaper" : "Use a photo",
        subtitle: "Paste a secure https image link", onClickAttr: 'data-nav="wallpaper-photo"'
      }) + (s.wallpaperUrl ? row({ icon: "trash", title: "Remove photo", subtitle: "Go back to a gradient", onClickAttr: 'data-nav="wallpaper-clear"', danger: true }) : ""));
      return html + "</div>";
    }

    if (section === "text") {
      title = "Text size & density"; sub = "Comfort for long conversations";
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Text size</h1><p>Comfort for long conversations</p></div></div>';
      html += group("Scale", '<div class="slider-row"><label class="tiny dim" for="font-range">Small</label>' +
        '<input id="font-range" type="range" min="0.85" max="1.35" step="0.05" value="' + Number(s.fontScale || 1) + '" data-setting-range="fontScale">' +
        '<output>' + Math.round((Number(s.fontScale || 1)) * 100) + '%</output></div>' +
        '<div class="slider-row"><label class="tiny dim" for="density-toggle">Density</label>' +
        '<span class="grow"></span><span class="segmented" style="flex:none">' +
          '<button type="button" data-density="comfortable" class="' + (s.density !== "compact" ? "active" : "") + '">Comfortable</button>' +
          '<button type="button" data-density="compact" class="' + (s.density === "compact" ? "active" : "") + '">Compact</button>' +
        '</span></div>');
      return html + "</div>";
    }

    if (section === "privacy") {
      title = "Privacy & security"; sub = "You decide what this browser shares";
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Privacy & security</h1><p>You decide what this browser shares</p></div></div>';
      html += group("Visibility", [
        row({ icon: "wifi", title: "Show online status", subtitle: "Friends see when this browser is active", toggle: "showOnline", checked: s.showOnline }),
        row({ icon: "eye", title: "Show last seen", subtitle: "Let friends see when you were last around", toggle: "lastSeenVisible", checked: s.lastSeenVisible }),
        row({ icon: "sparkle", title: "Typing indicator", subtitle: "Share when you are typing", toggle: "typingIndicator", checked: s.typingIndicator }),
        row({ icon: "checks", title: "Read receipts", subtitle: "Send blue double ticks", toggle: "readReceipts", checked: s.readReceipts })
      ].join(""));
      html += group("Screen lock", [
        row({ icon: "lock", title: "Lock this browser automatically", subtitle: "Ask for your phone approval after inactivity", value: s.autoLockMinutes + " min", onClickAttr: 'data-nav="autolock"' }),
        row({ icon: "shield", title: "Lock when tab is hidden", subtitle: "Require unlock after switching tabs", toggle: "lockOnHide", checked: s.lockOnHide })
      ].join(""));
      html += group("Session", row({
        icon: "info", title: "How this session works",
        subtitle: "Pairing codes are single-use. This browser never stores your password and cannot change your account."
      }));
      return html + "</div>";
    }

    if (section === "devices") {
      title = "Devices & web sessions"; sub = "Everything currently signed in to your account";
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>Devices</h1><p>Everything currently signed in to your account</p></div></div>';
      var deviceRows = (state.devices || []).map(function (device) {
        var active = device.active !== false;
        return '<div class="device-card">' +
          '<span class="row-ic">' + U.icon(String(device.deviceName || "").toLowerCase().indexOf("web") >= 0 ? "globe" : "device") + '</span>' +
          '<span class="meta"><strong>' + esc(device.deviceName || "Unknown device") + '</strong>' +
          '<span>' + esc([device.city, device.country].filter(Boolean).join(", ") || "Location unknown") +
          ' · last seen ' + esc(S.relativeTime(device.lastSeenAt)) + '</span></span>' +
          '<span class="pill ' + (active ? "" : "off") + '">' + (active ? "Active" : "Ended") + '</span>' +
        '</div>';
      }).join("");
      html += '<div class="group"><div class="group-title">Signed in</div>' +
        '<div class="device-card"><span class="row-ic">' + U.icon("globe") + '</span>' +
        '<span class="meta"><strong>This browser (Convo Chat Web)</strong>' +
        '<span>' + (window.ConvoApi.sessionExpiry() > 0
          ? 'Signed in · ends ' + esc(new Date(window.ConvoApi.sessionExpiry()).toLocaleString())
          : 'Signed in · no time limit (stays until you sign out)') + '</span></span>' +
        '<span class="pill">This device</span></div>' + deviceRows + '</div>';
      if (!(state.devices || []).length) {
        html += '<div class="group"><div class="row"><span class="row-main"><strong>No other devices reported</strong>' +
          '<span>Phones appear here once the Convo Chat app signs in.</span></span></div></div>';
      }
      html += group("Safety", row({
        icon: "alert", title: "Something looks wrong?",
        subtitle: "Log out everywhere, then change your password inside the Convo Chat app."
      }) + row({ icon: "lock", title: "Log out everywhere", subtitle: "Invalidate every planted browser session", onClickAttr: 'data-nav="logoutall"', danger: true }));
      return html + "</div>";
    }

    if (section === "about") {
      html += '<div class="page-head" style="display:flex;align-items:center;gap:8px;padding-bottom:2px">' +
        '<button type="button" class="icon-btn" data-nav="settings" aria-label="Back to settings">' + U.icon("back") + '</button>' +
        '<div><h1>About</h1><p>Convo Chat Web</p></div></div>';
      html += '<div class="group"><div class="row"><span class="row-main"><strong>Convo Chat Web</strong>' +
        '<span>Version 5.0 · the browser companion for the Convo Chat Android app.</span></span></div>' +
        row({ icon: "shield", title: "Private by design", subtitle: "No analytics, no ads, no third-party scripts. Your messages stay between your phone and the Convo gateway." }) +
        row({ icon: "wifi", title: "Connection", subtitle: "All requests are proxied through the Convo Worker over TLS." }) +
        row({ icon: "info", title: "Limits", subtitle: "Text and emoji messages are supported here. Media, calls and group chats stay in the Android app." }) +
      '</div>';
      return html + "</div>";
    }

    return html + back + "</div>";
  }

  /* ---------------------------------------------------------------- profile */
  function renderProfilePane(state) {
    var me = state.me || {};
    var s = S.settings();
    var initial = (me.name || "C").trim().charAt(0).toUpperCase();
    var joined = me.createdAt ? new Date(Number(me.createdAt)).toLocaleDateString(undefined, { month: "long", year: "numeric" }) : "";
    var stats = [
      { value: me.friends || 0, label: "Friends" },
      { value: me.followers || 0, label: "Followers" },
      { value: me.following || 0, label: "Following" }
    ].map(function (item) {
      return '<div class="pf-stat"><b>' + esc(String(item.value)) + '</b><span>' + esc(item.label) + '</span></div>';
    }).join("");
    var badges = "";
    if (me.role === "moderator") badges += '<span class="pf-badge mod">' + U.icon("check") + 'Moderator</span>';
    if (me.premium) badges += '<span class="pf-badge prem">' + U.icon("star") + 'Premium</span>';
    if (me.profileType && me.profileType !== "Personal") badges += '<span class="pf-badge">' + esc(me.profileType) + '</span>';

    return '<div class="pane-single scroll-wrap">' +
      '<div class="pf-cover" ' + (me.coverImageUrl ? 'style="background-image:url(\'' + esc(me.coverImageUrl) + '\')"' : "") + '>' +
        '<div class="pf-cover-glow"></div>' +
        '<div class="pf-cover-top"><span class="pf-chip">' + U.icon("globe") + 'Convo Web</span>' +
          '<button type="button" class="pf-edit-btn" data-action="edit-profile" aria-label="Edit profile">' + U.icon("edit") + '</button></div>' +
      '</div>' +
      '<div class="pf-head">' +
        '<button type="button" class="pf-avatar" data-action="change-avatar" aria-label="Change profile photo">' +
          U.avatar({ name: me.name, profileImageUrl: me.profileImageUrl }, 104) +
          '<span class="pf-avatar-pick">' + U.icon("camera") + '</span>' +
        '</button>' +
        '<div class="pf-ident"><h1>' + esc(me.name || "Convo user") + '</h1>' +
          '<p class="pf-handle">' + (me.username ? "@" + esc(me.username) : esc(me.email || "")) + '</p>' +
          (me.professionalTitle ? '<p class="pf-title">' + esc(me.professionalTitle) + (me.pronouns ? " · " + esc(me.pronouns) : "") + '</p>' : "") +
          (badges ? '<div class="pf-badges">' + badges + '</div>' : "") +
        '</div>' +
      '</div>' +
      (me.bio ? '<p class="pf-bio">' + esc(me.bio) + '</p>' : '<p class="pf-bio dim">Add a short bio so friends know it is you.</p>') +
      '<div class="pf-stats">' + stats + '</div>' +
      '<div class="pf-actions">' +
        '<button type="button" class="btn primary" data-action="edit-profile">' + U.icon("edit") + '<span>Edit profile</span></button>' +
        '<button type="button" class="btn ghost" data-action="share-profile">' + U.icon("share") + '<span>Share</span></button>' +
      '</div>' +
      '<div class="pf-link"><span class="pf-link-label">Public profile link</span>' +
        '<button type="button" class="pf-link-value" data-action="copy-profile-link" title="Copy link">' + U.icon("link") +
          '<span>' + esc(state.profileLink || "Loading…") + '</span>' + U.icon("copy") + '</button>' +
      '</div>' +
      group("Profile details", [
        row({ icon: "user", title: "Display name", value: me.name || "—", onClickAttr: 'data-action="edit-profile"' }),
        row({ icon: "globe", title: "Username", value: me.username ? "@" + me.username : "—" }),
        row({ icon: "info", title: "About", value: me.bio || "Add a short bio", onClickAttr: 'data-action="edit-profile"' }),
        row({ icon: "camera", title: "Profile photo", subtitle: "Change it from this browser", onClickAttr: 'data-action="change-avatar"' }),
        joined ? row({ icon: "calendar", title: "Joined", value: joined }) : ""
      ].join("")) +
      group("Notifications & privacy", [
        row({ icon: "bell", title: "Message notifications", toggle: "notify", checked: s.notify }),
        row({ icon: "eye", title: "Read receipts", toggle: "readReceipts", checked: s.readReceipts }),
        row({ icon: "shield", title: "Privacy & security", subtitle: "Presence, typing and auto-lock", onClickAttr: 'data-nav="privacy"' })
      ].join("")) +
      group("This browser", [
        row({ icon: "device", title: "Devices & sessions", subtitle: "Review where you are signed in", onClickAttr: 'data-nav="devices"' }),
        row({ icon: "download", title: "Export my data", subtitle: "Chat history as JSON", onClickAttr: 'data-nav="export"' }),
        row({ icon: "logout", title: "Log out of this browser", subtitle: "Your phone stays signed in", danger: true, onClickAttr: 'data-action="logout"' })
      ].join("")) +
      '<p class="tiny dim center" style="padding:18px 24px 10px">Changes made here update your Convo Chat account everywhere — phone and web.</p>' +
    '</div>';
  }

  /* ------------------------------------------------------------------ lock */
  function renderLockScreen(state) {
    return '<div class="pair-wrap"><div class="pair-card">' +
      '<div class="pair-art"><span class="pulse"></span><span class="pulse"></span><span class="pulse"></span>' +
      '<span class="pair-mark">' + U.icon("lock") + '</span></div>' +
      '<h1>Locked for your privacy</h1>' +
      '<p class="lead">Convo Chat Web locked itself after a few quiet minutes. Your messages are still here — unlock to continue.</p>' +
      '<button type="button" class="btn primary wide" data-action="unlock">' + U.icon("lock") + '<span>Unlock</span></button>' +
      '<p class="pair-fine">Tip: turn off auto-lock in Settings → Privacy & security on a trusted computer.</p>' +
    '</div></div>';
  }

  /* --------------------------------------------------------------- pairing */
  function isDesktop() { return window.matchMedia("(min-width: 860px)").matches; }

  function renderPairing() {
    // Desktop: show QR + tabs side-by-side. Mobile: tabbed view (QR tab included).
    var isDesk = isDesktop();
    var qrPane = '<div class="qr-pane" id="qr-pane">' +
      '<div class="qr-header"><span class="qr-badge">' + U.icon("qr") + ' QR sign-in</span><span class="tiny dim">Unavailable</span></div>' +
      '<div class="qr-status" id="qr-status"><span class="qr-dot"></span><span id="qr-status-text">QR sign-in is not enabled on this Worker. Use Pair code or Sign in instead.</span></div>' +
      '<p class="pair-fine" style="margin-top:6px">No decorative or non-functional QR is shown. QR login needs a matching secure phone-approval API.</p>' +
    '</div>';

    var tabs = '<div class="auth-tabs" role="tablist">' +
        '<button type="button" class="auth-tab active" role="tab" data-action="auth-mode" data-mode="code">Pair code</button>' +
        '<button type="button" class="auth-tab" role="tab" data-action="auth-mode" data-mode="signin">Sign in</button>' +
        '<button type="button" class="auth-tab" role="tab" data-action="auth-mode" data-mode="signup">Create account</button>' +
        (isDesk ? "" : '<button type="button" class="auth-tab" role="tab" data-action="auth-mode" data-mode="qr">QR</button>') +
      '</div>';

    var codePane = '<div class="auth-pane auth-code">' +
        '<p class="auth-hint">Open <strong>Convo Chat</strong> on your phone → <strong>Account security</strong> → <strong>Link my browser</strong>, then type the 10-digit code here.</p>' +
        '<form data-form="pair" novalidate><input class="pair-code-input" id="pair-code" inputmode="numeric" autocomplete="one-time-code" maxlength="10" pattern="[0-9]*" placeholder="0000000000" aria-label="10-digit pairing code">' +
        '<button type="button" class="btn primary wide" data-action="pair">' + U.icon("check") + '<span>Connect browser</span></button></form>' +
        '<p class="pair-error" id="pair-error" role="alert"></p>' +
      '</div>';

    var signinPane = '<div class="auth-pane auth-signin">' +
        '<form data-form="login" novalidate>' +
        '<input class="auth-input" id="login-email" type="email" autocomplete="email" placeholder="Email address" aria-label="Email address">' +
        '<input class="auth-input" id="login-password" type="password" autocomplete="current-password" placeholder="Password" aria-label="Password">' +
        '<button type="button" class="btn primary wide" data-action="email-login">' + U.icon("lock") + '<span>Sign in &amp; save this browser</span></button></form>' +
        '<p class="pair-error" id="login-error" role="alert"></p>' +
        '<p class="auth-hint">Your login is saved in this browser and stays until you sign out.</p>' +
      '</div>';

    var signupPane = '<div class="auth-pane auth-signup">' +
        '<form data-form="signup" novalidate>' +
        '<input class="auth-input" id="signup-name" type="text" autocomplete="name" placeholder="Your name" aria-label="Your name">' +
        '<input class="auth-input" id="signup-email" type="email" autocomplete="email" placeholder="Email address" aria-label="Email address">' +
        '<input class="auth-input" id="signup-password" type="password" autocomplete="new-password" placeholder="Password (min 6 characters)" aria-label="Password">' +
        '<button type="button" class="btn primary wide" data-action="email-signup">' + U.icon("check") + '<span>Create account &amp; sign in</span></button></form>' +
        '<p class="pair-error" id="signup-error" role="alert"></p>' +
        '<p class="auth-hint">Email only — no phone number needed. Use this account in the Convo Chat app too.</p>' +
      '</div>';

    // Mobile QR tab content (hidden unless mode=qr)
    var qrTabPane = '<div class="auth-pane auth-qr">' + qrPane + '</div>';

    if (isDesk) {
      // Desktop dual layout: left QR, right tabs
      return '<div class="pair-wrap pair-desktop"><div class="pair-card auth-card pair-card-wide" id="auth-card" data-auth-mode="code">' +
        '<div class="pair-art"><span class="pulse"></span><span class="pulse"></span><span class="pulse"></span>' +
        '<span class="pair-mark">' + U.icon("qr") + '</span></div>' +
        '<h1>Connect Convo Chat</h1>' +
        '<p class="lead">Scan with your phone for instant login, or use code / email.</p>' +
        '<div class="pair-desktop-grid">' +
          '<div class="pair-desktop-qr">' + qrPane + '</div>' +
          '<div class="pair-desktop-forms">' + tabs + codePane + signinPane + signupPane + '</div>' +
        '</div>' +
        '<div class="pair-steps">' +
          '<div class="pair-step"><b>1</b><span>QR expires in 30s and requires phone authentication.</span></div>' +
          '<div class="pair-step"><b>2</b><span>This browser stays signed in with no time limit — sign out whenever you like.</span></div>' +
          '<div class="pair-step"><b>3</b><span>Your phone stays in control and can revoke any browser.</span></div>' +
        '</div>' +
        '<p class="pair-fine">Convo Chat Web is a private companion: it shows accepted friend chats only. ' +
        'Media, calls and group chats stay on your phone.</p>' +
      '</div></div>';
    }

    return '<div class="pair-wrap"><div class="pair-card auth-card" id="auth-card" data-auth-mode="code">' +
      '<div class="pair-art"><span class="pulse"></span><span class="pulse"></span><span class="pulse"></span>' +
      '<span class="pair-mark">' + U.icon("qr") + '</span></div>' +
      '<h1>Connect Convo Chat</h1>' +
      '<p class="lead">Pair with your phone, or sign in with the same email you use in the app.</p>' +
      tabs +
      codePane + signinPane + signupPane + qrTabPane +
      '<div class="pair-steps">' +
        '<div class="pair-step"><b>1</b><span>Codes are single-use and expire in 90 seconds.</span></div>' +
        '<div class="pair-step"><b>2</b><span>This browser stays signed in with no time limit — sign out whenever you like.</span></div>' +
        '<div class="pair-step"><b>3</b><span>Your phone stays in control and can revoke any browser.</span></div>' +
      '</div>' +
      '<p class="pair-fine">Convo Chat Web is a private companion: it shows accepted friend chats only. ' +
      'Media, calls and group chats stay on your phone.</p>' +
    '</div></div>';
  }

  window.ConvoScreens = {
    renderChatsPane: renderChatsPane,
    renderChatPlaceholder: renderChatPlaceholder,
    renderSearchPane: renderSearchPane,
    renderSettingsPane: renderSettingsPane,
    renderProfilePane: renderProfilePane,
    renderLockScreen: renderLockScreen,
    renderPairing: renderPairing,
    wallpaperClass: wallpaperClass
  };
})();
