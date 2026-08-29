"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { FeedPost, PublicUser } from "@/lib/queries";
import { Avatar, Shimmer } from "@/components/ui";
import { PostCard } from "@/components/PostCard";
import { ACCENT_KEYS, THEMES, accentGradient } from "@/lib/themes";
import { compact, fileToDataUrl, presenceLabel } from "@/lib/format";
import {
  IconBack,
  IconBlock,
  IconChat,
  IconCheck,
  IconEye,
  IconHeart,
  IconImage,
  IconInfo,
  IconLink,
  IconLogout,
  IconPalette,
  IconPlus,
  IconShield,
  IconSpark,
  IconUser,
} from "@/components/icons";

type ProfilePayload = {
  profile: PublicUser;
  stats: { posts: number; followers: number; following: number; reactions: number; views: number };
  following: boolean;
  link: string;
  posts: FeedPost[];
};

export function ProfileView({
  userId,
  me,
  onBack,
  onOpenProfile,
  onOpenDm,
  onEdit,
}: {
  userId: string;
  me: PublicUser;
  onBack?: () => void;
  onOpenProfile: (id: string) => void;
  onOpenDm: (id: string) => void;
  onEdit?: () => void;
}) {
  const [data, setData] = useState<ProfilePayload | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    const res = await fetch(`/api/people?profile=${userId}`);
    if (res.ok) setData((await res.json()) as ProfilePayload);
  }, [userId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  if (!data) {
    return (
      <div className="glass p-6">
        <Shimmer className="h-32 w-full rounded-2xl" />
        <div className="mt-4 flex items-center gap-3">
          <Shimmer className="h-16 w-16 rounded-full" />
          <div className="flex-1 space-y-2">
            <Shimmer className="h-3.5 w-40" />
            <Shimmer className="h-2.5 w-24" />
          </div>
        </div>
      </div>
    );
  }

  const p = data.profile;
  const mine = p.id === me.id;

  return (
    <div className="space-y-3">
      <div className="glass overflow-hidden">
        <div
          className="relative h-36"
          style={{ background: accentGradient(p.accent), opacity: 0.9 }}
        >
          <div className="absolute inset-0" style={{ background: "linear-gradient(180deg, transparent, var(--bg))", opacity: 0.75 }} />
          {onBack && (
            <button onClick={onBack} className="glass-soft tap absolute left-3 top-3 p-2" aria-label="Back">
              <IconBack size={17} />
            </button>
          )}
        </div>

        <div className="-mt-12 px-5 pb-5">
          <div className="flex items-end gap-3">
            <Avatar name={p.name} src={p.avatarUrl} accent={p.accent} size={88} ring="aurora" online={p.online} verified={p.verified} />
            <div className="min-w-0 flex-1 pb-1">
              <h2 className="truncate text-[19px] font-black tracking-tight">{p.name}</h2>
              <p className="text-[12px] text-[color:var(--muted)]">@{p.handle}</p>
            </div>
          </div>

          <p className="mt-3 text-[10.5px]" style={{ color: p.online ? "#3ddc84" : "var(--muted)" }}>
            {presenceLabel(p.online, p.lastSeen, p.activityVisible)}
            {p.location ? ` · ${p.location}` : ""}
          </p>
          {p.bio && <p className="mt-2 text-[13.5px] leading-relaxed text-[color:var(--fg)]/85">{p.bio}</p>}

          <button
            onClick={() => {
              navigator.clipboard?.writeText(`https://${data.link}`).catch(() => {});
              setCopied(true);
              setTimeout(() => setCopied(false), 1600);
            }}
            className="chip tap mt-3 flex items-center gap-2 px-3 py-1.5 text-[11.5px] font-semibold"
          >
            <IconLink size={13} /> {copied ? "Link copied" : data.link}
          </button>

          <div className="mt-4 grid grid-cols-4 gap-2">
            {[
              ["Posts", data.stats.posts],
              ["Followers", data.stats.followers],
              ["Reactions", data.stats.reactions],
              ["Views", data.stats.views],
            ].map(([label, value]) => (
              <div key={String(label)} className="glass-soft px-2 py-2.5 text-center">
                <p className="text-[15px] font-black grad-text">{compact(Number(value))}</p>
                <p className="text-[9.5px] uppercase tracking-wider text-[color:var(--muted)]">{label}</p>
              </div>
            ))}
          </div>

          <div className="mt-4 flex gap-2">
            {mine ? (
              <button onClick={onEdit} className="btn-primary flex-1 py-2.5 text-[12.5px]">
                Edit profile
              </button>
            ) : (
              <>
                <button
                  onClick={async () => {
                    await fetch("/api/people", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: JSON.stringify({ action: data.following ? "unfollow" : "follow", targetId: p.id }),
                    });
                    load();
                  }}
                  className="tap flex flex-1 items-center justify-center gap-1.5 rounded-xl py-2.5 text-[12.5px] font-bold"
                  style={
                    data.following
                      ? { background: "color-mix(in srgb, var(--fg) 9%, transparent)", color: "var(--muted)" }
                      : { background: accentGradient(p.accent), color: "#07070f" }
                  }
                >
                  {data.following ? <IconCheck size={14} /> : <IconPlus size={14} />}
                  {data.following ? "Following" : "Follow"}
                </button>
                <button onClick={() => onOpenDm(p.id)} className="chip tap flex items-center gap-2 px-4 py-2.5 text-[12.5px] font-bold">
                  <IconChat size={15} /> Message
                </button>
                <button
                  onClick={async () => {
                    await fetch("/api/people", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: JSON.stringify({ action: "block", targetId: p.id }),
                    });
                    onBack?.();
                  }}
                  className="chip tap px-3 py-2.5"
                  aria-label="Block"
                >
                  <IconBlock size={15} />
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      {data.posts.length === 0 ? (
        <div className="glass grid place-items-center gap-2 px-4 py-14 text-center">
          <IconSpark size={26} className="text-[color:var(--muted)]" />
          <p className="text-[13px] text-[color:var(--muted)]">No posts to show yet.</p>
        </div>
      ) : (
        data.posts.map((post, i) => (
          <PostCard key={post.id} post={post} me={me} index={i} onChanged={load} onOpenProfile={onOpenProfile} />
        ))
      )}
    </div>
  );
}

