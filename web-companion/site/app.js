/* Convo Chat Web — application controller. v4.9.2 */
(function () {
  "use strict";
  var S = window.ConvoStore;
  var U = window.ConvoUi;
  var Api = window.ConvoApi;
  var Screens = window.ConvoScreens;
  var Chat = window.ConvoChat;

  var POLL_CHATS = 20000;
  var POLL_MESSAGES = 7000;
  var WEB_VERSION = "v4.9.2";
  var POLL_PRESENCE = 45000;
  // v4.7.0: premium friends invite vibration handled via ActivityNotification type premium_approved
  var LAUNCH_MIN_MS = 1150;

  var state = {
    tab: "chats",
    settingsSection: "",
    activePeer: "",
    activePeerName: "",
    activePeerImage: "",
    me: { uid: "", name: "", username: "", email: "", profileImageUrl: "", bio: "" },
    chats: [],
    messages: [],
    drafts: {},
    replyTo: null,
    typing: {},
    devices: [],
    searchQuery: "",
    searchResults: [],
    people: [],
    peopleLoading: false,
    friendRequests: [],
    searchLoading: false,
    chatFilter: "all",
    homePosts: [],
    homeLoading: false,
    authMode: (function(){ try{ return localStorage.getItem("convo.authMode")||"code"; }catch(_){ return "code"; } })(),
    loadingChats: true,
    loadingMessages: false,
    hasMoreMessages: false,
    locked: false,
    launchProgress: 0,
    profileLink: "",
    unreadTotal: 0,
    online: navigator.onLine,
    uploadProgress: null,
    qrId: "",
    qrData: "",
    qrCountdown: 30
  };

  var timers = { chats: 0, messages: 0, presence: 0, typing: 0, lock: 0 };
  var launchStartedAt = Date.now();
  var lastTypingSentAt = 0;
  var typingStopTimer = 0;
  var messagePollFailures = 0;
  var pollingMessages = false;
  var loadingOlder = false;
  var openOverlayCloser = null;

  /* ------------------------------------------------------------ launch screen */
  function prefix(mark) {
    var t = (Date.now() - launchStartedAt) / LAUNCH_MIN_MS;
    var curved = 1 - Math.pow(1 - Math.min(1, t), 2.2);
    return Math.max(state.launchProgress, Math.min(.95, Math.max(curved, mark)));
  }
  function setLaunchProgress(value, note) {
    state.launchProgress = Math.min(1, value);
    var fill = document.getElementById("launch-fill");
    if (fill) fill.style.width = Math.round(state.launchProgress * 100) + "%";
    var sub = document.getElementById("launch-sub");
    if (sub && note) sub.textContent = note;
  }
  function particles() {
    var canvas = document.getElementById("launch-particles");
    if (!canvas) return function () {};
    var ctx = null;
    try { ctx = canvas.getContext("2d"); } catch (_) { ctx = null; }
    if (!ctx) return function () {};
    var dots = [];
    var raf = 0;
    var DPR = Math.min(2, window.devicePixelRatio || 1);
    function resize() { canvas.width = window.innerWidth * DPR; canvas.height = window.innerHeight * DPR; }
    resize();
    for (var i = 0; i < 46; i++) {
      dots.push({ x: Math.random()*canvas.width, y: Math.random()*canvas.height, r:(0.7+Math.random()*1.9)*DPR, vx:(Math.random()-.5)*.12*DPR, vy:(-0.12-Math.random()*.26)*DPR, a:.15+Math.random()*.5, hue:[262,196,330][i%3] });
    }
    function frame() {
      ctx.clearRect(0,0,canvas.width,canvas.height);
      dots.forEach(function(dot){ dot.x+=dot.vx; dot.y+=dot.vy; if(dot.y<-12){dot.y=canvas.height+10;dot.x=Math.random()*canvas.width;} if(dot.x<-12)dot.x=canvas.width+10; if(dot.x>canvas.width+12)dot.x=-10; ctx.beginPath(); ctx.fillStyle="hsla("+dot.hue+",92%,76%,"+dot.a+")"; ctx.arc(dot.x,dot.y,dot.r,0,Math.PI*2); ctx.fill(); });
      raf=requestAnimationFrame(frame);
    }
    frame();
    window.addEventListener("resize", resize);
    return function stop(){ cancelAnimationFrame(raf); window.removeEventListener("resize", resize); };
  }
  function finishLaunch() {
    var launch=document.getElementById("launch");
    if(!launch) return;
    setLaunchProgress(1);
    launch.classList.add("done");
    document.getElementById("app").hidden=false;
    document.documentElement.setAttribute("data-ready","1");
    setTimeout(function(){ launch.remove(); },520);
  }

  /* ---------------------------------------------------------------- helpers */
  function session(){ return Api.session(); }
  function persistCache(){ S.cacheWrite({ chats: state.chats.slice(0,60), me: state.me, tab: state.tab, cachedAt: Date.now() }); }
  function restoreCache(){ var c=S.cacheRead(); if(c&&Array.isArray(c.chats)&&c.chats.length) state.chats=c.chats; if(c&&c.me&&c.me.uid) state.me=Object.assign(state.me,c.me); unreadTotal(); }
  function unreadTotal(){ state.unreadTotal=state.chats.reduce(function(s,c){return s+Number(c.unread||0);},0); return state.unreadTotal; }
  function isMobile(){ return window.matchMedia("(max-width: 860px)").matches; }
  function findChat(uid){ return state.chats.filter(function(c){return c.uid===uid;})[0]; }

  /* ------------------------------------------------------------------- rail */
  function renderNav(){
    var rail=document.getElementById("rail-nav");
    var tabbar=document.getElementById("tabbar");
    var unread=unreadTotal();
    var badge=unread ? '<span class="rail-badge">'+(unread>99?"99+":unread)+'</span>' : "";
    var items=[
      {id:"home",icon:"home",label:"Home",badge:""},
      {id:"chats",icon:"chats",label:"Chats",badge:badge},
      {id:"search",icon:"search",label:"Discover",badge:(state.friendRequests||[]).length ? '<span class="rail-badge">'+state.friendRequests.length+'</span>' : ""},
      {id:"settings",icon:"settings",label:"Settings",badge:""}
    ];
    var html=items.map(function(item){
      return '<button type="button" class="rail-btn '+(state.tab===item.id?"active":"")+'" data-tab="'+item.id+'" aria-label="'+item.label+'">'+U.icon(item.icon)+'<span>'+item.label+'</span>'+(item.badge||"")+'</button>';
    }).join("");
    var profileActive=state.tab==="profile";
    html+='<button type="button" class="rail-btn avatar-btn '+(profileActive?"active":"")+'" data-tab="profile" aria-label="Your profile">'+U.avatar({name:state.me.name||"Convo",profileImageUrl:state.me.profileImageUrl},34)+'</button>';
    html+='<button type="button" class="rail-btn" data-action="logout" aria-label="Log out">'+U.icon("logout")+'<span>Log out</span></button>';
    if(rail) rail.innerHTML=html;
    if(tabbar){
      tabbar.innerHTML=items.concat([{id:"profile",icon:"user",label:"You",badge:""}]).map(function(item){
        var active=state.tab===item.id || (item.id==="settings" && state.tab==="settings");
        return '<button type="button" class="'+(active?"active":"")+'" data-tab="'+item.id+'">'+U.icon(item.icon)+'<span>'+item.label+'</span>'+(item.badge||"")+'</button>';
      }).join("");
    }
  }

  /* ----------------------------------------------------------------- render */
  function renderPane(){
    var pane=document.getElementById("pane");
    if(state.locked){ pane.innerHTML=Screens.renderLockScreen(state); document.getElementById("tabbar").hidden=false; renderNav(); return; }
    if(!session()){
      pane.innerHTML=Screens.renderPairing();
      document.getElementById("tabbar").hidden=true;
      var rn=document.getElementById("rail-nav"); if(rn) rn.innerHTML="";
      // New design: mode already set in render, just init QR if needed
      setTimeout(function(){
        var m=state.authMode||"code";
        if(m==="qr") initQrOnLogin();
        // Auto-generate QR even when on desktop (QR always visible)
        if(document.getElementById("qr-canvas")) initQrOnLogin();
      }, 80);
      return;
    }
    document.getElementById("tabbar").hidden=false;
    if(state.tab==="chats"){
      pane.innerHTML='<div class="split '+(state.activePeer?"has-open":"")+'">'+Screens.renderChatsPane(state)+(state.activePeer?Chat.renderChatPane(state):Screens.renderChatPlaceholder())+'</div><div style="position:fixed;bottom:8px;right:12px;background:linear-gradient(135deg,#8b7bff,#5ac8fa);color:#fff;padding:6px 12px;border-radius:20px;font-size:11px;font-weight:800;z-index:999">v'+WEB_VERSION+' • Web</div>';
      requestAnimationFrame(function(){
        var back=document.getElementById("chat-back");
        if(back) back.style.display=isMobile()?"inline-flex":"none";
        bindChatScrollGuard();
        bindFileDropZone();
      });
    } else if(state.tab==="search"){ pane.innerHTML=Screens.renderSearchPane(state); }
    else if(state.tab==="settings"){ pane.innerHTML=Screens.renderSettingsPane(state, state.settingsSection); }
    else if(state.tab==="profile"){ pane.innerHTML=Screens.renderProfilePane(state); loadProfileLink(); }
    renderNav();
    persistCache();
  }
  function refreshChatListOnly(){
    if(state.tab!=="chats") return;
    var existing=document.querySelector(".pane-list");
    if(!existing){ renderPane(); return; }
    var holder=document.createElement("div"); holder.innerHTML=Screens.renderChatsPane(state);
    var fresh=holder.firstElementChild;
    existing.replaceWith(fresh);
  }
  function toast(m,k,a){ U.toast(m,k,a); }

  // ── scroll guard: mark user is scrolling to prevent auto-jump ──
  function bindChatScrollGuard(){
    var sc=document.getElementById("msg-scroll");
    if(!sc || sc.__bound) return;
    sc.__bound=true;
    var tick=0;
    sc.addEventListener("scroll", function(){
      var atBottom=(sc.scrollHeight - sc.scrollTop - sc.clientHeight) < 120;
      window.__convoUserScrollingUntil = atBottom ? 0 : Date.now()+1800;
      var btn=document.getElementById("scroll-bottom");
      if(btn) btn.classList.toggle("show", !atBottom);
      // throttle badge
      if(tick++%4===0){
        var countEl=document.getElementById("scroll-count");
        if(countEl && !atBottom) {}
      }
    }, {passive:true});
  }
  function bindFileDropZone(){
    var zone=document.getElementById("pane-main-dropzone");
    var overlay=document.getElementById("drag-overlay");
    if(!zone || !overlay) return;
    zone.addEventListener("dragenter", function(e){ e.preventDefault(); overlay.hidden=false; });
    zone.addEventListener("dragover", function(e){ e.preventDefault(); });
    zone.addEventListener("dragleave", function(e){ if(!zone.contains(e.relatedTarget)) overlay.hidden=true; });
    zone.addEventListener("drop", function(e){
      e.preventDefault(); overlay.hidden=true;
      var files=e.dataTransfer && e.dataTransfer.files;
      if(files && files.length) handleFiles(files);
    });
    var fileInput=document.getElementById("file-input-hidden");
    if(fileInput && !fileInput.__bound){
      fileInput.__bound=true;
      fileInput.addEventListener("change", function(){ if(fileInput.files && fileInput.files.length) handleFiles(fileInput.files); fileInput.value=""; });
    }
  }

  /* ------------------------------------------------------------------ data */
  async function loadBootstrap(){
    setLaunchProgress(prefix(.45), "Syncing your profile…");
    try{
      var data=await Api.bootstrap();
      if(data.me) state.me=Object.assign(state.me,data.me);
      if(Array.isArray(data.chats)) state.chats=normalizeChats(data.chats);
      if(Array.isArray(data.devices)) state.devices=data.devices;
      if(data.settings) applyServerSettings(data.settings);
      if(data.suggestedWallpapers && Array.isArray(data.suggestedWallpapers)) state.wallpapers=data.suggestedWallpapers;
      state.loadingChats=false; unreadTotal(); persistCache();
      setLaunchProgress(prefix(.82), "Almost ready…");
      return true;
    }catch(error){
      if(error.status===401) return false;
      restoreCache(); state.loadingChats=false;
      toast(error.message||"Could not load your chats","error");
      return true;
    }
  }
  function applyServerSettings(server){
    if(typeof server.readReceipts==="boolean") state.serverReadReceipts=server.readReceipts;
    if(typeof server.typingIndicator==="boolean") state.serverTyping=server.typingIndicator;
  }
  function normalizeChats(list){
    return (list||[]).map(function(chat){
      return { uid:String(chat.uid||""), name:String(chat.name||"Convo user"), username:String(chat.username||""), profileImageUrl:String(chat.profileImageUrl||""), lastMessage:chat.lastMessage||null, lastAt:Number(chat.lastAt||(chat.lastMessage&&chat.lastMessage.timestamp)||0), unread:Number(chat.unread||0), online:chat.online===true, lastActive:Number(chat.lastActive||0), muted:chat.muted===true, mutual:chat.mutual!==false };
    }).filter(function(chat){return chat.uid && chat.uid!==state.me.uid;});
  }
  async function loadHomePosts(){
    if(!session()||state.locked) return;
    state.homeLoading=true;
    try{ var data=await Api.homePosts(); if(Array.isArray(data.posts)) state.homePosts=data.posts; }catch(e){ state.homePosts=[]; }
    state.homeLoading=false;
    if(state.tab==="home") renderPane();
  }
  async function refreshChats(silent){
    if(!session()||state.locked) return;
    try{
      var data=await Api.chats();
      if(data.me) state.me=Object.assign(state.me,data.me);
      if(Array.isArray(data.chats)){
        var nextChats=normalizeChats(data.chats);
        notifyIncoming(state.chats,nextChats);
        state.chats=nextChats;
      }
      unreadTotal(); updateTitleBadge();
      refreshChatListOnly(); renderNav(); persistCache();
    }catch(error){ if(error.status!==401 && !silent) toast(error.message,"error"); }
  }

  /* --------------------------------------------- live notifications (v4.5.0) */
  var baseTitle=document.title||"Convo Chat Web";
  function updateTitleBadge(){
    var total=0; (state.chats||[]).forEach(function(c){ if(!c.muted) total+=Number(c.unread||0); });
    document.title=total ? "("+total+") "+baseTitle : baseTitle;
    try{ if(navigator.setAppBadge){ total?navigator.setAppBadge(total):navigator.clearAppBadge(); } }catch(e){}
  }
  function notifyIncoming(prev,next){
    if(!prev||!prev.length) return;
    var byUid={}; prev.forEach(function(c){byUid[c.uid]=c;});
    next.forEach(function(chat){
      var before=byUid[chat.uid]; if(!before||chat.muted) return;
      var last=chat.lastMessage||{}; var fromPeer=last.senderId && last.senderId!==state.me.uid;
      var isNew=chat.lastAt>(before.lastAt||0) && chat.unread>(before.unread||0);
      if(!fromPeer||!isNew) return;
      var viewing=!document.hidden && state.activePeer===chat.uid;
      if(viewing) return;
      var body=String(last.text||"New message").slice(0,140);
      if(document.hidden && "Notification" in window && Notification.permission==="granted" && S.settings().notifications!==false){
        try{ var n=new Notification(chat.name,{body:body,tag:"convo-"+chat.uid,icon:chat.profileImageUrl||undefined,renotify:true}); n.onclick=function(){window.focus(); openChat(chat.uid,chat.name,chat.profileImageUrl); n.close();}; }catch(e){}
      }
      if(!document.hidden) toast(chat.name+": "+body.slice(0,60),"info");
      S.beep("recv");
    });
  }
  function askNotificationPermission(){
    if(!("Notification" in window) || Notification.permission!=="default") return;
    try{ Notification.requestPermission(); }catch(e){}
  }

  async function openChat(uid, nameHint, imageHint){
    if(!uid) return;
    state.activePeer=uid;
    var chat=findChat(uid);
    state.activePeerName=(chat&&chat.name)||nameHint||"Chat";
    state.activePeerImage=(chat&&chat.profileImageUrl)||imageHint||"";
    state.messages=[]; state.replyTo=null; state.loadingMessages=true; state.hasMoreMessages=false; state.typing={}; state.lastReadPeer=uid; state.lastReadSent="";
    if(chat && chat.unread) chat.unread=0;
    if(isMobile() && state.tab!=="chats") state.tab="chats";
    renderPane();
    await loadMessages();
  }
  async function loadMessages(older){
    var peer=state.activePeer; if(!peer || (older && loadingOlder)) return;
    if(older) loadingOlder=true;
    try{
      var before=older && state.messages.length ? state.messages[0].timestamp : 0;
      var previousScrollTop=0; var previousScrollHeight=0;
      var previousScroller=document.getElementById("msg-scroll");
      if(older && previousScroller){ previousScrollTop=previousScroller.scrollTop; previousScrollHeight=previousScroller.scrollHeight; }
      var data=await Api.messages(peer, before);
      if(state.activePeer!==peer){ if(older) loadingOlder=false; return; }
      var incoming=(data.messages||[]).map(normalizeMessage);
      state.messages=mergeMessages(incoming,state.messages);
      state.hasMoreMessages=Boolean(data.hasMore);
      state.loadingMessages=false; messagePollFailures=0;
      markRead();
      Chat.updateStream(state, !older);
      if(older){ var sc=document.getElementById("msg-scroll"); if(sc) sc.scrollTop=previousScrollTop + Math.max(0, sc.scrollHeight-previousScrollHeight); loadingOlder=false; }
      var chat=findChat(peer); if(chat && chat.unread){chat.unread=0; refreshChatListOnly(); renderNav();}
    }catch(error){
      if(older) loadingOlder=false;
      state.loadingMessages=false; messagePollFailures++; if(messagePollFailures===1) toast(error.message||"Could not load messages","error");
      Chat.updateStream(state,true);
    }
  }
  function mergeMessages(a,b){
    var seen={}; return a.concat(b).filter(function(m){ if(seen[m.messageId]) return false; seen[m.messageId]=true; return true; }).sort(function(x,y){return x.timestamp-y.timestamp;});
  }
  function normalizeMessage(raw){
    return { messageId:String(raw.messageId||raw.id||""), senderId:String(raw.senderId||""), senderName:String(raw.senderName||""), text:String(raw.text||""), timestamp:Number(raw.timestamp||0), edited:raw.edited===true, imageUrl:raw.imageUrl||"", fileUrl:raw.fileUrl||"", fileName:raw.fileName||"", fileMimeType:raw.fileMimeType||"", fileSize:raw.fileSize||0, voiceUrl:raw.voiceUrl||"", voiceDurationSec:raw.voiceDurationSec||0, seenByRecipient:raw.seenByRecipient===true, deliveredToRecipient:raw.deliveredToRecipient===true, read:raw.read===true, reactions:raw.reactions&&typeof raw.reactions==="object"?raw.reactions:{} };
  }
  async function markRead(){
    var peer=state.activePeer;
    if(!peer || !S.settings().readReceipts) return;
    var newest=state.messages.filter(function(m){return m.senderId!==state.me.uid;}).slice(-1)[0];
    if(!newest) return;
    if(state.lastReadPeer!==peer){ state.lastReadPeer=peer; state.lastReadSent=""; }
    if(state.lastReadSent===newest.messageId) return;
    state.lastReadSent=newest.messageId;
    try{ await Api.read(peer, newest.timestamp); }catch(_){ if(state.lastReadPeer===peer && state.lastReadSent===newest.messageId) state.lastReadSent=""; }
  }
  function createClientMessageId(){
    try{ if(window.crypto && crypto.randomUUID) return crypto.randomUUID().replace(/-/g, ""); }catch(_){}
    return Date.now().toString(36)+Math.random().toString(36).slice(2,18);
  }
  async function sendMessage(retry){
    var input=document.getElementById("composer-input");
    var peer=retry ? retry.peer : state.activePeer;
    if(!peer || (!retry && !input)) return;
    var text=retry ? retry.text : input.value.trim(); if(!text) return;
    var reply=retry ? retry.reply : state.replyTo;
    var clientMessageId=retry ? retry.clientMessageId : createClientMessageId();
    var serverMessageId="web_"+state.me.uid+"_"+clientMessageId;
    var optimistic=retry ? retry.message : { messageId:serverMessageId, clientMessageId:clientMessageId, senderId:state.me.uid, senderName:state.me.name||"You", text:text, timestamp:Date.now(), pending:true, reactions:{} };
    if(reply && !retry){ optimistic.replyToId=reply.messageId; optimistic.replyToText=reply.text; optimistic.replyToSenderName=reply.senderName; }
    if(!retry){
      state.messages.push(optimistic); state.drafts[peer]=""; state.replyTo=null;
      input.value=""; autosize(input); Chat.updateStream(state,true); renderComposerOnly();
    } else { optimistic.pending=true; optimistic.failed=false; }
    try{
      var extra=Object.assign({}, reply ? {replyToId:reply.messageId, replyToText:String(reply.text||"").slice(0,400), replyToSenderName:reply.senderName||""} : {}, {clientMessageId:clientMessageId});
      var payload=await Api.send(peer,text,extra);
      if(state.activePeer===peer){
        var idx=state.messages.indexOf(optimistic);
        if(idx>=0) state.messages[idx]=normalizeMessage(Object.assign({},optimistic,payload.message||{},{pending:false,failed:false}));
        Chat.updateStream(state,true);
      }
      S.beep("send"); refreshChats(true);
    }catch(error){
      optimistic.pending=false; optimistic.failed=true;
      if(state.activePeer===peer) Chat.updateStream(state,false);
      toast(error.message||"Message failed to send","error",{ label:"Retry", onClick:function(){
        var attempt={peer:peer,text:text,clientMessageId:clientMessageId,message:optimistic,reply:reply};
        if(state.activePeer===peer) sendMessage(attempt);
        else openChat(peer).then(function(){ sendMessage(attempt); });
      }});
    }
  }
  function autosize(textarea){ if(!textarea) return; textarea.style.height="auto"; textarea.style.height=Math.min(148, textarea.scrollHeight)+"px"; }
  function renderComposerOnly(){
    var host=document.querySelector(".composer"); if(!host) return;
    var holder=document.createElement("div"); holder.innerHTML=Chat.renderComposer(state);
    host.replaceWith(holder.firstElementChild);
    var input=document.getElementById("composer-input");
    if(input && !isMobile()){ input.focus({preventScroll:true}); autosize(input); }
    bindFileDropZone();
  }

  // ── File handling: unlimited size via Worker R2 ──
  async function handleFiles(fileList){
    if(!state.activePeer){ toast("Open a chat first, then share files","info"); return; }
    var files=Array.prototype.slice.call(fileList||[]);
    if(!files.length) return;
    for(var i=0;i<files.length;i++){
      var file=files[i];
      state.uploadProgress=0;
      renderComposerOnly();
      try{
        var res=await Api.uploadFile(file, function(p){ state.uploadProgress=p; var bar=document.querySelector(".upload-progress-bar"); if(bar) bar.style.width=p+"%"; var span=document.querySelector(".upload-progress span"); if(span) span.textContent=p+"%"; });
        var payload={ fileUrl:res.fileUrl||res.url, fileName:file.name, fileMimeType:file.type||"application/octet-stream", fileSize:file.size };
        // Treat as message: send via chat endpoint with file metadata
        await Api.send(state.activePeer, payload.fileName, payload);
        state.uploadProgress=null;
        renderComposerOnly();
        toast("File sent — "+file.name,"success");
        await loadMessages();
      }catch(err){
        state.uploadProgress=null; renderComposerOnly();
        toast(err.message||"Could not upload file","error");
      }
    }
  }
  async function previewFile(url, mime){
    var isImg=/^image\//.test(mime||"");
    var isPdf=(mime==="application/pdf") || /\.pdf$/i.test(url);
    var body="";
    if(isImg) body='<img class="zoom" src="'+S.escapeHtml(url)+'" alt="Preview" style="max-width:100%;border-radius:12px">'
    else if(isPdf) body='<iframe src="'+S.escapeHtml(url)+'" style="width:100%;height:70vh;border:0;border-radius:12px" title="PDF preview"></iframe>'
    else body='<p class="confirm-text">No inline preview for this file type. Download to view.</p><p class="tiny dim">'+S.escapeHtml(mime||"unknown")+'</p>';
    U.modal({ title:"File preview", body:body, wide:true, actions:[{label:"Close",variant:"ghost"}, {label:"Download",variant:"primary",onClick:function(){ window.open(url,"_blank","noopener"); }}] });
  }

  async function react(messageId, value){
    if(!state.activePeer) return;
    var message=state.messages.filter(function(m){return m.messageId===messageId;})[0];
    if(message){ message.reactions=message.reactions||{}; if(message.reactions[state.me.uid]===value) delete message.reactions[state.me.uid]; else message.reactions[state.me.uid]=value; Chat.updateStream(state,false); }
    try{ await Api.react(state.activePeer, messageId, value); }catch(error){ toast(error.message||"Could not add reaction","error"); loadMessages(); return; }
    S.beep("send");
    var scroller=document.getElementById("msg-scroll"); var pos=scroller?scroller.scrollTop:0;
    Chat.updateStream(state,false); var next=document.getElementById("msg-scroll"); if(next) next.scrollTop=pos;
  }

  /* ------------------------------------------------------------------ polls */
  async function pollMessages(){
    var peer=state.activePeer;
    if(!peer||state.locked||document.hidden||pollingMessages) return;
    pollingMessages=true;
    try{
      var data=await Api.messages(peer,0);
      if(state.activePeer!==peer) return;
      var incoming=(data.messages||[]).map(normalizeMessage);
      if(!incoming.length && !state.messages.length) return;
      var previousLast=state.messages.length ? state.messages[state.messages.length-1].messageId : "";
      // Merge the newest server window into the locally paginated history. Polls must
      // never replace older messages that the user has already loaded.
      state.messages=mergeMessages(incoming,state.messages);
      state.loadingMessages=false;
      var newest=state.messages.length ? state.messages[state.messages.length-1] : null;
      var hasNewPeerMessage=Boolean(newest && newest.messageId!==previousLast && newest.senderId!==state.me.uid);
      if(hasNewPeerMessage) S.beep("recv");
      Chat.updateStream(state,false); // scroll only if the reader was already at the bottom
      markRead();
      messagePollFailures=0;
    }catch(error){ messagePollFailures++; if(error.status===401) return; if(messagePollFailures===3) toast("Live updates paused — reconnecting…","info"); }
    finally{ pollingMessages=false; }
  }
  async function pollPresence(){
    if(state.locked||document.hidden||!session()) return;
    try{
      var data=await Api.presence();
      if(data && Array.isArray(data.people)){
        var map={}; data.people.forEach(function(p){map[p.uid]=p;});
        state.chats=state.chats.map(function(chat){ var person=map[chat.uid]; if(!person) return chat; return Object.assign({},chat,{online:person.online===true,lastActive:Number(person.lastActive||chat.lastActive)}); });
        state.typing=data.typing||state.typing;
        refreshChatListOnly();
        if(state.activePeer){ var head=document.querySelector(".chat-head .who span"); var chat=findChat(state.activePeer); if(head&&chat) head.innerHTML=Chat.statusLine(chat,state); }
      }
    }catch(_){}
  }
  function startPolls(){
    stopPolls();
    var hiddenTick=0, friendTick=0;
    refreshFriendRequests();
    timers.chats=setInterval(function(){
      if(state.locked) return;
      if(document.hidden){ hiddenTick++; if(hiddenTick%3) return; } else hiddenTick=0;
      refreshChats(true); friendTick=(friendTick+1)%4; if(!friendTick) refreshFriendRequests();
      if(friendTick===1 && state.tab==="home") loadHomePosts();
    }, POLL_CHATS);
    timers.messages=setInterval(pollMessages, POLL_MESSAGES);
    timers.presence=setInterval(pollPresence, POLL_PRESENCE);
  }
  function stopPolls(){ Object.keys(timers).forEach(function(k){ if(timers[k] && (k==="chats"||k==="messages"||k==="presence")){ clearInterval(timers[k]); timers[k]=0; } }); }

  /* ------------------------------------------------------------- interactions */
  function openEmojiPicker(button){
    closeOverlay();
    var grid=["😀","😁","😂","🤣","😊","😍","🥰","😘","😎","🤩","🥳","😇","🙃","😉","😌","😋","👍","👎","👏","🙏","💪","🤝","✌️","🤟","👌","🤌","☝️","👋","🫶","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","💖","💯","🔥","✨","🎉","🎊","🎁","🌹","🌸","🌼","😢","😭","😤","😡","🤔","🤨","😴","🥺","😱","🤯","🤗","🤭","🤫","😬","🫠","🫡","☕","🍕","🍔","🍰","🍻","🎵","🎮","⚽","🚀","🌙","⭐","☀️","🌧️","📍","💻","📱"];
    var pop=document.createElement("div"); pop.className="popover";
    pop.innerHTML='<div class="quick-reactions" style="margin-bottom:6px">'+S.reactions.slice(0,8).map(function(e){return '<button type="button" data-emoji="'+e+'">'+e+'</button>';}).join("")+'</div><div class="emoji-grid">'+grid.map(function(e){return '<button type="button" data-emoji="'+e+'">'+e+'</button>';}).join("")+'</div>';
    document.body.appendChild(pop);
    var rect=button.getBoundingClientRect(); pop.style.position="fixed"; pop.style.bottom=Math.max(12, window.innerHeight-rect.top+10)+"px"; pop.style.left=Math.max(12, Math.min(rect.left-8, window.innerWidth-350))+"px";
    pop.addEventListener("click", function(event){
      var target=event.target.closest("[data-emoji]"); if(!target) return;
      var input=document.getElementById("composer-input"); if(input){
        var start=input.selectionStart||input.value.length;
        input.value=input.value.slice(0,start)+target.dataset.emoji+input.value.slice(input.selectionEnd||start);
        input.focus(); input.selectionStart=input.selectionEnd=start+target.dataset.emoji.length;
        state.drafts[state.activePeer]=input.value;
      }
    });
    openOverlayCloser=function(){ pop.remove(); openOverlayCloser=null; };
  }
  function closeOverlay(){ if(openOverlayCloser) openOverlayCloser(); }
  function openAttachMenu(button){
    closeOverlay();
    U.sheetMenu([
      { icon:"file", label:"Choose file (any type, any size)", onClick:function(){ var inp=document.getElementById("file-input-hidden"); if(inp) inp.click(); } },
      { icon:"image", label:"Send an image link", onClick:function(){ promptImageLink(); } },
      { icon:"file", label:"Send a file link", onClick:function(){ promptFileLink(); } },
      { icon:"copy", label:"Paste from clipboard", onClick:function(){ pasteIntoComposer(); } },
      { separator:true },
      { icon:"info", label:"Preview & download — long-press file to manage", hint:"Tip", onClick:function(){ toast("Files shared here can be previewed inline and downloaded by anyone in the chat.","info"); } }
    ], "Attach");
  }
  async function promptImageLink(){
    var url=await U.prompt({title:"Send an image",label:"Secure https image link",placeholder:"https://…",help:"The link must start with https://. The image is shown in the chat on both devices."});
    if(!url) return; if(!/^https:\/\//i.test(url.trim())){ toast("Only https links are allowed","error"); return; }
    await Api.send(state.activePeer,"Photo",{imageUrl:url.trim()}).then(function(){loadMessages(); toast("Image sent","success");}).catch(function(error){toast(error.message||"Could not send the image","error");});
  }
  async function promptFileLink(){
    var url=await U.prompt({title:"Send a file",label:"Secure https file link",placeholder:"https://…",help:"Recipients can open or download the file on any device."});
    if(!url) return; if(!/^https:\/\//i.test(url.trim())){ toast("Only https links are allowed","error"); return; }
    await Api.send(state.activePeer,"File",{fileUrl:url.trim(),fileName:url.trim().split("/").pop()||"attachment"}).then(function(){loadMessages(); toast("File sent","success");}).catch(function(error){toast(error.message||"Could not send the file","error");});
  }
  async function pasteIntoComposer(){
    try{
      var text=await navigator.clipboard.readText();
      var input=document.getElementById("composer-input");
      if(input && text){ input.value+=text; state.drafts[state.activePeer]=input.value; autosize(input); input.focus(); }
    }catch(_){ toast("Clipboard access is blocked by the browser","error"); }
  }
  function openMessageMenu(messageId, anchor){
    closeOverlay();
    var message=state.messages.filter(function(m){return m.messageId===messageId;})[0]; if(!message) return;
    var mine=message.senderId===state.me.uid;
    var pop=document.createElement("div"); pop.className="popover"; pop.style.position="fixed";
    pop.innerHTML='<div class="quick-reactions">'+S.reactions.slice(0,6).map(function(e){return '<button type="button" data-quick="'+e+'">'+e+'</button>';}).join("")+'</div><div class="msg-actions" style="margin-top:6px">'+'<button type="button" class="act" data-act="reply">'+U.icon("reply")+'<span>Reply</span></button>'+'<button type="button" class="act" data-act="copy">'+U.icon("copy")+'<span>Copy text</span></button>'+'<button type="button" class="act" data-act="forward-hint">'+U.icon("forward")+'<span>Forward</span></button>'+(mine?'<button type="button" class="act danger" data-act="delete">'+U.icon("trash")+'<span>Delete for me</span></button>' : "")+'</div>';
    document.body.appendChild(pop);
    var rect=anchor.getBoundingClientRect(); pop.style.top=Math.min(window.innerHeight-pop.offsetHeight-12, rect.bottom+6)+"px"; pop.style.left=Math.max(10, Math.min(rect.left, window.innerWidth-pop.offsetWidth-12))+"px";
    pop.addEventListener("click", function(event){
      var quick=event.target.closest("[data-quick]"); if(quick){ react(message.messageId, quick.dataset.quick); closeOverlay(); return; }
      var action=event.target.closest("[data-act]"); if(!action) return;
      if(action.dataset.act==="reply"){ state.replyTo={messageId:message.messageId,text:message.text,senderName:message.senderName||state.activePeerName}; renderComposerOnly(); var input=document.getElementById("composer-input"); if(input) input.focus(); }
      else if(action.dataset.act==="copy"){ if(navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(message.text||"").then(function(){toast("Copied","success");},function(){toast("Copy failed","error");}); }
      else if(action.dataset.act==="delete"){ U.confirm({title:"Delete this message?",message:"It will be removed from this browser. The Convo app keeps its own copy.",confirmLabel:"Delete",danger:true}).then(function(ok){ if(!ok) return; state.messages=state.messages.filter(function(m){return m.messageId!==message.messageId;}); Chat.updateStream(state,false); Api.send(state.activePeer,"",{deleteMessageId:message.messageId}).catch(function(){}); }); }
      else if(action.dataset.act==="forward-hint"){ toast("Forwarding is available in the Convo Chat app","info"); }
      closeOverlay();
    });
    openOverlayCloser=function(){ pop.remove(); openOverlayCloser=null; };
  }
  function openChatInfo(){
    var chat=findChat(state.activePeer)||{};
    U.sheet({ title:"Conversation", body:'<div style="display:grid;gap:14px;padding:6px 4px">'+'<div style="display:flex;gap:12px;align-items:center">'+U.avatar(chat,56)+'<div><strong style="font-size:15px">'+S.escapeHtml(chat.name||state.activePeerName)+'</strong>'+'<div class="tiny dim">'+(chat.username?"@"+S.escapeHtml(chat.username):(chat.online?"Online now":"Accepted friend"))+'</div></div></div>'+'<div class="group" style="margin:0"><div class="row"><span class="row-main"><strong>Messages here</strong><span>'+state.messages.length+' loaded in this browser</span></span></div>'+'<div class="row"><span class="row-main"><strong>Privacy</strong><span>This chat is proxied through the Convo gateway. Your phone keeps the full history.</span></span></div></div>'+'</div>', bodyIsHTML:true });
  }

  /* --------------------------------------------------------------- settings */
  function handleSettingToggle(key,value){
    if(key==="compact"){ S.setSetting("density", value?"compact":"comfortable"); return; }
    S.setSetting(key,value);
    if(key==="notify" && value) requestNotifications();
    if(key==="readReceipts"||key==="typingIndicator"||key==="showOnline"){ Api.saveSettings({readReceipts:S.settings().readReceipts, typingIndicator:S.settings().typingIndicator, showOnline:S.settings().showOnline}).catch(function(){}); }
    if(key==="notify") renderPane();
    if(key==="hidePreview"||key==="sendSound"||key==="time24"||key==="sendOnEnter"){ if(state.tab==="chats"&&state.activePeer){ Chat.updateStream(state,false); renderComposerOnly(); } }
  }
  async function requestNotifications(){
    if(!("Notification" in window)){ toast("This browser does not support notifications","error"); return; }
    try{ var p=await Notification.requestPermission(); if(p!=="granted"){ S.setSetting("notify",false); toast("Notifications stay off — you can enable them in browser settings","info"); } else { toast("Notifications enabled for new messages","success"); new Notification("Convo Chat Web",{body:"You will be notified about new messages.",tag:"convo-welcome"}); } }catch(_){ toast("Notifications are not available here","error"); }
    if(state.tab==="settings") renderPane();
  }

  async function logout(){
    var ok=await U.confirm({title:"Log out of this browser?",message:"Your chats stay safe on your phone. You can pair this browser again any time.",confirmLabel:"Log out"});
    if(!ok) return; await Api.logout(); Api.clearSession(); S.cacheClear(); state.chats=[]; state.messages=[]; state.activePeer=""; state.tab="chats"; stopPolls(); stopQr(); renderPane(); toast("Logged out","success");
  }
  async function logoutEverywhere(){
    var ok=await U.confirm({title:"Log out every browser?",message:"Every paired browser session is invalidated immediately. Your phone stays signed in.",confirmLabel:"Log out everywhere",danger:true});
    if(!ok) return; try{ await Api.logoutEverywhere(); Api.clearSession(); S.cacheClear(); state.chats=[]; state.messages=[]; state.activePeer=""; stopPolls(); stopQr(); renderPane(); toast("All browser sessions ended","success"); }catch(error){ toast(error.message||"Could not end the sessions","error"); }
  }
  async function exportData(){
    toast("Preparing your export…","info");
    try{ var data=await Api.exportData(); var blob=new Blob([JSON.stringify(data,null,2)],{type:"application/json"}); var url=URL.createObjectURL(blob); var link=document.createElement("a"); link.href=url; link.download="convo-chat-export-"+new Date().toISOString().slice(0,10)+".json"; document.body.appendChild(link); link.click(); link.remove(); setTimeout(function(){URL.revokeObjectURL(url);},4000); toast("Export downloaded","success"); }catch(error){ toast(error.message||"Export failed","error"); }
  }
  async function editProfile(){
    var values=await U.formModal({title:"Edit profile",subtitle:"These details appear on your Convo Chat profile everywhere.",confirmLabel:"Save changes",fields:[{key:"name",label:"Display name",value:state.me.name||"",max:60,placeholder:"Your name"},{key:"professionalTitle",label:"Title",value:state.me.professionalTitle||"",max:80,placeholder:"Photographer, student…",help:"Optional. Shown under your name."},{key:"pronouns",label:"Pronouns",value:state.me.pronouns||"",max:40,placeholder:"she/her"},{key:"bio",label:"About you",value:state.me.bio||"",multiline:true,rows:3,max:200,help:"Up to 200 characters."}]});
    if(!values) return; if(!values.name.trim()){ toast("A display name is required","error"); return; }
    try{ var data=await Api.saveProfile({name:values.name.trim(),bio:values.bio.trim(),professionalTitle:values.professionalTitle.trim(),pronouns:values.pronouns.trim()}); if(data.me) state.me=Object.assign(state.me,data.me); renderPane(); toast("Profile updated","success"); }catch(error){ toast(error.message||"Could not save your profile","error"); }
  }
  function changeAvatar(){
    var input=document.createElement("input"); input.type="file"; input.accept="image/png,image/jpeg,image/webp,image/gif";
    input.addEventListener("change", function(){
      var file=input.files&&input.files[0]; if(!file) return; if(file.size>12*1024*1024){ toast("Choose an image under 12 MB","error"); return; }
      var reader=new FileReader(); reader.onload=function(){
        var image=new Image(); image.onload=function(){
          var side=Math.min(image.width,image.height); var canvas=document.createElement("canvas"); canvas.width=512; canvas.height=512; var ctx=canvas.getContext("2d");
          ctx.drawImage(image,(image.width-side)/2,(image.height-side)/2,side,side,0,0,512,512);
          var dataUrl=canvas.toDataURL("image/jpeg",0.86);
          toast("Uploading photo…","info");
          Api.uploadAvatar(dataUrl).then(function(data){ if(data.me) state.me=Object.assign(state.me,data.me); else if(data.profileImageUrl) state.me.profileImageUrl=data.profileImageUrl; renderPane(); toast("Profile photo updated","success"); }).catch(function(error){ toast(error.message||"Could not upload that photo","error"); });
        }; image.onerror=function(){ toast("That file is not a readable image","error"); }; image.src=reader.result;
      }; reader.onerror=function(){ toast("Could not read that file","error"); }; reader.readAsDataURL(file);
    }); input.click();
  }
  async function loadProfileLink(){
    if(state.profileLink) return;
    try{ var data=await Api.profileLink(); var link=data.workerProfileUrl||data.profileUrl||""; if(!link) return; state.profileLink=link; if(state.tab==="profile") renderPane(); }catch(_){}
  }
  async function copyProfileLink(){
    var link=state.profileLink; if(!link){ try{ var data=await Api.profileLink(); link=data.workerProfileUrl||data.profileUrl||""; state.profileLink=link; }catch(_){} }
    if(!link){ toast("Profile link is not ready yet","error"); return; }
    writeClipboard(link,"Profile link copied");
  }
  async function shareProfile(){
    var link=state.profileLink; if(!link){ try{ var data=await Api.profileLink(); link=data.workerProfileUrl||data.profileUrl||""; state.profileLink=link; }catch(_){} }
    var title=(state.me.name||"Convo Chat")+" on Convo Chat";
    if(navigator.share && link){ try{ await navigator.share({title:title,text:"Find me on Convo Chat",url:link}); return; }catch(_){ return; } }
    if(link) writeClipboard(link,"Profile link copied — share it anywhere"); else toast("Profile link is not ready yet","error");
  }
  function writeClipboard(text,message){
    if(navigator.clipboard && navigator.clipboard.writeText){ navigator.clipboard.writeText(text).then(function(){toast(message,"success");}).catch(function(){toast(text,"info");}); }
    else { var area=document.createElement("textarea"); area.value=text; document.body.appendChild(area); area.select(); try{ document.execCommand("copy"); toast(message,"success"); }catch(_){ toast(text,"info"); } area.remove(); }
  }

  /* --------------------------------------------------------------- pairing */
  async function redeemCode(){
    var input=document.getElementById("pair-code"); var errorNode=document.getElementById("pair-error"); if(!input) return;
    var code=input.value.replace(/\D/g,""); if(code.length!==10){ if(errorNode) errorNode.textContent="Enter the full 10-digit code from your phone."; input.focus(); return; }
    var button=document.querySelector('[data-action="pair"]'); if(button){ button.disabled=true; button.innerHTML=U.icon("refresh")+"<span>Connecting…</span>"; }
    try{ await Api.redeem(code); if(errorNode) errorNode.textContent=""; state.tab="chats"; stopQr(); await bootAfterSession(); toast("Browser connected","success"); }
    catch(error){ if(errorNode) errorNode.textContent=error.message||"That code did not work. Create a new one on your phone."; if(button){button.disabled=false; button.innerHTML=U.icon("check")+"<span>Connect browser</span>";} input.select(); }
  }
  async function emailLogin(){
    var emailInput=document.getElementById("login-email"); var passInput=document.getElementById("login-password"); var errorNode=document.getElementById("login-error"); if(!emailInput||!passInput) return;
    var email=emailInput.value.trim(); var password=passInput.value;
    if(!email||!password){ if(errorNode) errorNode.textContent="Enter your email and password."; (email||!password?emailInput:passInput).focus(); return; }
    var button=document.querySelector('[data-action="email-login"]'); if(button){ button.disabled=true; button.innerHTML=U.icon("refresh")+"<span>Signing in…</span>"; }
    try{ await Api.login(email,password); if(errorNode) errorNode.textContent=""; state.tab="chats"; stopQr(); await bootAfterSession(); toast("Signed in — this browser is saved","success"); }
    catch(error){ if(errorNode) errorNode.textContent=error.message||"Sign-in failed. Check your email and password."; if(button){button.disabled=false; button.innerHTML=U.icon("lock")+"<span>Sign in &amp; save this browser</span>";} passInput.value=""; passInput.focus(); }
  }
  function setAuthMode(mode){
    mode=mode||"code";
    state.authMode=mode;
    try{ localStorage.setItem("convo.authMode", mode); }catch(_){}
    var card=document.getElementById("auth-card"); if(!card) return;
    card.setAttribute("data-auth-mode", mode);
    Array.prototype.forEach.call(card.querySelectorAll(".auth-tab"), function(tab){ tab.classList.toggle("active", tab.dataset.mode===mode); });
    if(mode==="qr") initQrOnLogin(); else stopQr();
    // Focus first input of the active pane so keyboard shows on mobile
    setTimeout(function(){
      var focusId = mode==="code" ? "pair-code" : mode==="signin" ? "login-email" : mode==="signup" ? "signup-name" : "";
      if(focusId){ var el=document.getElementById(focusId); if(el) try{ el.focus(); }catch(_){} }
    }, 80);
  }
  async function handleForgotPassword(){
    var emailInput=document.getElementById("login-email");
    var email=emailInput?emailInput.value.trim():"";
    if(!email) { await U.prompt({title:"Reset password",label:"Enter your email",placeholder:"you@example.com",help:"We will send a reset link if this email is registered."}).then(function(v){ if(v) email=v.trim(); }); if(!email) return; }
    try{
      // Use Firebase Auth REST via Worker or direct
      var payload=await Api.requestPasswordReset(email);
      toast("If this email is registered, a reset link has been sent","success");
    }catch(e){
      // Fallback: direct Firebase API
      try{
        var apiKey="AIzaSyAw1P7TLWfkLXS1f0qjrLUSwkfVEOhLxfk";
        var res=await fetch("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key="+apiKey, {method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify({requestType:"PASSWORD_RESET", email: email})});
        var j=await res.json();
        if(res.ok) toast("Reset link sent to "+email,"success");
        else toast(j.error&&j.error.message||"Could not send reset link","error");
      }catch(err){ toast(err.message||"Reset failed","error"); }
    }
  }
  async function emailSignup(){
    var nameInput=document.getElementById("signup-name"); var emailInput=document.getElementById("signup-email"); var passInput=document.getElementById("signup-password"); var errorNode=document.getElementById("signup-error"); if(!nameInput||!emailInput||!passInput) return;
    var name=nameInput.value.trim(); var email=emailInput.value.trim(); var password=passInput.value;
    if(!name){ if(errorNode) errorNode.textContent="Please enter your name."; nameInput.focus(); return; }
    if(!email||!password){ if(errorNode) errorNode.textContent="Enter your email and a password."; (email?passInput:emailInput).focus(); return; }
    if(password.length<6){ if(errorNode) errorNode.textContent="Password must be at least 6 characters."; passInput.focus(); return; }
    var button=document.querySelector('[data-action="email-signup"]'); if(button){ button.disabled=true; button.innerHTML=U.icon("refresh")+"<span>Creating your account…</span>"; }
    try{ await Api.signup(name,email,password); if(errorNode) errorNode.textContent=""; state.tab="chats"; stopQr(); await bootAfterSession(); toast("Welcome to Convo Chat — your account is ready","success"); }
    catch(error){ if(errorNode) errorNode.textContent=error.message||"Could not create the account."; if(button){button.disabled=false; button.innerHTML=U.icon("check")+"<span>Create account &amp; sign in</span>";} }
  }

  // Real QR sign-in via Worker secure phone-approval API (v4.9.1)
  var qrTimer=0, qrPollTimer=0, qrCountdown=0;
  async function initQrOnLogin(){
    stopQr();
    var canvas=document.getElementById("qr-canvas");
    var statusText=document.getElementById("qr-status-text");
    var timerEl=document.getElementById("qr-timer");
    var dot=document.getElementById("qr-dot");
    var overlay=document.getElementById("qr-overlay");
    if(statusText) statusText.textContent="Creating secure QR…";
    if(dot) dot.className="qr-dot loading";
    try{
      var data=await Api.qrCreate();
      state.qrId=data.qrId||data.qr_id||"";
      state.qrData=data.qrData||data.qr_data||data.qrId||"";
      if(!state.qrId||!state.qrData) throw new Error("Worker did not return QR");
      // Draw QR
      if(canvas) drawQr(canvas, state.qrData);
      if(statusText) statusText.textContent="Scan with Convo Chat app → Device Login";
      if(dot) dot.className="qr-dot active";
      // Countdown 30s
      qrCountdown=30;
      if(timerEl) timerEl.textContent=qrCountdown+"s";
      if(overlay) overlay.style.display="none";
      clearInterval(qrTimer);
      qrTimer=setInterval(function(){
        qrCountdown--;
        if(timerEl) timerEl.textContent=qrCountdown+"s";
        var timerM=document.getElementById("qr-timer-text-m"); if(timerM) timerM.textContent=qrCountdown+"s";
        if(qrCountdown<=0){
          clearInterval(qrTimer);
          if(statusText) statusText.textContent="Generating new QR…";
          if(dot) dot.className="dot expired";
          if(overlay){ overlay.textContent="Expired"; overlay.style.display="grid"; }
          stopQrPoll();
          // Auto regenerate after 1s
          setTimeout(function(){ initQrOnLogin(); }, 1000);
        }
      },1000);
      // Poll every 2s
      clearInterval(qrPollTimer);
      qrPollTimer=setInterval(async function(){
        if(!state.qrId) return;
        try{
          var poll=await Api.qrPoll(state.qrId);
          if(poll.status==="authenticated"||poll.sessionToken){
            clearInterval(qrTimer); clearInterval(qrPollTimer);
            if(statusText) statusText.textContent="Approved — signing in…";
            if(dot) dot.className="qr-dot success";
            // Save session
            var sess={ token: poll.sessionToken, expiresAt: Number(poll.expiresAt||0), pairedAt: Date.now(), via: "qr" };
            Api.saveSession(sess);
            state.tab="chats"; stopQr();
            await bootAfterSession();
            toast("QR approved — welcome","success");
          } else if(poll.status==="scanned"){
            if(statusText) statusText.textContent="Scanned — confirm on phone…";
            if(dot) dot.className="qr-dot scanned";
          }
        }catch(e){
          // If 410 expired, show refresh
          if(e.status===410||(e.message||"").toLowerCase().indexOf("expired")>=0){
            clearInterval(qrTimer); clearInterval(qrPollTimer);
            if(statusText) statusText.textContent="QR expired — tap Refresh";
            if(dot) dot.className="qr-dot expired";
          }
        }
      },2000);
    }catch(error){
      if(statusText) statusText.textContent=error.message||"Could not create QR — use Pair code";
      if(dot) dot.className="qr-dot error";
      if(timerEl) timerEl.textContent="—";
    }
  }
  function drawQr(canvas, text){
    try{
      if(!canvas) canvas=document.getElementById("qr-canvas");
      var canvases = [canvas, document.getElementById("qr-canvas-m")].filter(Boolean);
      canvases.forEach(function(c){
        var ctx=c.getContext("2d");
        ctx.clearRect(0,0,c.width,c.height);
        ctx.fillStyle="#fff"; ctx.fillRect(0,0,c.width,c.height);
        var img=new Image();
        img.crossOrigin="anonymous";
        img.onload=(function(cv, ct){ return function(){ try{ ct.drawImage(img,0,0,cv.width,cv.height); }catch(_){} }; })(c, ctx);
        img.onerror=(function(cv, ct){ return function(){ ct.fillStyle="#111"; ct.font="12px sans-serif"; ct.textAlign="center"; ct.fillText(text.slice(0,24), cv.width/2, cv.height/2); }; })(c, ctx);
        img.src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data="+encodeURIComponent(text);
      });
    }catch(e){
      try{ var c2=document.getElementById("qr-canvas")||document.getElementById("qr-canvas-m"); if(c2){ var cx=c2.getContext("2d"); cx.fillStyle="#fff"; cx.fillRect(0,0,180,180); cx.fillStyle="#000"; cx.font="11px monospace"; cx.fillText(text.slice(0,20),10,90); } }catch(_){}
    }
  }
  function stopQrPoll(){ if(qrPollTimer){ clearInterval(qrPollTimer); qrPollTimer=0; } }
  function stopQr(){ state.qrId=""; state.qrData=""; stopQrPoll(); if(qrTimer){ clearInterval(qrTimer); qrTimer=0; } }

  /* -------------------------------------------------------------- lock/log */
  function armLockTimer(){ if(timers.lock) clearTimeout(timers.lock); if(!session()) return; var minutes=Number(S.settings().autoLockMinutes||0); if(!minutes) return; timers.lock=setTimeout(function(){ if(document.hidden) return; lock(); }, minutes*60000); }
  function lock(){ if(!session()) return; state.locked=true; stopPolls(); renderPane(); }
  function unlock(){ state.locked=false; renderPane(); startPolls(); armLockTimer(); refreshChats(true); }

  /* --------------------------------------------------------------- events */
  function bindGlobalEvents(){
    // Prevent form submission refresh (critical fix)
    document.addEventListener("submit", function(e){ e.preventDefault(); e.stopPropagation(); }, true);

    document.addEventListener("click", function(event){
      var target=event.target;
      // Don't interfere with text input focus - but still allow button clicks inside same card
      // Only skip if the click is directly on an input element itself, not its container
      if(target.tagName==="INPUT" || target.tagName==="TEXTAREA" || target.tagName==="SELECT" || target.isContentEditable){
        return;
      }
      if(!target.closest(".popover") && !target.closest('[data-action="emoji"]') && !target.closest('[data-bubble]')) closeOverlay();
      var tabButton=target.closest("[data-tab]"); if(tabButton){ state.tab=tabButton.dataset.tab; state.settingsSection=""; if(state.tab!=="chats") state.activePeer=state.tab==="search"?"":state.activePeer; if(state.tab==="search") refreshFriendRequests(); renderPane(); return; }
      var navButton=target.closest("[data-nav]"); if(navButton){ handleNav(navButton.dataset.nav); return; }
      var chatButton=target.closest("[data-chat]"); if(chatButton){ openChat(chatButton.dataset.chat); return; }
      var filterButton=target.closest("[data-filter]"); if(filterButton){ state.chatFilter=filterButton.dataset.filter; renderPane(); return; }
      var actionButton=target.closest("[data-action]"); if(actionButton){ event.preventDefault(); handleAction(actionButton, event); return; }
      var themeButton=target.closest("[data-theme-id]"); if(themeButton){ S.setSetting("theme", themeButton.dataset.themeId); renderPane(); return; }
      var bubbleStyle=target.closest("[data-bubble-style]"); if(bubbleStyle){ S.setSetting("bubbleStyle", bubbleStyle.dataset.bubbleStyle); renderPane(); return; }
      var tint=target.closest("[data-bubble-tint]"); if(tint){ S.setSetting("bubbleTint", tint.dataset.bubbleTint); renderPane(); return; }
      var corners=target.closest("[data-corners]"); if(corners){ S.setSetting("cornerStyle", corners.dataset.corners); renderPane(); return; }
      var wallpaper=target.closest("[data-wallpaper]"); if(wallpaper){ S.setSetting("wallpaper", wallpaper.dataset.wallpaper); S.setSetting("wallpaperUrl",""); renderPane(); return; }
      var density=target.closest("[data-density]"); if(density){ S.setSetting("density", density.dataset.density); renderPane(); return; }
      var emptyAction=target.closest("[data-empty-action]"); if(emptyAction){ state.tab="search"; renderPane(); return; }
      var zoom=target.closest("[data-zoom]"); if(zoom){ U.modal({ title:"Photo", body:'<img class="zoom" src="'+S.escapeHtml(zoom.dataset.zoom)+'" alt="Photo">', actions:[{label:"Close",variant:"ghost"}, {label:"Open original",variant:"primary",onClick:function(){ window.open(zoom.dataset.zoom,"_blank","noopener"); }}], wide:true }); return; }
      var reaction=target.closest("[data-react]"); if(reaction){ react(reaction.dataset.msg, reaction.dataset.react); return; }
      var bubble=target.closest("[data-bubble]"); if(bubble){ openMessageMenu(bubble.dataset.bubble, bubble); return; }
      var preview=target.closest("[data-preview]"); if(preview){ event.preventDefault(); previewFile(preview.dataset.preview, preview.dataset.mime||""); return; }
    });

    document.addEventListener("input", function(event){
      var target=event.target;
      if(target.id==="chat-search"){ state.searchQuery=target.value; refreshChatListOnly(); var box=document.getElementById("chat-search"); if(box){ box.focus(); box.setSelectionRange(box.value.length, box.value.length); } return; }
      if(target.id==="people-search"){ state.searchQuery=target.value; schedulePeopleSearch(); return; }
      if(target.id==="composer-input"){ state.drafts[state.activePeer]=target.value; autosize(target); signalTyping(Boolean(target.value.trim())); var send=document.querySelector(".send-btn"); if(send) send.classList.toggle("rec", !target.value.trim()); return; }
      if(target.id==="pair-code"){ target.value=target.value.replace(/\D/g,"").slice(0,10); if(target.value.length===10) redeemCode(); return; }
      if(target.matches("[data-setting-range]")){ S.setSetting(target.dataset.settingRange, Number(target.value)); var out=target.parentElement.querySelector("output"); if(out) out.textContent=Math.round(Number(target.value)*100)+"%"; return; }
    });
    document.addEventListener("change", function(event){
      var toggle=event.target.closest("[data-toggle]"); if(toggle){ handleSettingToggle(toggle.dataset.toggle, toggle.checked); return; }
      // File input handled in bindFileDropZone
    });
    document.addEventListener("keydown", function(event){
      if(event.key==="Enter" && event.target.id==="pair-code"){ event.preventDefault(); redeemCode(); return; }
      if(event.key==="Enter" && (event.target.id==="login-email" || event.target.id==="login-password")){ event.preventDefault(); if(event.target.id==="login-email"){ var lp=document.getElementById("login-password"); if(lp) lp.focus(); } else emailLogin(); return; }
      if(event.key==="Enter" && /^signup-/.test(event.target.id||"")){ event.preventDefault(); var order=["signup-name","signup-email","signup-password"]; var idx=order.indexOf(event.target.id); if(idx>=0 && idx<2){ var nx=document.getElementById(order[idx+1]); if(nx) nx.focus(); } else emailSignup(); return; }
      if(event.target.id!=="composer-input") return;
      var sendOnEnter=S.settings().sendOnEnter;
      if(event.key==="Enter" && !event.shiftKey && sendOnEnter){ event.preventDefault(); sendMessage(); }
      else if(event.key==="Enter" && event.shiftKey && !sendOnEnter){ event.preventDefault(); sendMessage(); }
      else if(event.key==="Escape" && state.replyTo){ state.replyTo=null; renderComposerOnly(); }
      else if(event.key==="ArrowUp" && !event.target.value && state.messages.length){ var mine=state.messages.filter(function(m){return m.senderId===state.me.uid;}).slice(-1)[0]; if(mine){ event.preventDefault(); state.replyTo={messageId:mine.messageId,text:mine.text,senderName:mine.senderName}; renderComposerOnly(); } }
    });

    // Keyboard-aware layout (improved)
    (function trackKeyboard(){
      var vv=window.visualViewport; var root=document.documentElement;
      function apply(){
        var h=vv ? vv.height : window.innerHeight;
        var kb=vv ? Math.max(0, window.innerHeight - vv.height - vv.offsetTop) : 0;
        root.style.setProperty("--app-h", Math.round(h)+"px");
        root.style.setProperty("--kb", Math.round(kb)+"px");
        document.body.classList.toggle("kb-open", kb>80);
        if(kb>80){
          var el=document.activeElement;
          if(el && el.id==="composer-input"){
            var sc=document.getElementById("msg-scroll");
            if(sc && sc.scrollHeight - sc.scrollTop - sc.clientHeight < 300) setTimeout(function(){ if(sc) sc.scrollTop=sc.scrollHeight; }, 100);
          } else if(el && /INPUT|TEXTAREA/.test(el.tagName)){
            try{ el.scrollIntoView({block:"center",behavior:"smooth"}); }catch(e){}
          }
        }
      }
      if(vv){ vv.addEventListener("resize", apply); vv.addEventListener("scroll", apply); }
      window.addEventListener("resize", apply);
      document.addEventListener("focusin", function(){ setTimeout(apply, 250); setTimeout(apply, 600); });
      document.addEventListener("focusout", function(){ setTimeout(apply, 100); });
      apply();
    })();

    document.addEventListener("pointerdown", function once(){ askNotificationPermission(); document.removeEventListener("pointerdown", once); });
    window.addEventListener("online", function(){ state.online=true; toast("Back online","success"); startPolls(); refreshChats(true); });
    window.addEventListener("offline", function(){ state.online=false; toast("You are offline — messages will stay on this device","error"); });
    document.addEventListener("visibilitychange", function(){
      if(document.hidden){ if(S.settings().lockOnHide) state.hiddenAt=Date.now(); }
      else { if(S.settings().lockOnHide && state.hiddenAt && Date.now()-state.hiddenAt>120000) lock(); if(!state.locked){ refreshChats(true); if(state.activePeer) pollMessages(); pollPresence(); } armLockTimer(); }
    });
    ["pointerdown","keydown","wheel","touchstart"].forEach(function(name){ document.addEventListener(name, function(){ if(!state.locked) armLockTimer(); }, {passive:true}); });
    document.addEventListener("scroll", function(event){
      if(event.target && event.target.id==="msg-scroll"){
        var el=event.target; var atBottom=(el.scrollHeight-el.scrollTop-el.clientHeight)<120;
        window.__convoUserScrollingUntil=atBottom?0:Date.now()+1800;
        var button=document.getElementById("scroll-bottom"); if(button) button.classList.toggle("show", !atBottom);
        if(el.scrollTop<90 && state.hasMoreMessages && !loadingOlder) loadMessages(true);
      }
    }, true);
    window.addEventListener("resize", function(){ var back=document.getElementById("chat-back"); if(back) back.style.display=isMobile()?"inline-flex":"none"; });
    window.addEventListener("convo:session-expired", function(){ stopPolls(); stopQr(); state.chats=[]; state.messages=[]; state.activePeer=""; renderPane(); toast("Your browser session ended. Sign in again to continue.","error"); });
  }

  function handleNav(nav){
    if(nav==="settings"){ state.settingsSection=""; state.tab="settings"; renderPane(); return; }
    if(nav==="logout"){ logout(); return; }
    if(nav==="logoutall"){ logoutEverywhere(); return; }
    if(nav==="export"){ exportData(); return; }
    if(nav==="clearcache"){ U.confirm({title:"Clear local cache?",message:"Messages cached in this browser are removed. They stay safe on your phone.",confirmLabel:"Clear"}).then(function(ok){ if(ok){ S.cacheClear(); state.chats=[]; refreshChats(); toast("Cache cleared","success"); } }); return; }
    if(nav==="autolock"){ U.sheetMenu([{icon:"sparkle",label:"Off",onClick:function(){S.setSetting("autoLockMinutes",0); renderPane();}}, {icon:"clock",label:"After 5 minutes",onClick:function(){S.setSetting("autoLockMinutes",5); renderPane();}}, {icon:"clock",label:"After 15 minutes",onClick:function(){S.setSetting("autoLockMinutes",15); renderPane();}}, {icon:"clock",label:"After 1 hour",onClick:function(){S.setSetting("autoLockMinutes",60); renderPane();}}], "Auto-lock"); return; }
    if(nav==="wallpaper-photo"){ U.prompt({title:"Photo wallpaper",label:"Secure https image link",placeholder:"https://…",help:"Use a link to a photo you own. It stays on this browser."}).then(function(url){ if(!url) return; if(!/^https:\/\//i.test(url.trim())){ toast("Only https links are allowed","error"); return; } S.setSetting("wallpaperUrl",url.trim()); S.setSetting("wallpaper","aurora"); renderPane(); toast("Wallpaper updated","success"); }); return; }
    if(nav==="wallpaper-clear"){ S.setSetting("wallpaperUrl",""); renderPane(); return; }
    if(nav==="profile"){ state.tab="profile"; renderPane(); return; }
    state.settingsSection=nav; state.tab="settings"; renderPane();
  }
  function handleAction(button, ev){
    if(ev) ev.preventDefault();
    var action=button.dataset.action;
    if(action==="refresh"){ refreshChats(); toast("Refreshed","success"); return; }
    if(action==="clear-search"){ state.searchQuery=""; state.searchResults=[]; renderPane(); return; }
    if(action==="open-search"){ state.tab="search"; state.searchQuery=""; renderPane(); return; }
    if(action==="back"){ state.activePeer=""; renderPane(); return; }
    if(action==="send"){ sendMessage(); return; }
    if(action==="emoji"){ openEmojiPicker(button); return; }
    if(action==="attach"){ openAttachMenu(button); return; }
    if(action==="cancel-reply"){ state.replyTo=null; renderComposerOnly(); return; }
    if(action==="chat-info"){ openChatInfo(); return; }
    if(action==="chat-menu"){ U.sheetMenu([{icon:"history",label:"Reload messages",onClick:function(){loadMessages();}}, {icon:"search",label:"Search in chat",onClick:function(){toast("Search inside a chat is coming to the web build","info");}}, {icon:"bellOff",label:"Mute in app",hint:"Android",onClick:function(){toast("Mute controls live in the Convo Chat app","info");}}, {separator:true}, {icon:"info",label:"Conversation info",onClick:openChatInfo}], state.activePeerName||"Chat"); return; }
    if(action==="chat-search"){ toast("Search inside a conversation is coming soon to the web build","info"); return; }
    if(action==="scroll-bottom"){ var scroller=document.getElementById("msg-scroll"); if(scroller) scroller.scrollTo({top:scroller.scrollHeight, behavior:S.settings().animations?"smooth":"auto"}); return; }
    if(action==="load-older"){ loadMessages(true); return; }
    if(action==="save-profile"||action==="edit-profile"){ editProfile(); return; }
    if(action==="change-avatar"){ changeAvatar(); return; }
    if(action==="share-profile"){ shareProfile(); return; }
    if(action==="copy-profile-link"){ copyProfileLink(); return; }
    if(action==="pair"){ redeemCode(); return; }
    if(action==="email-login"){ emailLogin(); return; }

    if(action==="forgot-password"){ handleForgotPassword(); return; }
    if(action==="email-signup"){ emailSignup(); return; }
    if(action==="friend-add"){ addFriend(button.dataset.uid, button); return; }
    if(action==="friend-accept"){ respondFriend(button.dataset.request, true); return; }
    if(action==="friend-decline"){ respondFriend(button.dataset.request, false); return; }
    if(action==="auth-mode"){ setAuthMode(button.dataset.mode); return; }
    if(action==="qr-refresh"){ initQrOnLogin(); return; }
    if(action==="unlock"){ unlock(); return; }
    if(action==="logout"){ logout(); return; }
  }

  var peopleTimer=0, peopleSeq=0;
  function schedulePeopleSearch(){
    if(peopleTimer) clearTimeout(peopleTimer);
    peopleTimer=setTimeout(function(){
      var query=(state.searchQuery||"").trim().toLowerCase();
      state.searchResults=query ? state.chats.filter(function(c){ return (c.name||"").toLowerCase().indexOf(query)>=0 || (c.username||"").toLowerCase().indexOf(query)>=0; }) : [];
      var global=query.replace(/^@/,""); state.people=[]; state.peopleLoading=global.length>=3; rerenderSearchKeepFocus();
      if(global.length>=3){
        var seq=++peopleSeq;
        Api.find(query).then(function(data){ if(seq!==peopleSeq) return; state.people=(data.people||[]).filter(function(p){return !p.friend;}); state.peopleLoading=false; rerenderSearchKeepFocus(); }).catch(function(){ if(seq===peopleSeq){ state.peopleLoading=false; rerenderSearchKeepFocus(); } });
      }
    },260);
  }
  function rerenderSearchKeepFocus(){
    if(state.tab!=="search") return;
    var input=document.getElementById("people-search"); var hadFocus=input && document.activeElement===input;
    renderPane(); input=document.getElementById("people-search");
    if(input && hadFocus){ input.focus(); input.setSelectionRange(input.value.length, input.value.length); }
  }
  var knownRequestIds=null;
  async function refreshFriendRequests(){
    if(!session()||state.locked) return;
    try{
      var data=await Api.friendRequests(); var list=data.incoming||[];
      if(knownRequestIds){ list.forEach(function(r){ if(knownRequestIds.indexOf(r.id)>=0) return; toast(r.name+" sent you a friend request","info"); if(document.hidden && "Notification" in window && Notification.permission==="granted"){ try{ new Notification("Convo Chat",{body:r.name+" sent you a friend request",tag:"convo-fr-"+r.id}); }catch(e){} } });
      }
      knownRequestIds=list.map(function(r){return r.id;}); state.friendRequests=list; renderNav(); if(state.tab==="search") rerenderSearchKeepFocus();
    }catch(e){}
  }
  async function addFriend(uid, button){
    if(!uid) return; if(button){ button.disabled=true; button.innerHTML=U.icon("refresh")+"<span>Sending…</span>"; }
    try{ var data=await Api.friendRequest(uid); state.people=state.people.map(function(p){return p.uid===uid?Object.assign({},p,{requested:!data.friend,friend:Boolean(data.friend)}):p;}); toast(data.friend?"You are now friends — say hello!":"Friend request sent","success"); if(data.friend) await refreshChats(true); rerenderSearchKeepFocus(); }catch(error){ toast(error.message||"Could not send the request","error"); if(button){ button.disabled=false; button.innerHTML=U.icon("plus")+"<span>Add friend</span>"; } }
  }
  async function respondFriend(id,accept){
    try{
      await Api.friendRespond(id,accept);
      var req=state.friendRequests.filter(function(r){return r.id===id;})[0];
      state.friendRequests=state.friendRequests.filter(function(r){return r.id!==id;});
      knownRequestIds=(knownRequestIds||[]).filter(function(x){return x!==id;});
      toast(accept?"Friend added":"Request declined", accept?"success":"info");
      if(accept){ await refreshChats(true); if(req){ state.tab="chats"; openChat(req.uid,req.name,req.profileImageUrl); return; } }
      rerenderSearchKeepFocus(); renderNav();
    }catch(error){ toast(error.message||"Could not update the request","error"); }
  }
  function signalTyping(active){
    if(!S.settings().typingIndicator||!state.activePeer) return;
    var now=Date.now(); if(active && now-lastTypingSentAt>3500){ lastTypingSentAt=now; Api.typing(state.activePeer).catch(function(){}); }
    if(typingStopTimer) clearTimeout(typingStopTimer); if(active) typingStopTimer=setTimeout(function(){ lastTypingSentAt=0; },6000);
  }

  /* ------------------------------------------------------------------ boot */
  async function bootAfterSession(){
    state.loadingChats=true;
    var ok=await loadBootstrap(); if(!ok){ Api.clearSession(); renderPane(); return; }
    renderPane(); startPolls(); armLockTimer();
    if(state.chats.length && !isMobile()){ var first=state.chats.slice().sort(function(a,b){return Number(b.lastAt||0)-Number(a.lastAt||0);})[0]; if(first) openChat(first.uid); }
  }
  async function start(){
    S.loadSettings();
    var stopParticles=particles();
    setLaunchProgress(.08,"Waking up your workspace…");
    restoreCache(); bindGlobalEvents();
    if(!session()){
      await new Promise(function(r){setTimeout(r,260);});
      setLaunchProgress(prefix(.55),"Ready when you are");
      await new Promise(function(r){setTimeout(r, Math.max(0, LAUNCH_MIN_MS - (Date.now()-launchStartedAt)));});
      finishLaunch(); stopParticles(); renderPane();
      toast("Sign in, create an account, or pair with your phone","info");
      return;
    }
    setLaunchProgress(prefix(.3),"Checking your secure session…");
    var cached=S.cacheRead(); if(cached&&cached.chats){ state.chats=cached.chats; unreadTotal(); }
    var ok=await loadBootstrap();
    if(!ok){ Api.clearSession(); setLaunchProgress(1); await new Promise(function(r){setTimeout(r,240);}); finishLaunch(); stopParticles(); renderPane(); toast("Session expired — pair this browser again","error"); return; }
    renderPane(); setLaunchProgress(1,"Welcome back");
    await new Promise(function(r){setTimeout(r, Math.max(0, LAUNCH_MIN_MS - (Date.now()-launchStartedAt)));});
    finishLaunch(); stopParticles(); startPolls(); armLockTimer();
    if(state.chats.length && !isMobile()){ var first=state.chats.slice().sort(function(a,b){return Number(b.lastAt||0)-Number(a.lastAt||0);})[0]; if(first) openChat(first.uid); }
  }
  window.ConvoApp = { state: state };
  window.addEventListener("DOMContentLoaded", function(){ start().catch(function(error){ setLaunchProgress(1,"Something went wrong"); finishLaunch(); toast("Could not start Convo Chat Web: "+(error.message||"unknown error"),"error"); }); });
})();
