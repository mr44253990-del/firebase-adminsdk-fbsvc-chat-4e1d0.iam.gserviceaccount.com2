"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { PublicUser, StoryGroup } from "@/lib/queries";
import { Avatar, Shimmer } from "@/components/ui";
import { accentGradient } from "@/lib/themes";
import { storyLeft, timeAgo } from "@/lib/format";
import {
  IconBack,
  IconClose,
  IconEye,
  IconFlame,
  IconHeart,
  IconPlus,
  IconSpark,
  IconTrash,
} from "@/components/icons";

const STORY_REACTIONS: { key: string; Icon: typeof IconHeart; label: string }[] = [
  { key: "love", Icon: IconHeart, label: "Love" },
  { key: "fire", Icon: IconFlame, label: "Fire" },
  { key: "wow", Icon: IconSpark, label: "Wow" },
];

export function StoryRail({
  groups,
  me,
  loading,
  onAdd,
  onRefresh,
  onOpenProfile,
}: {
  groups: StoryGroup[];
  me: PublicUser;
  loading: boolean;
  onAdd: () => void;
  onRefresh: () => void;
  onOpenProfile: (id: string) => void;
}) {
  const [active, setActive] = useState<number | null>(null);

  return (
    <>
      <div className="glass px-4 py-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.18em] text-[color:var(--muted)]">
            Stories
          </h2>
          <span className="text-[11px] text-[color:var(--muted)]">disappear in 12h</span>
        </div>
        <div className="no-bar flex gap-3.5 overflow-x-auto pb-1">
          <button onClick={onAdd} className="tap flex w-[68px] shrink-0 flex-col items-center gap-2">
            <div
              className="grid h-[68px] w-[68px] place-items-center rounded-full"
              style={{
                background: "color-mix(in srgb, var(--fg) 8%, transparent)",
                border: "1.5px dashed color-mix(in srgb, var(--p) 65%, transparent)",
              }}
            >
              <IconPlus size={22} className="text-[color:var(--p)]" />
            </div>
            <span className="w-full truncate text-center text-[11px] font-semibold">Your story</span>
          </button>

          {loading && groups.length === 0
            ? Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex w-[68px] shrink-0 flex-col items-center gap-2">
                  <Shimmer className="h-[68px] w-[68px] rounded-full" />
                  <Shimmer className="h-2.5 w-12" />
                </div>
              ))
            : groups.map((g, i) => (
                <button
                  key={g.author.id}
                  onClick={() => setActive(i)}
                  className="tap flex w-[68px] shrink-0 flex-col items-center gap-2"
                >
                  <Avatar
                    name={g.author.name}
                    src={g.author.avatarUrl}
                    accent={g.author.accent}
                    size={68}
                    ring={g.seen ? "seen" : "aurora"}
                    online={g.author.online}
                  />
                  <span className="w-full truncate text-center text-[11px] font-semibold">
                    {g.author.id === me.id ? "You" : g.author.name.split(" ")[0]}
                  </span>
                </button>
              ))}
        </div>
      </div>

      {active !== null && groups[active] && (
        <StoryViewer
          groups={groups}
          startIndex={active}
          me={me}
          onClose={() => {
            setActive(null);
            onRefresh();
          }}
          onOpenProfile={(id) => {
            setActive(null);
            onOpenProfile(id);
          }}
        />
      )}
    </>
  );
}

