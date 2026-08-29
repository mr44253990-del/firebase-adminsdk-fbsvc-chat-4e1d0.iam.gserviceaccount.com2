"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChatSummary, MessageItem, PublicUser } from "@/lib/queries";
import { Avatar, Sheet, Shimmer } from "@/components/ui";
import { accentGradient } from "@/lib/themes";
import { clockTime, fileToDataUrl, presenceLabel, timeAgo } from "@/lib/format";
import {
  IconBack,
  IconCheck,
  IconClose,
  IconEdit,
  IconForward,
  IconGroup,
  IconHeart,
  IconImage,
  IconMic,
  IconReply,
  IconSearch,
  IconSend,
  IconTrash,
} from "@/components/icons";

export function MessagesView({
  me,
  people,
  onOpenProfile,
  onRefreshBadges,
}: {
  me: PublicUser;
  people: PublicUser[];
  onOpenProfile: (id: string) => void;
  onRefreshBadges: () => void;
}) {
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [openId, setOpenId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [groupSheet, setGroupSheet] = useState(false);

  const load = useCallback(async () => {
    const res = await fetch("/api/chats");
    if (res.ok) setChats((await res.json()).chats as ChatSummary[]);
    setLoading(false);
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    const t = setInterval(load, 12000);
    return () => clearInterval(t);
  }, [load]);

  const open = chats.find((c) => c.id === openId) ?? null;
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return chats;
    return chats.filter((c) =>
      (c.kind === "group" ? c.title : c.people.map((p) => p.name).join(" ")).toLowerCase().includes(q),
    );
  }, [chats, query]);

  if (open) {
    return (
      <ChatThread
        chat={open}
        me={me}
        chats={chats}
        onBack={() => {
          setOpenId(null);
          load();
          onRefreshBadges();
        }}
        onOpenProfile={onOpenProfile}
        people={people}
      />
    );
  }

  return (
    <div className="space-y-3">
      <div className="glass px-4 py-4">
        <div className="flex items-center gap-3">
          <h2 className="text-xl font-black tracking-tight">Messages</h2>
          <button
            onClick={() => setGroupSheet(true)}
            className="chip tap ml-auto flex items-center gap-1.5 px-3 py-2 text-[11.5px] font-bold"
          >
            <IconGroup size={14} /> New group
          </button>
        </div>
        <div className="field mt-3 flex items-center gap-2 px-3.5 py-2.5">
          <IconSearch size={16} className="text-[color:var(--muted)]" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search chats and groups"
            className="flex-1 bg-transparent text-[13px] outline-none placeholder:text-[color:var(--muted)]"
          />
        </div>
      </div>

      <div className="glass overflow-hidden">
        {loading && chats.length === 0
          ? Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-3.5">
                <Shimmer className="h-12 w-12 rounded-full" />
                <div className="flex-1 space-y-2">
                  <Shimmer className="h-3 w-28" />
                  <Shimmer className="h-2.5 w-44" />
                </div>
              </div>
            ))
          : filtered.map((chat, i) => {
              const other = chat.people[0];
              const label = chat.kind === "group" ? chat.title : other?.name ?? "Unknown";
              const unread = chat.unread > 0;
              return (
                <button
                  key={chat.id}
                  onClick={() => setOpenId(chat.id)}
                  className="anim-up relative flex w-full items-center gap-3 border-b border-white/6 px-4 py-3.5 text-left last:border-0"
                  style={{
                    animationDelay: `${Math.min(i, 8) * 45}ms`,
                    background: unread ? "linear-gradient(90deg, rgba(255,77,109,.10), transparent 55%)" : undefined,
                  }}
                >
                  {chat.kind === "group" ? (
                    <div
                      className="grid h-12 w-12 shrink-0 place-items-center overflow-hidden rounded-2xl"
                      style={{ background: accentGradient(chat.accent) }}
                    >
                      {chat.coverUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={chat.coverUrl} alt="" className="h-full w-full object-cover" />
                      ) : (
                        <IconGroup size={20} className="text-black/70" />
                      )}
                    </div>
                  ) : (
                    <Avatar
                      name={label}
                      src={other?.avatarUrl}
                      accent={other?.accent}
                      size={48}
                      ring={unread ? "aurora" : "none"}
                      online={other?.online}
                    />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className={`truncate text-[14px] ${unread ? "font-black" : "font-semibold"}`}>{label}</p>
                      {chat.kind === "group" && (
                        <span className="chip px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider">
                          {chat.people.length}
                        </span>
                      )}
                      <span className="ml-auto shrink-0 text-[10.5px] text-[color:var(--muted)]">
                        {chat.lastMessage ? timeAgo(chat.lastMessage.createdAt) : ""}
                      </span>
                    </div>
                    <p
                      className="truncate text-[12.5px]"
                      style={{ color: unread ? "var(--fg)" : "var(--muted)", opacity: unread ? 0.95 : 1 }}
                    >
                      {chat.lastMessage
                        ? `${chat.lastMessage.senderId === me.id ? "You: " : ""}${
                            chat.lastMessage.body || `[${chat.lastMessage.mediaKind ?? "media"}]`
                          }`
                        : "Say hello"}
                    </p>
                  </div>
                  {unread && (
                    <span
                      className="unread-glow grid h-6 min-w-6 place-items-center rounded-full px-1.5 text-[10.5px] font-black"
                      style={{ background: "#ff4d6d", color: "#fff" }}
                    >
                      {chat.unread}
                    </span>
                  )}
                </button>
              );
            })}
        {!loading && filtered.length === 0 && (
          <p className="px-4 py-10 text-center text-[13px] text-[color:var(--muted)]">
            No conversations yet. Open a profile and start one.
          </p>
        )}
      </div>

      <GroupSheet
        open={groupSheet}
        people={people}
        onClose={() => setGroupSheet(false)}
        onCreated={() => {
          setGroupSheet(false);
          load();
        }}
      />
    </div>
  );
}

