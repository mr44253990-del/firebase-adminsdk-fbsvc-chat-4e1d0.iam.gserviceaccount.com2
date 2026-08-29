"use client";

import { useEffect, useRef, useState } from "react";
import type { FeedPost, PublicUser } from "@/lib/queries";
import { Avatar } from "@/components/ui";
import { accentGradient } from "@/lib/themes";
import { compact, timeAgo } from "@/lib/format";
import {
  IconComment,
  IconEdit,
  IconEye,
  IconFlame,
  IconGlobe,
  IconHeart,
  IconLock,
  IconSend,
  IconSmile,
  IconSpark,
  IconThumb,
  IconTrash,
} from "@/components/icons";

export const REACTIONS = [
  { key: "like", Icon: IconThumb, label: "Like", color: "#5b8cff" },
  { key: "love", Icon: IconHeart, label: "Love", color: "#ff5f8f" },
  { key: "fire", Icon: IconFlame, label: "Fire", color: "#ff9a3d" },
  { key: "wow", Icon: IconSpark, label: "Wow", color: "#ffd166" },
  { key: "haha", Icon: IconSmile, label: "Haha", color: "#4fe0c1" },
];

export function PostCard({
  post,
  me,
  onChanged,
  onOpenProfile,
  index,
}: {
  post: FeedPost;
  me: PublicUser;
  onChanged: () => void;
  onOpenProfile: (id: string) => void;
  index: number;
}) {
  const [myReaction, setMyReaction] = useState(post.myReaction);
  const [total, setTotal] = useState(post.reactionTotal);
  const [picker, setPicker] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [draft, setDraft] = useState("");
  const [editing, setEditing] = useState(false);
  const [editBody, setEditBody] = useState(post.body);
  const [visibility, setVisibility] = useState(post.visibility);
  const [busy, setBusy] = useState(false);
  const [burst, setBurst] = useState(0);
  const cardRef = useRef<HTMLElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const viewed = useRef(false);
  const mine = post.author.id === me.id;

  // Count a view once the card is 55% visible; autoplay video in the same pass.
  useEffect(() => {
    const el = cardRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            if (!viewed.current) {
              viewed.current = true;
              fetch("/api/posts", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action: "view", postId: post.id }),
              }).catch(() => {});
            }
            videoRef.current?.play().catch(() => {});
          } else {
            videoRef.current?.pause();
          }
        }
      },
      { threshold: 0.55 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [post.id]);

  async function react(kind: string) {
    setPicker(false);
    const removing = myReaction === kind;
    setMyReaction(removing ? null : kind);
    setTotal((t) => (removing ? Math.max(0, t - 1) : myReaction ? t : t + 1));
    if (!removing) setBurst((b) => b + 1);
    await fetch("/api/posts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "react", postId: post.id, kind }),
    });
  }

  async function comment() {
    const text = draft.trim();
    if (!text) return;
    setBusy(true);
    setDraft("");
    await fetch("/api/posts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "comment", postId: post.id, body: text }),
    });
    setBusy(false);
    onChanged();
  }

  async function saveEdit() {
    setBusy(true);
    await fetch("/api/posts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "update", postId: post.id, body: editBody, visibility }),
    });
    setBusy(false);
    setEditing(false);
    onChanged();
  }

  async function remove() {
    setBusy(true);
    await fetch("/api/posts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "delete", postId: post.id }),
    });
    onChanged();
  }

  const active = REACTIONS.find((r) => r.key === myReaction);
  const topKinds = Object.entries(post.reactionCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([k]) => REACTIONS.find((r) => r.key === k))
    .filter(Boolean) as typeof REACTIONS;

  return (
    <article
      ref={cardRef}
      className="glass anim-up overflow-hidden"
      style={{ animationDelay: `${Math.min(index, 6) * 60}ms` }}
    >
      <header className="flex items-center gap-3 px-4 pt-4">
        <Avatar
          name={post.author.name}
          src={post.author.avatarUrl}
          accent={post.author.accent}
          size={44}
          ring="aurora"
          online={post.author.online}
          onClick={() => onOpenProfile(post.author.id)}
        />
        <div className="min-w-0 flex-1">
          <button onClick={() => onOpenProfile(post.author.id)} className="flex items-center gap-1.5">
            <span className="truncate text-[14px] font-bold">{post.author.name}</span>
            {post.author.verified && (
              <span className="grid h-4 w-4 place-items-center rounded-full" style={{ background: accentGradient(post.author.accent) }}>
                <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="#0a0a12" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12.8 9.5 17 19 7" /></svg>
              </span>
            )}
          </button>
          <div className="flex items-center gap-1.5 text-[11px] text-[color:var(--muted)]">
            <span>@{post.author.handle}</span>
            <span>·</span>
            <span>{timeAgo(post.createdAt)}</span>
            {post.edited && <span>· edited</span>}
            <span>·</span>
            {post.visibility === "private" ? <IconLock size={11} /> : <IconGlobe size={11} />}
          </div>
        </div>
        {mine && !editing && (
          <div className="flex items-center gap-1">
            <button onClick={() => setEditing(true)} className="chip tap p-2" aria-label="Edit post">
              <IconEdit size={15} />
            </button>
            <button onClick={remove} disabled={busy} className="chip tap p-2" aria-label="Delete post">
              <IconTrash size={15} />
            </button>
          </div>
        )}
      </header>

      {editing ? (
        <div className="px-4 pt-3">
          <textarea
            value={editBody}
            onChange={(e) => setEditBody(e.target.value)}
            rows={3}
            className="field w-full resize-none bg-transparent px-3.5 py-3 text-[14px] outline-none"
          />
          <div className="mt-2 flex items-center gap-2">
            {(["public", "private"] as const).map((v) => (
              <button
                key={v}
                onClick={() => setVisibility(v)}
                className="chip tap flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-semibold capitalize"
                style={visibility === v ? { borderColor: "var(--p)", background: "color-mix(in srgb, var(--p) 20%, transparent)" } : undefined}
              >
                {v === "public" ? <IconGlobe size={12} /> : <IconLock size={12} />}
                {v}
              </button>
            ))}
            <button onClick={saveEdit} disabled={busy} className="btn-primary ml-auto px-4 py-2 text-[12px]">
              Save
            </button>
            <button onClick={() => setEditing(false)} className="chip tap px-3 py-2 text-[12px]">
              Cancel
            </button>
          </div>
        </div>
      ) : (
        post.body && (
          <p className="whitespace-pre-wrap px-4 pt-3 text-[14.5px] leading-relaxed text-[color:var(--fg)]/92">
            {post.body}
          </p>
        )
      )}

      {post.mediaUrl && (
        <div className="relative mt-3.5 overflow-hidden" style={{ background: "#05060d" }}>
          {post.mediaKind === "video" ? (
            <video
              ref={videoRef}
              src={post.mediaUrl}
              muted
              loop
              playsInline
              preload="metadata"
              className="max-h-[520px] w-full object-cover"
            />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={post.mediaUrl}
              alt=""
              loading="lazy"
              className="max-h-[520px] w-full object-cover"
            />
          )}
        </div>
      )}

      <div className="flex items-center gap-3 px-4 pt-3 text-[11.5px] text-[color:var(--muted)]">
        <div className="flex items-center gap-1">
          {topKinds.map(({ key, Icon, color }) => (
            <span
              key={key}
              className="grid h-[19px] w-[19px] place-items-center rounded-full"
              style={{ background: color, color: "#0a0a12" }}
            >
              <Icon size={11} />
            </span>
          ))}
          <span className="ml-1 font-semibold">{compact(total)}</span>
        </div>
        <span>·</span>
        <span>{post.commentCount} comments</span>
        <span className="ml-auto flex items-center gap-1">
          <IconEye size={13} /> {compact(post.views)}
        </span>
      </div>

      <div className="relative mt-2.5 flex border-t border-white/8">
        <div className="relative flex-1">
          {picker && (
            <div className="glass anim-pop absolute bottom-[calc(100%+8px)] left-2 z-20 flex gap-1 p-1.5" style={{ borderRadius: 999 }}>
              {REACTIONS.map(({ key, Icon, label, color }) => (
                <button
                  key={key}
                  onClick={() => react(key)}
                  title={label}
                  className="tap grid h-10 w-10 place-items-center rounded-full"
                  style={{ background: myReaction === key ? color : "transparent", color: myReaction === key ? "#0a0a12" : color }}
                >
                  <Icon size={19} />
                </button>
              ))}
            </div>
          )}
          <button
            onClick={() => (myReaction ? react(myReaction) : react("like"))}
            onContextMenu={(e) => {
              e.preventDefault();
              setPicker((p) => !p);
            }}
            onDoubleClick={() => setPicker(true)}
            onPointerEnter={() => setPicker(true)}
            onPointerLeave={() => setPicker(false)}
            className="tap relative flex w-full items-center justify-center gap-2 py-3 text-[12.5px] font-bold"
            style={{ color: active ? active.color : "var(--muted)" }}
          >
            {active ? <active.Icon size={17} /> : <IconThumb size={17} />}
            {active ? active.label : "React"}
            {burst > 0 && active && (
              <span key={burst} className="burst pointer-events-none absolute">
                <active.Icon size={17} />
              </span>
            )}
          </button>
        </div>
        <button
          onClick={() => setShowComments((s) => !s)}
          className="tap flex flex-1 items-center justify-center gap-2 py-3 text-[12.5px] font-bold text-[color:var(--muted)]"
        >
          <IconComment size={17} /> Comment
        </button>
      </div>

      {showComments && (
        <div className="anim-pop border-t border-white/8 px-4 py-3">
          <div className="space-y-2.5">
            {post.comments.length === 0 && (
              <p className="text-[12px] text-[color:var(--muted)]">Be the first to say something.</p>
            )}
            {post.comments.map((c) => (
              <div key={c.id} className="flex gap-2.5">
                <Avatar
                  name={c.author.name}
                  src={c.author.avatarUrl}
                  accent={c.author.accent}
                  size={30}
                  onClick={() => onOpenProfile(c.author.id)}
                />
                <div className="glass-soft flex-1 px-3 py-2">
                  <p className="text-[12px] font-bold">
                    {c.author.name}
                    <span className="ml-2 font-normal text-[color:var(--muted)]">{timeAgo(c.createdAt)}</span>
                  </p>
                  <p className="mt-0.5 text-[13px] leading-snug">{c.body}</p>
                </div>
              </div>
            ))}
            {post.commentCount > post.comments.length && (
              <p className="text-[11.5px] font-semibold text-[color:var(--p)]">
                {post.commentCount - post.comments.length} earlier comments
              </p>
            )}
          </div>
          <div className="field mt-3 flex items-center gap-2 px-3 py-2">
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && comment()}
              placeholder="Write a comment…"
              className="flex-1 bg-transparent text-[13px] outline-none placeholder:text-[color:var(--muted)]"
            />
            <button onClick={comment} disabled={busy || !draft.trim()} className="tap disabled:opacity-40" aria-label="Send comment">
              <IconSend size={17} className="text-[color:var(--p)]" />
            </button>
          </div>
        </div>
      )}
    </article>
  );
}
