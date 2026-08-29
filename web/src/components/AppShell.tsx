"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FeedPost, PublicUser, StoryGroup } from "@/lib/queries";
import { AuthScreen } from "@/components/AuthScreen";
import { Backdrop, Avatar, PostSkeleton, Toast } from "@/components/ui";
import { StoryRail } from "@/components/Stories";
import { PostCard } from "@/components/PostCard";
import { Composer } from "@/components/Composer";
import { MessagesView } from "@/components/MessagesView";
import { NotificationsView, PeopleView } from "@/components/PeopleView";
import { ProfileView, SettingsView } from "@/components/ProfileSettings";
import { themeStyle } from "@/lib/themes";
import {
  IconBell,
  IconChat,
  IconGear,
  IconHome,
  IconImage,
  IconNote,
  IconPeople,
  IconPlus,
  IconSpark,
  IconUser,
} from "@/components/icons";

type Me = PublicUser & { email?: string; themeId?: string; readReceipts?: boolean; reduceMotion?: boolean };
type Tab = "home" | "messages" | "people" | "activity" | "profile" | "settings";
type Badges = { chats: number; messages: number; notifications: number };

const CACHE_KEY = "nexus.feed.cache.v1";

export function AppShell() {
  const [booted, setBooted] = useState(false);
  const [me, setMe] = useState<Me | null>(null);
  const [badges, setBadges] = useState<Badges>({ chats: 0, messages: 0, notifications: 0 });
  const [tab, setTab] = useState<Tab>("home");
  const [stories, setStories] = useState<StoryGroup[]>([]);
  const [posts, setPosts] = useState<FeedPost[]>([]);
  const [notes, setNotes] = useState<{ author: PublicUser; body: string }[]>([]);
  const [people, setPeople] = useState<PublicUser[]>([]);
  const [following, setFollowing] = useState<string[]>([]);
  const [blocked, setBlocked] = useState<string[]>([]);
  const [loadingFeed, setLoadingFeed] = useState(true);
  const [loadingPeople, setLoadingPeople] = useState(true);
  const [composer, setComposer] = useState<null | "post" | "story" | "note">(null);
  const [fabOpen, setFabOpen] = useState(false);
  const [profileId, setProfileId] = useState<string | null>(null);
  const [openEditor, setOpenEditor] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [fromCache, setFromCache] = useState(false);
  const [pendingDm, setPendingDm] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const flash = useCallback((message: string) => {
    setToast(message);
    setTimeout(() => setToast(null), 2200);
  }, []);

  const loadSession = useCallback(async () => {
    const res = await fetch("/api/session");
    const json = await res.json();
    setMe(json.user ?? null);
    if (json.badges) setBadges(json.badges);
    setBooted(true);
    return json.user as Me | null;
  }, []);

  /** Progressive hydration: stories first (cheap), posts next, people last. */
  const loadFeed = useCallback(async () => {
    try {
      const sres = await fetch("/api/feed?part=stories");
      if (sres.ok) setStories((await sres.json()).stories as StoryGroup[]);
      const pres = await fetch("/api/feed");
      if (pres.ok) {
        const json = await pres.json();
        setPosts(json.posts as FeedPost[]);
        setStories(json.stories as StoryGroup[]);
        setNotes(json.notes ?? []);
        setFromCache(false);
        try {
          localStorage.setItem(CACHE_KEY, JSON.stringify({ posts: json.posts, stories: json.stories, at: Date.now() }));
        } catch {}
      }
    } catch {
      try {
        const raw = localStorage.getItem(CACHE_KEY);
        if (raw) {
          const cached = JSON.parse(raw);
          setPosts(cached.posts ?? []);
          setStories(cached.stories ?? []);
          setFromCache(true);
        }
      } catch {}
    }
    setLoadingFeed(false);
  }, []);

  const loadPeople = useCallback(async () => {
    const res = await fetch("/api/people");
    if (res.ok) {
      const json = await res.json();
      setPeople(json.people as PublicUser[]);
      setFollowing(json.following as string[]);
      setBlocked(json.blocked as string[]);
    }
    setLoadingPeople(false);
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadSession().then((user) => {
      if (user) {
        loadFeed();
        loadPeople();
      }
    });
  }, [loadSession, loadFeed, loadPeople]);

  // Presence heartbeat + badge polling.
  useEffect(() => {
    if (!me) return;
    const t = setInterval(() => {
      fetch("/api/session")
        .then((r) => r.json())
        .then((j) => j.badges && setBadges(j.badges))
        .catch(() => {});
    }, 15000);
    return () => clearInterval(t);
  }, [me]);

  useEffect(() => {
    const on = () => {
      setOffline(false);
      if (me) loadFeed();
    };
    const off = () => setOffline(true);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setOffline(!navigator.onLine);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, [me, loadFeed]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [tab, profileId]);

  const themeVars = useMemo(() => themeStyle(me?.themeId), [me?.themeId]);

  const patchMe = useCallback(
    async (patch: Record<string, unknown>) => {
      const res = await fetch("/api/me", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patch),
      });
      const json = await res.json();
      if (!res.ok) {
        flash(json.error ?? "Could not save");
        return;
      }
      setMe(json.user as Me);
      setBlocked(json.blocked as string[]);
      flash("Saved");
    },
    [flash],
  );

  const peopleAction = useCallback(
    async (action: string, targetId: string) => {
      await fetch("/api/people", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action, targetId }),
      });
      loadPeople();
      if (action === "block" || action === "unblock") loadFeed();
      flash(
        action === "follow"
          ? "Following"
          : action === "unfollow"
            ? "Unfollowed"
            : action === "block"
              ? "Blocked"
              : "Unblocked",
      );
    },
    [loadPeople, loadFeed, flash],
  );

  const openDm = useCallback(
    async (targetId: string) => {
      const res = await fetch("/api/chats", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "open-dm", targetId }),
      });
      if (res.ok) {
        setPendingDm(targetId);
        setProfileId(null);
        setTab("messages");
      }
    },
    [],
  );

  if (!booted) {
    return (
      <div className="relative grid h-full w-full place-items-center" style={themeVars as React.CSSProperties}>
        <Backdrop bubbles={14} />
        <div className="relative z-10 flex flex-col items-center gap-4">
          <div className="ring-aurora grid h-16 w-16 place-items-center rounded-full" style={{ padding: 3 }}>
            <div className="grid h-full w-full place-items-center rounded-full" style={{ background: "var(--bg)" }}>
              <IconSpark size={22} className="text-[color:var(--p)]" />
            </div>
          </div>
          <p className="text-[11px] font-bold uppercase tracking-[0.3em] text-[color:var(--muted)]">Convo Nexus</p>
        </div>
      </div>
    );
  }

  if (!me) {
    return (
      <AuthScreen
        onDone={() => {
          setBooted(false);
          loadSession().then((user) => {
            if (user) {
              setLoadingFeed(true);
              setLoadingPeople(true);
              loadFeed();
              loadPeople();
            }
          });
        }}
      />
    );
  }

  const tabs: { key: Tab; Icon: typeof IconHome; label: string; badge?: number }[] = [
    { key: "home", Icon: IconHome, label: "Feed" },
    { key: "messages", Icon: IconChat, label: "Chats", badge: badges.chats },
    { key: "people", Icon: IconPeople, label: "People" },
    { key: "activity", Icon: IconBell, label: "Activity", badge: badges.notifications },
    { key: "profile", Icon: IconUser, label: "You" },
  ];

  return (
    <div
      className={`relative h-full w-full overflow-hidden ${me.reduceMotion ? "reduce-motion" : ""}`}
      style={themeVars as React.CSSProperties}
    >
      <Backdrop bubbles={me.reduceMotion ? 0 : 18} />

      {/* Top bar */}
      <header className="relative z-20 px-3 pt-3 sm:px-5">
        <div className="glass mx-auto flex max-w-2xl items-center gap-2.5 px-3.5 py-2.5">
          <div className="grid h-9 w-9 place-items-center rounded-xl" style={{ background: "linear-gradient(120deg, var(--p), var(--s))" }}>
            <IconSpark size={18} className="text-black/80" />
          </div>
          <div className="min-w-0">
            <p className="text-[13px] font-black leading-none tracking-tight grad-text">NEXUS</p>
            <p className="mt-0.5 text-[9.5px] uppercase tracking-[0.2em] text-[color:var(--muted)]">
              {offline ? "offline mode" : "live"}
            </p>
          </div>

          <button
            onClick={() => setTab("activity")}
            className="chip tap relative ml-auto p-2.5"
            aria-label="Notifications"
          >
            <IconBell size={17} />
            {badges.notifications > 0 && (
              <span
                className="unread-glow absolute -right-1 -top-1 grid h-[18px] min-w-[18px] place-items-center rounded-full px-1 text-[9.5px] font-black"
                style={{ background: "#ff4d6d", color: "#fff" }}
              >
                {badges.notifications}
              </span>
            )}
          </button>
          <button onClick={() => setTab("messages")} className="chip tap relative p-2.5" aria-label="Messages">
            <IconChat size={17} />
            {badges.messages > 0 && (
              <span
                className="unread-glow absolute -right-1 -top-1 grid h-[18px] min-w-[18px] place-items-center rounded-full px-1 text-[9.5px] font-black"
                style={{ background: "#ff4d6d", color: "#fff" }}
              >
                {badges.messages}
              </span>
            )}
          </button>
          <button onClick={() => setTab("settings")} className="chip tap p-2.5" aria-label="Settings">
            <IconGear size={17} />
          </button>
          <Avatar
            name={me.name}
            src={me.avatarUrl}
            accent={me.accent}
            size={36}
            ring="aurora"
            online
            onClick={() => {
              setProfileId(null);
              setTab("profile");
            }}
          />
        </div>
      </header>

      {(offline || fromCache) && (
        <div className="relative z-20 mx-auto mt-2 max-w-2xl px-3 sm:px-5">
          <div className="glass-soft anim-pop flex items-center gap-2 px-3.5 py-2 text-[11.5px]">
            <span className="h-1.5 w-1.5 rounded-full" style={{ background: "#ffb020" }} />
            Showing the cached copy. Media refreshes automatically when you are back online.
          </div>
        </div>
      )}

      {/* Content */}
      <main ref={scrollRef} className="scroller relative z-10 h-[calc(100dvh-64px)] px-3 pb-28 pt-3 sm:px-5">
        <div className="mx-auto max-w-2xl space-y-3">
          {profileId ? (
            <ProfileView
              userId={profileId}
              me={me}
              onBack={() => setProfileId(null)}
              onOpenProfile={setProfileId}
              onOpenDm={openDm}
            />
          ) : tab === "home" ? (
            <>
              <StoryRail
                groups={stories}
                me={me}
                loading={loadingFeed}
                onAdd={() => setComposer("story")}
                onRefresh={loadFeed}
                onOpenProfile={setProfileId}
              />

              {notes.length > 0 && (
                <div className="glass px-4 py-3">
                  <p className="mb-2 flex items-center gap-1.5 text-[10.5px] font-bold uppercase tracking-[0.18em] text-[color:var(--muted)]">
                    <IconNote size={12} /> Notes
                  </p>
                  <div className="no-bar flex gap-2 overflow-x-auto">
                    {notes.map((n) => (
                      <button
                        key={n.author.id}
                        onClick={() => setProfileId(n.author.id)}
                        className="glass-soft tap flex shrink-0 items-center gap-2 px-3 py-2 text-left"
                      >
                        <Avatar name={n.author.name} src={n.author.avatarUrl} accent={n.author.accent} size={26} />
                        <div>
                          <p className="text-[11px] font-bold">{n.author.name.split(" ")[0]}</p>
                          <p className="max-w-[150px] truncate text-[11px] text-[color:var(--muted)]">{n.body}</p>
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <button
                onClick={() => setComposer("post")}
                className="glass flex w-full items-center gap-3 px-4 py-3.5 text-left"
              >
                <Avatar name={me.name} src={me.avatarUrl} accent={me.accent} size={40} />
                <span className="flex-1 text-[13.5px] text-[color:var(--muted)]">Share something with Nexus…</span>
                <span className="chip grid h-9 w-9 place-items-center">
                  <IconImage size={16} />
                </span>
              </button>

              {loadingFeed && posts.length === 0 ? (
                <>
                  <PostSkeleton />
                  <PostSkeleton />
                  <PostSkeleton />
                </>
              ) : (
                posts.map((post, i) => (
                  <PostCard
                    key={post.id}
                    post={post}
                    me={me}
                    index={i}
                    onChanged={loadFeed}
                    onOpenProfile={setProfileId}
                  />
                ))
              )}

              {!loadingFeed && posts.length === 0 && (
                <div className="glass grid place-items-center gap-2 px-4 py-16 text-center">
                  <IconSpark size={26} className="text-[color:var(--muted)]" />
                  <p className="text-[13px] text-[color:var(--muted)]">The feed is empty. Be the first to post.</p>
                </div>
              )}
            </>
          ) : tab === "messages" ? (
            <MessagesView
              me={me}
              people={people}
              onOpenProfile={setProfileId}
              onRefreshBadges={() => {
                fetch("/api/session")
                  .then((r) => r.json())
                  .then((j) => j.badges && setBadges(j.badges))
                  .catch(() => {});
              }}
              key={pendingDm ?? "messages"}
            />
          ) : tab === "people" ? (
            <PeopleView
              me={me}
              people={people}
              following={following}
              blocked={blocked}
              loading={loadingPeople}
              onAction={peopleAction}
              onOpenProfile={setProfileId}
              onOpenDm={openDm}
            />
          ) : tab === "activity" ? (
            <NotificationsView
              onOpenProfile={setProfileId}
              onRefreshBadges={() => {
                fetch("/api/session")
                  .then((r) => r.json())
                  .then((j) => j.badges && setBadges(j.badges))
                  .catch(() => {});
              }}
            />
          ) : tab === "profile" ? (
            <ProfileView
              userId={me.id}
              me={me}
              onOpenProfile={setProfileId}
              onOpenDm={openDm}
              onEdit={() => {
                setOpenEditor(true);
                setTab("settings");
              }}
            />
          ) : (
            <SettingsView
              me={me}
              onUpdated={patchMe}
              onLogout={async () => {
                await fetch("/api/auth", {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({ action: "logout" }),
                });
                setMe(null);
                setTab("home");
              }}
              blocked={blocked}
              people={people}
              onUnblock={(id) => peopleAction("unblock", id)}
              openEditor={openEditor}
            />
          )}
        </div>
      </main>

      {/* Floating create */}
      <div className="pointer-events-none fixed bottom-24 right-4 z-30 flex flex-col items-end gap-2 sm:right-8">
        {fabOpen &&
          ([
            ["post", IconImage, "Post"],
            ["story", IconSpark, "Story"],
            ["note", IconNote, "Note"],
          ] as const).map(([kind, Icon, label], i) => (
            <button
              key={kind}
              onClick={() => {
                setComposer(kind);
                setFabOpen(false);
              }}
              className="glass anim-pop pointer-events-auto flex items-center gap-2 px-4 py-2.5 text-[12px] font-bold"
              style={{ animationDelay: `${i * 50}ms`, borderRadius: 999 }}
            >
              <Icon size={15} /> {label}
            </button>
          ))}
        <button
          onClick={() => setFabOpen((v) => !v)}
          className="btn-primary pointer-events-auto grid h-14 w-14 place-items-center"
          style={{ borderRadius: 999, transform: fabOpen ? "rotate(45deg)" : undefined }}
          aria-label="Create"
        >
          <IconPlus size={24} />
        </button>
      </div>

      {/* Bottom navigation */}
      <nav className="fixed inset-x-0 bottom-0 z-30 px-3 pb-3 sm:px-5">
        <div className="glass mx-auto flex max-w-2xl items-center justify-between px-2 py-2">
          {tabs.map(({ key, Icon, label, badge }) => {
            const on = tab === key && !profileId;
            return (
              <button
                key={key}
                onClick={() => {
                  setProfileId(null);
                  setOpenEditor(false);
                  setTab(key);
                }}
                className="tap relative flex flex-1 flex-col items-center gap-1 rounded-2xl py-2"
                style={on ? { background: "color-mix(in srgb, var(--p) 20%, transparent)" } : undefined}
              >
                <span className="relative" style={{ color: on ? "var(--p)" : "var(--muted)" }}>
                  <Icon size={20} />
                  {!!badge && badge > 0 && (
                    <span
                      className="unread-glow absolute -right-2 -top-1.5 grid h-[15px] min-w-[15px] place-items-center rounded-full px-1 text-[8.5px] font-black"
                      style={{ background: "#ff4d6d", color: "#fff" }}
                    >
                      {badge}
                    </span>
                  )}
                </span>
                <span className="text-[9.5px] font-bold" style={{ color: on ? "var(--fg)" : "var(--muted)" }}>
                  {label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      <Composer
        open={!!composer}
        kind={composer ?? "post"}
        me={me}
        onClose={() => setComposer(null)}
        onDone={(msg) => {
          flash(msg);
          loadFeed();
        }}
      />

      <Toast message={toast} />
    </div>
  );
}