function GroupSheet({
  open,
  people,
  onClose,
  onCreated,
}: {
  open: boolean;
  people: PublicUser[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const [title, setTitle] = useState("");
  const [picked, setPicked] = useState<string[]>([]);
  const [cover, setCover] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  async function create() {
    setBusy(true);
    let coverUrl = "";
    if (cover) {
      const res = await fetch("/api/upload", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dataUrl: cover, bucket: "groups", kind: "image" }),
      });
      if (res.ok) coverUrl = (await res.json()).url;
    }
    await fetch("/api/chats", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "create-group", title, memberIds: picked, coverUrl }),
    });
    setBusy(false);
    setTitle("");
    setPicked([]);
    setCover(null);
    onCreated();
  }

  return (
    <Sheet open={open} onClose={onClose} title="Create a group">
      <button
        onClick={() => fileRef.current?.click()}
        className="tap relative grid h-32 w-full place-items-center overflow-hidden rounded-2xl"
        style={{ background: cover ? "#000" : "color-mix(in srgb, var(--fg) 8%, transparent)", border: "1px dashed var(--glass-border)" }}
      >
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={cover} alt="" className="h-full w-full object-cover" />
        ) : (
          <span className="flex items-center gap-2 text-[12px] font-semibold text-[color:var(--muted)]">
            <IconImage size={16} /> Background cover
          </span>
        )}
      </button>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        hidden
        onChange={async (e) => {
          const f = e.target.files?.[0];
          if (f) setCover(await fileToDataUrl(f));
        }}
      />

      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Group name"
        className="field mt-3 w-full bg-transparent px-4 py-3 text-[14px] outline-none placeholder:text-[color:var(--muted)]"
      />

      <p className="mt-4 mb-2 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
        Add people ({picked.length})
      </p>
      <div className="space-y-1.5">
        {people.map((p) => {
          const on = picked.includes(p.id);
          return (
            <button
              key={p.id}
              onClick={() => setPicked((v) => (on ? v.filter((x) => x !== p.id) : [...v, p.id]))}
              className="glass-soft tap flex w-full items-center gap-3 px-3 py-2.5 text-left"
              style={on ? { borderColor: "var(--p)" } : undefined}
            >
              <Avatar name={p.name} src={p.avatarUrl} accent={p.accent} size={36} online={p.online} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-semibold">{p.name}</p>
                <p className="text-[10.5px] text-[color:var(--muted)]">@{p.handle}</p>
              </div>
              <span
                className="grid h-6 w-6 place-items-center rounded-full"
                style={{ background: on ? accentGradient(p.accent) : "color-mix(in srgb, var(--fg) 10%, transparent)" }}
              >
                {on && <IconCheck size={13} className="text-black/80" />}
              </span>
            </button>
          );
        })}
      </div>

      <button
        onClick={create}
        disabled={busy || !title.trim()}
        className="btn-primary mt-5 w-full py-3 text-sm disabled:opacity-45"
      >
        {busy ? "Creating…" : "Create group"}
      </button>
      <p className="mt-3 pb-2 text-center text-[11px] leading-relaxed text-[color:var(--muted)]">
        Group messages never raise notifications — only one-to-one threads do.
      </p>
    </Sheet>
  );
}

