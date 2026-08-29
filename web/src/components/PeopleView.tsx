"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { NotificationItem, PublicUser } from "@/lib/queries";
import { Avatar, Shimmer } from "@/components/ui";
import { accentGradient } from "@/lib/themes";
import { presenceLabel, timeAgo } from "@/lib/format";
import {
  IconBell,
  IconBlock,
  IconChat,
  IconCheck,
  IconComment,
  IconHeart,
  IconPeople,
  IconPlus,
  IconSearch,
  IconSpark,
  IconUser,
} from "@/components/icons";

export function PeopleView({
  me,
  people,
  following,
  blocked,
  loading,
  onAction,
  onOpenProfile,
  onOpenDm,
}: {
  me: PublicUser;
  people: PublicUser[];
  following: string[];
  blocked: string[];
  loading: boolean;
  onAction: (action: string, targetId: string) => void;
  onOpenProfile: (id: string) => void;
  onOpenDm: (id: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<"all" | "online" | "following">("all");

  const list = useMemo(() => {
    const q = query.trim().toLowerCase();
    return people
      .filter((p) => !blocked.includes(p.id))
      .filter((p) => (tab === "online" ? p.online : tab === "following" ? following.includes(p.id) : true))
      .filter((p) => !q || p.name.toLowerCase().includes(q) || p.handle.includes(q));
  }, [people, query, tab, following, blocked]);

  const onlineCount = people.filter((p) => p.online).length;

  return (
    <div className="space-y-3">
      <div className="glass px-4 py-4">
        <div className="flex items-center gap-3">
          <h2 className="text-xl font-black tracking-tight">People</h2>
          <span className="chip flex items-center gap-1.5 px-2.5 py-1 text-[10.5px] font-bold">
            <span className="h-1.5 w-1.5 rounded-full" style={{ background: "#3ddc84", boxShadow: "0 0 8px 2px rgba(61,220,132,.6)" }} />
            {onlineCount} online
          </span>
          <span className="ml-auto text-[11px] text-[color:var(--muted)]">{people.length + 1} members</span>
        </div>

        <div className="field mt-3 flex items-center gap-2 px-3.5 py-2.5">
          <IconSearch size={16} className="text-[color:var(--muted)]" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name or @username"
            className="flex-1 bg-transparent text-[13px] outline-none placeholder:text-[color:var(--muted)]"
          />
        </div>

        <div className="mt-3 flex gap-2">
          {(["all", "online", "following"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className="chip tap px-3.5 py-1.5 text-[11.5px] font-bold capitalize"
              style={tab === t ? { borderColor: "var(--p)", background: "color-mix(in srgb, var(--p) 20%, transparent)" } : undefined}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {loading && people.length === 0
          ? Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="glass p-4">
                <div className="flex items-center gap-3">
                  <Shimmer className="h-12 w-12 rounded-full" />
                  <div className="flex-1 space-y-2">
                    <Shimmer className="h-3 w-24" />
                    <Shimmer className="h-2.5 w-32" />
                  </div>
                </div>
              </div>
            ))
          : list.map((p, i) => (
              <div key={p.id} className="glass anim-up p-4" style={{ animationDelay: `${Math.min(i, 8) * 45}ms` }}>
                <div className="flex items-center gap-3">
                  <Avatar
                    name={p.name}
                    src={p.avatarUrl}
                    accent={p.accent}
                    size={50}
                    ring="aurora"
                    online={p.online}
                    verified={p.verified}
                    onClick={() => onOpenProfile(p.id)}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[14px] font-bold">{p.name}</p>
                    <p className="truncate text-[11px] text-[color:var(--muted)]">@{p.handle}</p>
                    <p className="mt-0.5 flex items-center gap-1 text-[10.5px]" style={{ color: p.online ? "#3ddc84" : "var(--muted)" }}>
                      {presenceLabel(p.online, p.lastSeen, p.activityVisible)}
                    </p>
                  </div>
                </div>
                {p.bio && <p className="mt-3 line-clamp-2 text-[12.5px] leading-relaxed text-[color:var(--muted)]">{p.bio}</p>}
                <div className="mt-3 flex gap-2">
                  <button
                    onClick={() => onAction(following.includes(p.id) ? "unfollow" : "follow", p.id)}
                    className="tap flex flex-1 items-center justify-center gap-1.5 rounded-xl py-2 text-[11.5px] font-bold"
                    style={
                      following.includes(p.id)
                        ? { background: "color-mix(in srgb, var(--fg) 9%, transparent)", color: "var(--muted)" }
                        : { background: accentGradient(p.accent), color: "#07070f" }
                    }
                  >
                    {following.includes(p.id) ? <IconCheck size={13} /> : <IconPlus size={13} />}
                    {following.includes(p.id) ? "Following" : "Follow"}
                  </button>
                  <button onClick={() => onOpenDm(p.id)} className="chip tap px-3 py-2" aria-label="Message">
                    <IconChat size={15} />
                  </button>
                  <button onClick={() => onAction("block", p.id)} className="chip tap px-3 py-2" aria-label="Block">
                    <IconBlock size={15} />
                  </button>
                </div>
              </div>
            ))}
      </div>

      {!loading && list.length === 0 && (
        <div className="glass grid place-items-center gap-2 px-4 py-14 text-center">
          <IconPeople size={26} className="text-[color:var(--muted)]" />
          <p className="text-[13px] text-[color:var(--muted)]">Nobody matches that filter yet.</p>
        </div>
      )}

      {blocked.length > 0 && (
        <div className="glass px-4 py-4">
          <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
            Blocked ({blocked.length})
          </p>
          <div className="flex flex-wrap gap-2">
            {blocked.map((id) => {
              const p = people.find((x) => x.id === id);
              if (!p) return null;
              return (
                <button key={id} onClick={() => onAction("unblock", id)} className="chip tap flex items-center gap-2 px-3 py-1.5 text-[11.5px] font-semibold">
                  <Avatar name={p.name} src={p.avatarUrl} accent={p.accent} size={20} /> {p.name} · unblock
                </button>
              );
            })}
          </div>
        </div>
      )}
      <div className="h-2" />
      <p className="pb-2 text-center text-[10.5px] text-[color:var(--muted)]">
        Signed in as {me.name} · @{me.handle}
      </p>
    </div>
  );
}

const KIND_ICON: Record<string, typeof IconBell> = {
  message: IconChat,
  reaction: IconHeart,
  comment: IconComment,
  follow: IconUser,
  story: IconSpark,
};

export function NotificationsView({
  onOpenProfile,
  onRefreshBadges,
}: {
  onOpenProfile: (id: string) => void;
  onRefreshBadges: () => void;
}) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const res = await fetch("/api/notifications");
    if (res.ok) setItems((await res.json()).items as NotificationItem[]);
    setLoading(false);
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function markAll() {
    await fetch("/api/notifications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "read" }),
    });
    load();
    onRefreshBadges();
  }

  async function clearAll() {
    await fetch("/api/notifications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action: "clear" }),
    });
    load();
    onRefreshBadges();
  }

  const unread = items.filter((i) => !i.isRead).length;

  return (
    <div className="space-y-3">
      <div className="glass flex items-center gap-3 px-4 py-4">
        <h2 className="text-xl font-black tracking-tight">Activity</h2>
        {unread > 0 && (
          <span className="unread-glow grid h-6 min-w-6 place-items-center rounded-full px-2 text-[10.5px] font-black" style={{ background: "#ff4d6d", color: "#fff" }}>
            {unread}
          </span>
        )}
        <button onClick={markAll} className="chip tap ml-auto px-3 py-1.5 text-[11px] font-bold">
          Mark read
        </button>
        <button onClick={clearAll} className="chip tap px-3 py-1.5 text-[11px] font-bold">
          Clear
        </button>
      </div>

      <div className="glass overflow-hidden">
        {loading && items.length === 0
          ? Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-3.5">
                <Shimmer className="h-10 w-10 rounded-full" />
                <Shimmer className="h-3 flex-1" />
              </div>
            ))
          : items.map((n, i) => {
              const Icon = KIND_ICON[n.kind] ?? IconBell;
              return (
                <div
                  key={n.id}
                  className="anim-up flex items-center gap-3 border-b border-white/6 px-4 py-3.5 last:border-0"
                  style={{
                    animationDelay: `${Math.min(i, 8) * 40}ms`,
                    background: n.isRead ? undefined : "linear-gradient(90deg, rgba(255,77,109,.10), transparent 60%)",
                  }}
                >
                  <Avatar
                    name={n.actor?.name ?? "System"}
                    src={n.actor?.avatarUrl}
                    accent={n.actor?.accent}
                    size={42}
                    ring={n.isRead ? "none" : "aurora"}
                    onClick={() => n.actor && onOpenProfile(n.actor.id)}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] leading-snug">
                      <span className="font-bold">{n.actor?.name ?? "Nexus"}</span>{" "}
                      <span className="text-[color:var(--muted)]">{n.body}</span>
                    </p>
                    <p className="mt-0.5 text-[10.5px] text-[color:var(--muted)]">{timeAgo(n.createdAt)}</p>
                  </div>
                  <span
                    className="grid h-8 w-8 shrink-0 place-items-center rounded-full"
                    style={{ background: "color-mix(in srgb, var(--p) 22%, transparent)", color: "var(--p)" }}
                  >
                    <Icon size={15} />
                  </span>
                </div>
              );
            })}
        {!loading && items.length === 0 && (
          <div className="grid place-items-center gap-2 px-4 py-14 text-center">
            <IconBell size={26} className="text-[color:var(--muted)]" />
            <p className="text-[13px] text-[color:var(--muted)]">All caught up.</p>
          </div>
        )}
      </div>
    </div>
  );
}