export function SettingsView({
  me,
  onUpdated,
  onLogout,
  blocked,
  people,
  onUnblock,
  openEditor,
}: {
  me: PublicUser & { themeId?: string; readReceipts?: boolean; reduceMotion?: boolean; email?: string };
  onUpdated: (patch: Record<string, unknown>) => void;
  onLogout: () => void;
  blocked: string[];
  people: PublicUser[];
  onUnblock: (id: string) => void;
  openEditor: boolean;
}) {
  const [tab, setTab] = useState<"profile" | "themes" | "privacy" | "about">(openEditor ? "profile" : "themes");
  const [name, setName] = useState(me.name);
  const [handle, setHandle] = useState(me.handle);
  const [bio, setBio] = useState(me.bio);
  const [location, setLocation] = useState(me.location);
  const [accent, setAccent] = useState(me.accent);
  const [avatar, setAvatar] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  async function saveProfile() {
    setSaving(true);
    let avatarUrl: string | undefined;
    if (avatar) {
      const res = await fetch("/api/upload", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dataUrl: avatar, bucket: "avatars", kind: "image" }),
      });
      if (res.ok) avatarUrl = (await res.json()).url;
    }
    onUpdated({ action: "profile", name, handle, bio, location, accent, ...(avatarUrl ? { avatarUrl } : {}) });
    setAvatar(null);
    setSaving(false);
  }

  return (
    <div className="space-y-3">
      <div className="glass px-4 py-4">
        <h2 className="text-xl font-black tracking-tight">Settings</h2>
        <div className="no-bar mt-3 flex gap-2 overflow-x-auto">
          {([
            ["profile", IconUser, "Profile"],
            ["themes", IconPalette, "Appearance"],
            ["privacy", IconShield, "Privacy"],
            ["about", IconInfo, "About"],
          ] as const).map(([key, Icon, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className="chip tap flex shrink-0 items-center gap-1.5 px-3.5 py-2 text-[11.5px] font-bold"
              style={tab === key ? { borderColor: "var(--p)", background: "color-mix(in srgb, var(--p) 20%, transparent)" } : undefined}
            >
              <Icon size={14} /> {label}
            </button>
          ))}
        </div>
      </div>

      {tab === "profile" && (
        <div className="glass space-y-3 px-4 py-5">
          <div className="flex items-center gap-4">
            <button onClick={() => fileRef.current?.click()} className="tap relative grid h-20 w-20 place-items-center overflow-hidden rounded-2xl" style={{ background: avatar || me.avatarUrl ? "#000" : accentGradient(accent) }}>
              {avatar || me.avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={avatar ?? me.avatarUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <IconImage size={20} className="text-black/70" />
              )}
              <span className="absolute inset-x-0 bottom-0 py-1 text-center text-[9.5px] font-bold uppercase tracking-wider" style={{ background: "rgba(0,0,0,.5)" }}>
                change
              </span>
            </button>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              hidden
              onChange={async (e) => {
                const f = e.target.files?.[0];
                if (f) setAvatar(await fileToDataUrl(f));
              }}
            />
            <div className="flex-1 text-[11.5px] leading-relaxed text-[color:var(--muted)]">
              Pictures are stored in the <span className="font-semibold text-[color:var(--fg)]">avatars</span> bucket
              of the file store and update everywhere instantly.
            </div>
          </div>

          <Row label="Display name">
            <input value={name} onChange={(e) => setName(e.target.value)} className="w-full bg-transparent text-[13.5px] outline-none" />
          </Row>
          <Row label="Username">
            <input value={handle} onChange={(e) => setHandle(e.target.value.toLowerCase())} className="w-full bg-transparent text-[13.5px] outline-none" />
          </Row>
          <Row label="Bio">
            <input value={bio} onChange={(e) => setBio(e.target.value)} className="w-full bg-transparent text-[13.5px] outline-none" />
          </Row>
          <Row label="City">
            <input value={location} onChange={(e) => setLocation(e.target.value)} className="w-full bg-transparent text-[13.5px] outline-none" />
          </Row>

          <div>
            <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">Accent</p>
            <div className="flex flex-wrap gap-2">
              {ACCENT_KEYS.map((key) => (
                <button
                  key={key}
                  onClick={() => setAccent(key)}
                  aria-label={key}
                  className="tap grid h-9 w-9 place-items-center rounded-full"
                  style={{ background: accentGradient(key), boxShadow: accent === key ? "0 0 0 2.5px var(--fg)" : "none" }}
                >
                  {accent === key && <IconCheck size={14} className="text-black/80" />}
                </button>
              ))}
            </div>
          </div>

          <button onClick={saveProfile} disabled={saving} className="btn-primary w-full py-3 text-sm disabled:opacity-50">
            {saving ? "Saving…" : "Save profile"}
          </button>
        </div>
      )}

      {tab === "themes" && (
        <div className="glass px-4 py-5">
          <p className="mb-3 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
            App theme — changes every screen instantly
          </p>
          <div className="grid grid-cols-2 gap-3">
            {THEMES.map((t) => {
              const on = me.themeId === t.id;
              return (
                <button
                  key={t.id}
                  onClick={() => onUpdated({ action: "profile", themeId: t.id })}
                  className="tap overflow-hidden rounded-2xl text-left"
                  style={{
                    border: on ? "1.5px solid var(--p)" : "1px solid var(--glass-border)",
                    boxShadow: on ? "0 0 0 4px color-mix(in srgb, var(--p) 18%, transparent)" : undefined,
                  }}
                >
                  <div
                    className="h-20 w-full"
                    style={{
                      background: `radial-gradient(120px 80px at 20% 20%, ${t.vars["--p"]}, transparent 70%), radial-gradient(120px 80px at 80% 40%, ${t.vars["--s"]}, transparent 70%), radial-gradient(140px 100px at 50% 100%, ${t.vars["--t"]}, transparent 70%), ${t.vars["--bg"]}`,
                    }}
                  />
                  <div className="flex items-center gap-2 px-3 py-2.5" style={{ background: t.vars["--bg2"] }}>
                    <span className="text-[12px] font-bold" style={{ color: t.vars["--fg"] }}>
                      {t.name}
                    </span>
                    {on && <IconCheck size={14} className="ml-auto" />}
                  </div>
                </button>
              );
            })}
          </div>
          <Toggle
            label="Reduce motion"
            hint="Freezes the aurora and bubbles for battery saving"
            value={!!me.reduceMotion}
            onChange={(v) => onUpdated({ action: "profile", reduceMotion: v })}
          />
        </div>
      )}

      {tab === "privacy" && (
        <div className="glass px-4 py-5">
          <Toggle
            label="Activity status"
            hint="When on, friends see you online whenever you have a connection, and last-seen when you drop off"
            value={me.activityVisible}
            onChange={(v) => onUpdated({ action: "profile", activityVisible: v })}
          />
          <Toggle
            label="Read receipts"
            hint="Show others when you have opened their message"
            value={!!me.readReceipts}
            onChange={(v) => onUpdated({ action: "profile", readReceipts: v })}
          />
          <div className="mt-5">
            <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
              Blocked accounts ({blocked.length})
            </p>
            {blocked.length === 0 ? (
              <p className="text-[12.5px] text-[color:var(--muted)]">Nobody is blocked.</p>
            ) : (
              <div className="space-y-1.5">
                {blocked.map((id) => {
                  const p = people.find((x) => x.id === id);
                  return (
                    <div key={id} className="glass-soft flex items-center gap-3 px-3 py-2.5">
                      <Avatar name={p?.name ?? "User"} src={p?.avatarUrl} accent={p?.accent} size={34} />
                      <p className="flex-1 text-[13px] font-semibold">{p?.name ?? id}</p>
                      <button onClick={() => onUnblock(id)} className="chip tap px-3 py-1.5 text-[11px] font-bold">
                        Unblock
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
          <button
            onClick={onLogout}
            className="tap mt-6 flex w-full items-center justify-center gap-2 rounded-xl py-3 text-[13px] font-bold"
            style={{ background: "rgba(255,77,109,.16)", color: "#ff9db0" }}
          >
            <IconLogout size={16} /> Sign out
          </button>
        </div>
      )}

      {tab === "about" && (
        <div className="space-y-3">
          <div className="glass px-5 py-6 text-center">
            <div className="mx-auto grid h-24 w-24 place-items-center rounded-full ring-aurora" style={{ padding: 3 }}>
              <div className="grid h-full w-full place-items-center rounded-full" style={{ background: "var(--bg2)" }}>
                <span className="text-2xl font-black grad-text">RI</span>
              </div>
            </div>
            <h3 className="mt-4 text-[19px] font-black tracking-tight">Rakibul Islam</h3>
            <p className="text-[12.5px] text-[color:var(--muted)]">Kapilmuni College</p>
            <p className="mx-auto mt-3 max-w-md text-[13px] leading-relaxed text-[color:var(--fg)]/82">
              I build fast, expressive social products. Convo Nexus is my flagship messenger:
              stories, a public feed, private groups, encrypted backups and WebRTC calling — all
              wrapped in a liquid-glass interface that holds 60fps even on budget hardware.
            </p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              {["Android · Jetpack Compose", "Kotlin", "Firebase", "Supabase Storage", "WebRTC", "Next.js"].map((t) => (
                <span key={t} className="chip px-3 py-1.5 text-[11px] font-semibold">
                  {t}
                </span>
              ))}
            </div>
          </div>

          <div className="glass divide-y divide-white/6 px-4">
            {[
              ["Creator", "Rakibul Islam"],
              ["Studies at", "Kapilmuni College"],
              ["Role", "Android engineer · product designer"],
              ["Build", "Convo Nexus 5.0"],
              ["Text & graph data", "Firebase Firestore + Realtime Database"],
              ["Media & files", "Supabase Storage buckets"],
              ["Offline cache", "Room + progressive media cache"],
              ["Motion engine", "GlassKit draw-phase animations"],
            ].map(([k, v]) => (
              <div key={k} className="flex items-baseline justify-between gap-4 py-3">
                <span className="text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">{k}</span>
                <span className="text-right text-[13px] font-semibold">{v}</span>
              </div>
            ))}
          </div>

          <div className="glass px-4 py-4">
            <p className="flex items-center gap-2 text-[12px] font-bold">
              <IconEye size={14} /> What is new in 5.0
            </p>
            <ul className="mt-2 space-y-1.5 text-[12.5px] leading-relaxed text-[color:var(--muted)]">
              <li>Stories with 12-hour auto-delete and inline reactions</li>
              <li>Facebook-style feed: reactions, comments, view counts, video autoplay</li>
              <li>Public / private posts, editable after publishing</li>
              <li>Messages moved to their own hub with swipe-to-reply</li>
              <li>Groups with cover art and notification-free delivery</li>
              <li>Ten dynamic themes with live accent colours</li>
              <li>Progressive loading with glass shimmer skeletons</li>
            </ul>
          </div>
          <div className="flex items-center justify-center gap-2 pb-2 text-[10.5px] text-[color:var(--muted)]">
            <IconHeart size={12} /> Made with care in Bangladesh
          </div>
        </div>
      )}
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="field block px-3.5 py-2.5">
      <span className="mb-0.5 block text-[10px] font-bold uppercase tracking-wider text-[color:var(--muted)]">{label}</span>
      {children}
    </label>
  );
}

function Toggle({
  label,
  hint,
  value,
  onChange,
}: {
  label: string;
  hint: string;
  value: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="mt-4 flex items-start gap-3">
      <div className="min-w-0 flex-1">
        <p className="text-[13.5px] font-bold">{label}</p>
        <p className="mt-0.5 text-[11.5px] leading-relaxed text-[color:var(--muted)]">{hint}</p>
      </div>
      <button
        onClick={() => onChange(!value)}
        className="tap relative h-7 w-12 shrink-0 rounded-full transition-colors"
        style={{ background: value ? "linear-gradient(120deg, var(--p), var(--s))" : "color-mix(in srgb, var(--fg) 14%, transparent)" }}
        aria-label={label}
      >
        <span
          className="absolute top-1 h-5 w-5 rounded-full bg-white transition-all"
          style={{ left: value ? 26 : 4, boxShadow: "0 2px 8px rgba(0,0,0,.35)" }}
        />
      </button>
    </div>
  );
}