function StoryViewer({
  groups,
  startIndex,
  me,
  onClose,
  onOpenProfile,
}: {
  groups: StoryGroup[];
  startIndex: number;
  me: PublicUser;
  onClose: () => void;
  onOpenProfile: (id: string) => void;
}) {
  const [gi, setGi] = useState(startIndex);
  const [si, setSi] = useState(0);
  const [progress, setProgress] = useState(0);
  const [paused, setPaused] = useState(false);
  const [override, setOverride] = useState<Record<string, string>>({});
  const [burstKey, setBurstKey] = useState(0);
  const raf = useRef<number | null>(null);
  const group = groups[gi];
  const story = group?.items[si];

  const next = useCallback(() => {
    setProgress(0);
    if (!group) return;
    if (si + 1 < group.items.length) {
      setSi(si + 1);
    } else if (gi + 1 < groups.length) {
      setGi(gi + 1);
      setSi(0);
    } else {
      onClose();
    }
  }, [gi, si, group, groups.length, onClose]);

  const prev = useCallback(() => {
    setProgress(0);
    if (si > 0) setSi(si - 1);
    else if (gi > 0) {
      setGi(gi - 1);
      setSi(0);
    }
  }, [gi, si]);

  useEffect(() => {
    if (!story) return;
    fetch("/api/stories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "view", storyId: story.id }),
    }).catch(() => {});
  }, [story]);

  useEffect(() => {
    let last = performance.now();
    const tick = (now: number) => {
      const dt = now - last;
      last = now;
      if (!paused) {
        setProgress((p) => {
          const nextP = p + dt / 5200;
          if (nextP >= 1) {
            next();
            return 0;
          }
          return nextP;
        });
      }
      raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
    return () => {
      if (raf.current) cancelAnimationFrame(raf.current);
    };
  }, [paused, next]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowRight") next();
      if (e.key === "ArrowLeft") prev();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [next, prev, onClose]);

  if (!group || !story) return null;
  const mine = group.author.id === me.id;
  const reaction = override[story.id] ?? story.myReaction;

  async function react(kind: string) {
    setOverride((o) => ({ ...o, [story.id]: kind }));
    setBurstKey((k) => k + 1);
    await fetch("/api/stories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "react", storyId: story.id, reaction: kind }),
    });
  }

  async function remove() {
    await fetch("/api/stories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "delete", storyId: story.id }),
    });
    onClose();
  }

  return (
    <div className="fixed inset-0 z-[70]" style={{ background: "rgba(2,2,10,.9)" }}>
      <div className="absolute inset-0" style={{ backdropFilter: "blur(28px)" }} />
      <div className="relative mx-auto flex h-full w-full max-w-[440px] flex-col px-3 py-4">
        <div className="flex gap-1.5">
          {group.items.map((item, idx) => (
            <div key={item.id} className="h-[3px] flex-1 overflow-hidden rounded-full bg-white/22">
              <div
                className="h-full rounded-full"
                style={{
                  width: idx < si ? "100%" : idx === si ? `${progress * 100}%` : "0%",
                  background: accentGradient(story.accent),
                  transition: idx === si ? "none" : "width .2s",
                }}
              />
            </div>
          ))}
        </div>

        <div className="mt-3 flex items-center gap-3">
          <Avatar
            name={group.author.name}
            src={group.author.avatarUrl}
            accent={group.author.accent}
            size={40}
            ring="aurora"
            onClick={() => onOpenProfile(group.author.id)}
          />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold">{group.author.name}</p>
            <p className="text-[11px] text-white/60">
              {timeAgo(story.createdAt)} · {storyLeft(story.expiresAt)}
            </p>
          </div>
          {mine && (
            <button onClick={remove} className="chip tap p-2" aria-label="Delete story">
              <IconTrash size={16} />
            </button>
          )}
          <button onClick={onClose} className="chip tap p-2" aria-label="Close">
            <IconClose size={16} />
          </button>
        </div>

        <div
          className="relative mt-3 flex-1 overflow-hidden rounded-[28px]"
          style={{ border: "1px solid rgba(255,255,255,.14)", background: "#05060d" }}
          onPointerDown={() => setPaused(true)}
          onPointerUp={() => setPaused(false)}
          onPointerLeave={() => setPaused(false)}
        >
          {story.mediaKind === "video" ? (
            <video
              src={story.mediaUrl}
              autoPlay
              muted
              loop
              playsInline
              className="h-full w-full object-cover"
            />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={story.mediaUrl} alt="" className="h-full w-full object-cover" />
          )}
          <div
            className="absolute inset-0"
            style={{ background: "linear-gradient(180deg, rgba(0,0,0,.35), transparent 30%, rgba(0,0,0,.6))" }}
          />
          <button className="absolute inset-y-0 left-0 w-1/3" onClick={prev} aria-label="Previous" />
          <button className="absolute inset-y-0 right-0 w-1/3" onClick={next} aria-label="Next" />

          {story.caption && (
            <p className="absolute inset-x-5 bottom-20 text-center text-[17px] font-bold leading-snug drop-shadow-lg">
              {story.caption}
            </p>
          )}

          <div className="absolute inset-x-4 bottom-4 flex items-center gap-2">
            <span className="glass-soft flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-semibold">
              <IconEye size={13} /> {story.viewCount}
            </span>
            <div className="ml-auto flex items-center gap-2">
              {STORY_REACTIONS.map(({ key, Icon, label }) => (
                <button
                  key={key}
                  onClick={() => react(key)}
                  aria-label={label}
                  className="tap relative grid h-11 w-11 place-items-center rounded-full"
                  style={{
                    background:
                      reaction === key ? accentGradient(story.accent) : "rgba(255,255,255,.12)",
                    color: reaction === key ? "#07070f" : "#fff",
                    border: "1px solid rgba(255,255,255,.18)",
                  }}
                >
                  <Icon size={19} />
                  {reaction === key && (
                    <span key={burstKey} className="burst pointer-events-none absolute -top-2">
                      <Icon size={19} className="text-[color:var(--p)]" />
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-3 flex items-center justify-center gap-2 text-[11px] text-white/45">
          <IconBack size={12} />
          <span>tap sides to move · hold to pause</span>
        </div>
      </div>
    </div>
  );
}
