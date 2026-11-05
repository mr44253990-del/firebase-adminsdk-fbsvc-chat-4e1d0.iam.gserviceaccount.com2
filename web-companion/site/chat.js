/* Convo Chat Web — the conversation view: header, message stream, composer. */
(function () {
  "use strict";
  var S = window.ConvoStore;
  var U = window.ConvoUi;

  function esc(v) { return S.escapeHtml(v); }

  function wallpaperLayer() {
    var s = S.settings();
    var hasPhoto = s.wallpaperUrl && /^https:\/\//i.test(s.wallpaperUrl);
    return '<div class="chat-wallpaper ' + (hasPhoto ? "wp-photo" : "wp-" + (s.wallpaper || "aurora")) + '" style="' +
      (hasPhoto ? "background-image:url('" + esc(s.wallpaperUrl) + "')" : "") + '"></div>';
  }

  function statusLine(peer, state) {
    if (state.typing[peer.uid]) return '<span style="color:var(--accent-b);font-weight:700">typing…</span>';
    var s = S.settings();
    if (peer.online) return '<span class="online-dot"></span> Online now';
    if (!s.lastSeenVisible) return "Offline";
    return peer.lastActive ? "Last seen " + S.relativeTime(peer.lastActive) : "Offline";
  }

  function mediaFor(message) {
    var out = "";
    var img = message.imageUrl || "";
    if (img && /^https:\/\//i.test(img)) {
      out += '<img class="media" src="' + esc(img) + '" alt="Photo attachment" loading="lazy" decoding="async" ' +
        'referrerpolicy="no-referrer" data-zoom="' + esc(img) + '">';
    }
    if (message.fileUrl) {
      var name = message.fileName || "Attachment";
      out += '<a class="file-card" href="' + esc(message.fileUrl) + '" target="_blank" rel="noopener noreferrer" download>' +
        '<span class="file-ic">' + U.icon(/^image\//.test(message.fileMimeType || "") ? "image" : "file") + '</span>' +
        '<span class="file-meta"><strong>' + esc(name) + '</strong>' +
        '<span>' + esc(U.fmtSize(message.fileSize) || "Open file") + '</span></span>' +
        '<span class="file-dl">' + U.icon("download") + '</span></a>';
    }
    if (message.voiceUrl) {
      out += '<div class="file-card"><span class="file-ic">' + U.icon("mic") + '</span>' +
        '<span class="file-meta"><strong>Voice message</strong><span>' + (message.voiceDurationSec ? message.voiceDurationSec + "s" : "Recorded on phone") + '</span></span>' +
        '<a class="file-dl" href="' + esc(message.voiceUrl) + '" target="_blank" rel="noopener noreferrer" aria-label="Open voice message">' + U.icon("play") + '</a></div>';
    }
    return out;
  }

  function tickMarkup(message, mine) {
    if (!mine) return "";
    if (message.pending) return U.icon("clock", "tick-sent");
    if (message.failed) return U.icon("alert", "tick-sent");
    var seen = message.seenByRecipient || message.read;
    if (seen) return U.icon("checks", "tick-read");
    if (message.deliveredToRecipient) return U.icon("checks", "tick-sent");
    return U.icon("check", "tick-sent");
  }

  function bubbleBody(message) {
    var text = String(message.text || "");
    if (text === "[Message body stored separately]") {
      return '<span class="dim" style="font-style:italic">Message stored securely in the cloud store</span>';
    }
    if (S.isEmojiOnly(text)) return '<span class="emoji-only-text">' + esc(text) + '</span>';
    return S.richText(text);
  }

  function messageItem(message, state, prev, next) {
    var mine = message.senderId === state.me.uid;
    var chain = prev && prev.senderId === message.senderId && (message.timestamp - prev.timestamp) < 120000;
    var lastInChain = !next || next.senderId !== message.senderId || (next.timestamp - message.timestamp) > 120000;
    var emojiOnly = S.isEmojiOnly(message.text) && !message.imageUrl && !message.fileUrl && !message.voiceUrl;
    var reactions = message.reactions || {};
    var grouped = {};
    Object.keys(reactions).forEach(function (uid) {
      var value = reactions[uid];
      grouped[value] = (grouped[value] || 0) + 1;
    });
    var myReaction = reactions[state.me.uid];
    var reactionHTML = "";
    if (Object.keys(grouped).length) {
      reactionHTML = '<div class="reactions">' + Object.keys(grouped).map(function (key) {
        return '<button class="reaction-pill ' + (myReaction === key ? "mine" : "") + '" data-react="' + esc(key) +
          '" data-msg="' + esc(message.messageId) + '" aria-label="React ' + esc(key) + '">' + esc(key) +
          (grouped[key] > 1 ? '<em>' + grouped[key] + '</em>' : "") + '</button>';
      }).join("") + '</div>';
    }
    var media = mediaFor(message);
    var metaTime = S.clock(message.timestamp, S.settings().time24);
    var edited = message.edited ? '<span class="edited-tag">edited</span>' : "";
    return '<div class="msg ' + (mine ? "out" : "in") + (chain ? " chain" : "") + (lastInChain ? " last" : "") +
      '" data-msg="' + esc(message.messageId) + '">' +
      '<div class="bubble-wrap msg-body">' +
        (!mine && !chain ? '<div class="msg-name">' + esc(message.senderName || "Friend") + '</div>' : "") +
        '<div class="bubble' + (emojiOnly && !media ? " emoji-only" : "") + '" data-bubble="' + esc(message.messageId) + '" tabindex="0" role="article">' +
          media + (message.text ? bubbleBody(message) : "") +
          '<div class="bubble-meta">' + edited + '<span>' + esc(metaTime) + '</span>' + tickMarkup(message, mine) + '</div>' +
        '</div>' +
        reactionHTML +
      '</div>' +
    '</div>';
  }

  function renderStream(state) {
    var messages = state.messages || [];
    if (state.loadingMessages && !messages.length) {
      return '<div class="msg-scroll" id="msg-scroll">' +
        '<div class="msg in"><div class="msg-body"><div class="bubble"><div class="skeleton line" style="width:180px"></div>' +
        '<div class="skeleton line" style="width:120px;margin-top:6px"></div></div></div></div>' +
        '<div class="msg out"><div class="msg-body"><div class="bubble"><div class="skeleton line" style="width:140px"></div></div></div></div>' +
        '<div class="msg in"><div class="msg-body"><div class="bubble"><div class="skeleton line" style="width:200px"></div></div></div></div>' +
        '</div>';
    }
    if (!messages.length) {
      return '<div class="msg-scroll" id="msg-scroll">' +
        U.emptyState({ icon: "chats", title: "Say hello", body: "This is the beginning of your conversation. Messages sent here also appear in the Convo Chat app." }) +
        '</div>';
    }
    var out = "";
    var lastDay = "";
    messages.forEach(function (message, index) {
      var day = S.dayKey(message.timestamp);
      if (day !== lastDay) {
        out += '<div class="daysep">' + esc(S.dayLabel(message.timestamp)) + '</div>';
        lastDay = day;
      }
      out += messageItem(message, state, messages[index - 1], messages[index + 1]);
    });
    if (state.typing[state.activePeer]) {
      out += '<div class="msg in last"><div class="msg-body"><div class="bubble typing-bubble" aria-label="Friend is typing"><i></i><i></i><i></i></div></div></div>';
    }
    if (state.hasMoreMessages) {
      out = '<div class="center" style="padding:8px"><button class="btn ghost small" data-action="load-older">' + U.icon("history") + '<span>Load earlier messages</span></button></div>' + out;
    }
    return '<div class="msg-scroll" id="msg-scroll">' + out + '</div>';
  }

  function renderComposer(state) {
    var draft = state.drafts[state.activePeer] || "";
    var reply = state.replyTo;
    var replyHTML = "";
    if (reply) {
      replyHTML = '<div class="composer-reply">' +
        '<div><strong>Replying to ' + esc(reply.senderName || "message") + '</strong>' +
        '<span>' + esc(S.preview(reply.text)) + '</span></div>' +
        '<button class="icon-btn small" data-action="cancel-reply" aria-label="Cancel reply">' + U.icon("close") + '</button></div>';
    }
    return '<div class="composer">' + replyHTML +
      '<div class="composer-row">' +
        '<div class="composer-field">' +
          '<button class="icon-btn small" data-action="emoji" aria-label="Emoji" aria-haspopup="true">' + U.icon("emoji") + '</button>' +
          '<textarea id="composer-input" rows="1" placeholder="Write a message…" aria-label="Message" maxlength="5000">' + esc(draft) + '</textarea>' +
          '<button class="icon-btn small" data-action="attach" aria-label="Attach a link or photo" aria-haspopup="true">' + U.icon("plus") + '</button>' +
        '</div>' +
        '<button class="send-btn" data-action="send" aria-label="Send message">' + U.icon("send") + '</button>' +
      '</div>' +
      '<div class="composer-hint">' + (S.settings().sendOnEnter ? "Enter sends · Shift + Enter for a new line" : "Shift + Enter sends") + '</div>' +
    '</div>';
  }

  function renderChatPane(state) {
    var peer = state.chats.filter(function (c) { return c.uid === state.activePeer; })[0] ||
      (state.me && state.me.uid === state.activePeer ? null : null);
    if (!peer) {
      peer = { uid: state.activePeer, name: state.activePeerName || "Chat", profileImageUrl: state.activePeerImage || "" };
    }
    return '<div class="pane-main">' +
      wallpaperLayer() +
      '<div class="chat-head">' +
        '<button class="icon-btn" data-action="back" aria-label="Back to chats" style="display:none" id="chat-back">' + U.icon("back") + '</button>' +
        U.avatar({ name: peer.name, profileImageUrl: peer.profileImageUrl, online: peer.online }, 40) +
        '<div class="who"><strong>' + esc(peer.name) + '</strong><span>' + statusLine(peer, state) + '</span></div>' +
        '<div class="chat-head-actions">' +
          '<button class="icon-btn" data-action="chat-search" aria-label="Search in conversation">' + U.icon("search") + '</button>' +
          '<button class="icon-btn" data-action="chat-info" aria-label="Conversation info">' + U.icon("info") + '</button>' +
          '<button class="icon-btn" data-action="chat-menu" aria-label="More options">' + U.icon("more") + '</button>' +
        '</div>' +
      '</div>' +
      '<div id="stream-host">' + renderStream(state) + '</div>' +
      '<button class="scroll-bottom" id="scroll-bottom" data-action="scroll-bottom" aria-label="Jump to latest">' +
        U.icon("arrowDown") + '<span class="count" id="scroll-count" hidden>0</span></button>' +
      renderComposer(state) +
    '</div>';
  }

  /** Re-render only the message stream (keeps composer focus and caret). */
  function updateStream(state, autoscroll) {
    var host = document.getElementById("stream-host");
    if (!host) return;
    var scroller = document.getElementById("msg-scroll");
    var stick = !scroller || (scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight) < 120;
    host.innerHTML = renderStream(state);
    var next = document.getElementById("msg-scroll");
    if (next && (autoscroll || stick)) {
      requestAnimationFrame(function () { next.scrollTop = next.scrollHeight; });
    }
  }

  window.ConvoChat = {
    renderChatPane: renderChatPane,
    renderStream: renderStream,
    renderComposer: renderComposer,
    updateStream: updateStream,
    statusLine: statusLine
  };
})();