function ChatThread({
  chat,
  me,
  chats,
  people,
  onBack,
  onOpenProfile,
}: {
  chat: ChatSummary;
  me: PublicUser;
  chats: ChatSummary[];
  people: PublicUser[];
  onBack: () => void;
  onOpenProfile: (id: string) => void;
}) {
  const [items, setItems] = useState<MessageItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState("");
  const [replyTo, setReplyTo] = useState<MessageItem | null>(null);
  const [editTarget, setEditTarget] = useState<MessageItem | null>(null);
  const [forwardTarget, setForwardTarget] = useState<MessageItem | null>(null);
  const [recording, setRecording] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const chunks = useRef<Blob[]>([]);

  const other = chat.people.find((p) => p.id !== me.id) ?? chat.people[0];
  const title = chat.kind === "group" ? chat.title : other?.name ?? "Chat";

  const load = useCallback(
    async (scroll = false) => {
      const res = await fetch(`/api/chats?id=${chat.id}`);
      if (res.ok) setItems((await res.json()).messages as MessageItem[]);
      setLoading(false);
      if (scroll) requestAnimationFrame(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }));
    },
    [chat.id],
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(true);
    fetch("/api/chats", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "read", chatId: chat.id }),
    });
    const t = setInterval(() => load(false), 8000);
    return () => clearInterval(t);
  }, [load, chat.id]);

  async function send(extra?: { mediaUrl: string; mediaKind: string }) {
    const text = draft.trim();
    if (!text && !extra) return;
    setDraft("");
    const reply = replyTo;
    setReplyTo(null);
    await fetch("/api/chats", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        action: "send",
        chatId: chat.id,
        body: text,
        replyToId: reply?.id ?? "",
        mediaUrl: extra?.mediaUrl ?? "",
        mediaKind: extra?.mediaKind ?? "",
      }),
    });
    load(true);
  }

  async function upload(dataUrl: string, kind: string, bucket: string) {
    const res = await fetch("/api/upload", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ dataUrl, kind, bucket }),
    });
    if (!res.ok) return;
    const json = await res.json();
    await send({ mediaUrl: json.url, mediaKind: json.kind });
  }

  async function toggleVoice() {
    if (recording) {
      recorder.current?.stop();
      setRecording(false);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      chunks.current = [];
      rec.ondataavailable = (e) => chunks.current.push(e.data);
      rec.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks.current, { type: "audio/webm" });
        const reader = new FileReader();
        reader.onload = () => upload(String(reader.result), "audio", "voice");
        reader.readAsDataURL(blob);
      };
      rec.start();
      recorder.current = rec;
      setRecording(true);
    } catch {
      setRecording(false);
    }
  }

  async function act(action: string, payload: Record<string, string>) {
    await fetch("/api/chats", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action, chatId: chat.id, ...payload }),
    });
    load(false);
  }

  return (
    <div className="glass flex h-[calc(100dvh-190px)] flex-col overflow-hidden sm:h-[calc(100dvh-170px)]">
      <header className="flex items-center gap-3 border-b border-white/8 px-3 py-3">
        <button onClick={onBack} className="chip tap p-2" aria-label="Back">
          <IconBack size={17} />
        </button>
        {chat.kind === "group" ? (
          <div className="grid h-10 w-10 shrink-0 place-items-center overflow-hidden rounded-xl" style={{ background: accentGradient(chat.accent) }}>
            {chat.coverUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={chat.coverUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <IconGroup size={18} className="text-black/70" />
            )}
          </div>
        ) : (
          <Avatar
            name={title}
            src={other?.avatarUrl}
            accent={other?.accent}
            size={40}
            ring="aurora"
            online={other?.online}
            onClick={() => other && onOpenProfile(other.id)}
          />
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-[14px] font-bold">{title}</p>
          <p className="truncate text-[11px] text-[color:var(--muted)]">
            {chat.kind === "group"
              ? `${chat.people.length} members · notifications off`
              : other
                ? presenceLabel(other.online, other.lastSeen, other.activityVisible)
                : ""}
          </p>
        </div>
      </header>

      <div className="scroller flex-1 space-y-1.5 px-3 py-3">
        {loading &&
          items.length === 0 &&
          Array.from({ length: 5 }).map((_, i) => (
            <Shimmer key={i} className="h-12" style={{ width: `${45 + i * 9}%`, marginLeft: i % 2 ? "auto" : 0 }} />
          ))}
        {items.map((m) => (
          <Bubble
            key={m.id}
            m={m}
            me={me}
            group={chat.kind === "group"}
            people={people}
            onReply={() => setReplyTo(m)}
            onReact={(r) => act("react-message", { messageId: m.id, reaction: r })}
            onEdit={() => setEditTarget(m)}
            onDelete={() => act("delete-message", { messageId: m.id })}
            onForward={() => setForwardTarget(m)}
          />
        ))}
        <div ref={bottomRef} />
      </div>

      {replyTo && (
        <div className="anim-pop mx-3 mb-2 flex items-center gap-2 rounded-xl px-3 py-2" style={{ background: "color-mix(in srgb, var(--p) 16%, transparent)" }}>
          <IconReply size={14} className="text-[color:var(--p)]" />
          <p className="min-w-0 flex-1 truncate text-[12px]">
            <span className="font-bold">Replying: </span>
            {replyTo.body || `[${replyTo.mediaKind ?? "media"}]`}
          </p>
          <button onClick={() => setReplyTo(null)} className="tap" aria-label="Cancel reply">
            <IconClose size={14} />
          </button>
        </div>
      )}

      <div className="flex items-end gap-2 border-t border-white/8 px-3 py-2.5">
        <button onClick={() => fileRef.current?.click()} className="chip tap p-2.5" aria-label="Attach image">
          <IconImage size={17} />
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="image/*,video/*"
          hidden
          onChange={async (e) => {
            const f = e.target.files?.[0];
            if (!f) return;
            const dataUrl = await fileToDataUrl(f);
            upload(dataUrl, f.type.startsWith("video/") ? "video" : "image", "posts");
          }}
        />
        <button
          onClick={toggleVoice}
          className="chip tap p-2.5"
          aria-label="Voice note"
          style={recording ? { background: "#ff4d6d", borderColor: "#ff4d6d", color: "#fff" } : undefined}
        >
          <IconMic size={17} />
        </button>
        <div className="field flex flex-1 items-center gap-2 px-3.5 py-2.5">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            placeholder={recording ? "Recording…" : "Message"}
            className="flex-1 bg-transparent text-[13.5px] outline-none placeholder:text-[color:var(--muted)]"
          />
        </div>
        <button onClick={() => send()} className="btn-primary tap grid h-11 w-11 place-items-center" style={{ borderRadius: 999 }} aria-label="Send">
          <IconSend size={18} />
        </button>
      </div>

      <Sheet open={!!editTarget} onClose={() => setEditTarget(null)} title="Edit message">
        <input
          defaultValue={editTarget?.body}
          onChange={(e) => editTarget && setEditTarget({ ...editTarget, body: e.target.value })}
          className="field w-full bg-transparent px-4 py-3 text-[14px] outline-none"
        />
        <button
          onClick={() => {
            if (editTarget) act("edit-message", { messageId: editTarget.id, body: editTarget.body });
            setEditTarget(null);
          }}
          className="btn-primary mt-4 w-full py-3 text-sm"
        >
          Save changes
        </button>
      </Sheet>

      <Sheet open={!!forwardTarget} onClose={() => setForwardTarget(null)} title="Forward to">
        <div className="space-y-1.5 pb-2">
          {chats.map((c) => (
            <button
              key={c.id}
              onClick={() => {
                if (forwardTarget) act("forward-message", { messageId: forwardTarget.id, targetChatId: c.id });
                setForwardTarget(null);
              }}
              className="glass-soft tap flex w-full items-center gap-3 px-3 py-2.5 text-left"
            >
              <Avatar
                name={c.kind === "group" ? c.title : c.people[0]?.name ?? "?"}
                src={c.kind === "group" ? c.coverUrl : c.people[0]?.avatarUrl}
                accent={c.accent}
                size={36}
              />
              <p className="text-[13px] font-semibold">
                {c.kind === "group" ? c.title : c.people[0]?.name ?? "Chat"}
              </p>
              <IconForward size={15} className="ml-auto text-[color:var(--muted)]" />
            </button>
          ))}
        </div>
      </Sheet>
    </div>
  );
}

function Bubble({
  m,
  me,
  group,
  people,
  onReply,
  onReact,
  onEdit,
  onDelete,
  onForward,
}: {
  m: MessageItem;
  me: PublicUser;
  group: boolean;
  people: PublicUser[];
  onReply: () => void;
  onReact: (r: string) => void;
  onEdit: () => void;
  onDelete: () => void;
  onForward: () => void;
}) {
  const [dx, setDx] = useState(0);
  const [menu, setMenu] = useState(false);
  const startX = useRef(0);
  const mine = m.senderId === me.id;
  const sender = people.find((p) => p.id === m.senderId);

  if (m.deleted) {
    return (
      <div className={`flex ${mine ? "justify-end" : "justify-start"}`}>
        <p className="px-3 py-1.5 text-[11.5px] italic text-[color:var(--muted)]">message removed</p>
      </div>
    );
  }

  return (
    <div className={`flex items-end gap-2 ${mine ? "justify-end" : "justify-start"}`}>
      {!mine && group && (
        <Avatar name={sender?.name ?? "?"} src={sender?.avatarUrl} accent={sender?.accent} size={26} />
      )}
      <div
        className="relative max-w-[80%]"
        style={{ transform: `translateX(${dx}px)`, transition: dx === 0 ? "transform .22s cubic-bezier(.22,1,.36,1)" : "none" }}
        onTouchStart={(e) => {
          startX.current = e.touches[0].clientX;
        }}
        onTouchMove={(e) => {
          const delta = e.touches[0].clientX - startX.current;
          if (delta > 0) setDx(Math.min(delta * 0.55, 62));
        }}
        onTouchEnd={() => {
          if (dx > 34) onReply();
          setDx(0);
        }}
        onDoubleClick={() => onReply()}
      >
        {dx > 8 && (
          <span className="absolute -left-8 bottom-3 text-[color:var(--p)]" style={{ opacity: dx / 62 }}>
            <IconReply size={17} />
          </span>
        )}
        <div
          onClick={() => setMenu((v) => !v)}
          className="glass-soft cursor-pointer px-3.5 py-2.5"
          style={{
            background: mine
              ? "linear-gradient(135deg, color-mix(in srgb, var(--p) 55%, transparent), color-mix(in srgb, var(--s) 38%, transparent))"
              : undefined,
            borderTopRightRadius: mine ? 6 : 20,
            borderTopLeftRadius: mine ? 20 : 6,
          }}
        >
          {!mine && group && (
            <p className="mb-0.5 text-[10.5px] font-bold" style={{ color: "var(--p)" }}>
              {sender?.name ?? "Unknown"}
            </p>
          )}
          {m.forwardedFrom && (
            <p className="mb-1 flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider opacity-70">
              <IconForward size={10} /> forwarded
            </p>
          )}
          {m.replyPreview && (
            <div className="mb-1.5 rounded-lg px-2 py-1.5" style={{ background: "rgba(0,0,0,.22)", borderLeft: "2.5px solid var(--s)" }}>
              <p className="text-[10.5px] font-bold opacity-85">{m.replyPreview.name}</p>
              <p className="truncate text-[11.5px] opacity-70">{m.replyPreview.body}</p>
            </div>
          )}
          {m.mediaUrl && m.mediaKind === "image" && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={m.mediaUrl} alt="" loading="lazy" className="mb-1 max-h-64 w-full rounded-xl object-cover" />
          )}
          {m.mediaUrl && m.mediaKind === "video" && (
            <video src={m.mediaUrl} controls playsInline className="mb-1 max-h-64 w-full rounded-xl" />
          )}
          {m.mediaUrl && m.mediaKind === "audio" && (
            <audio src={m.mediaUrl} controls className="mb-1 w-52" />
          )}
          {m.body && <p className="whitespace-pre-wrap text-[13.5px] leading-snug">{m.body}</p>}
          <p className="mt-1 text-right text-[9.5px] opacity-60">
            {clockTime(m.createdAt)}
            {m.edited && " · edited"}
          </p>
          {m.reaction && (
            <span className="absolute -bottom-2 left-2 grid h-5 w-5 place-items-center rounded-full" style={{ background: "var(--bg2)", border: "1px solid var(--glass-border)" }}>
              <IconHeart size={11} className="text-[#ff5f8f]" />
            </span>
          )}
        </div>

        {menu && (
          <div className="glass anim-pop absolute z-20 mt-1 flex gap-1 p-1.5" style={{ borderRadius: 999, right: mine ? 0 : "auto", left: mine ? "auto" : 0 }}>
            <MenuBtn onClick={() => { onReply(); setMenu(false); }} label="Reply"><IconReply size={15} /></MenuBtn>
            <MenuBtn onClick={() => { onReact("love"); setMenu(false); }} label="React"><IconHeart size={15} /></MenuBtn>
            <MenuBtn onClick={() => { onForward(); setMenu(false); }} label="Forward"><IconForward size={15} /></MenuBtn>
            {mine && <MenuBtn onClick={() => { onEdit(); setMenu(false); }} label="Edit"><IconEdit size={15} /></MenuBtn>}
            {mine && <MenuBtn onClick={() => { onDelete(); setMenu(false); }} label="Delete"><IconTrash size={15} /></MenuBtn>}
          </div>
        )}
      </div>
    </div>
  );
}

function MenuBtn({ children, onClick, label }: { children: React.ReactNode; onClick: () => void; label: string }) {
  return (
    <button onClick={onClick} title={label} aria-label={label} className="tap grid h-9 w-9 place-items-center rounded-full hover:bg-white/10">
      {children}
    </button>
  );
}
