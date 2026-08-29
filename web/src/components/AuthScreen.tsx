"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Backdrop } from "@/components/ui";
import { ACCENT_KEYS, accentGradient, THEMES } from "@/lib/themes";
import { fileToDataUrl, initials } from "@/lib/format";
import { IconCheck, IconImage, IconLock, IconSpark, IconUser } from "@/components/icons";

type Mode = "login" | "signup";

const HEADLINES = [
  "Stories that breathe.",
  "Conversations that glow.",
  "A feed that feels alive.",
  "Groups built for the night shift.",
];

export function AuthScreen({ onDone }: { onDone: () => void }) {
  const [mode, setMode] = useState<Mode>("login");
  const [identifier, setIdentifier] = useState("rakibul");
  const [password, setPassword] = useState("nexus1234");
  const [name, setName] = useState("");
  const [handle, setHandle] = useState("");
  const [email, setEmail] = useState("");
  const [signupPass, setSignupPass] = useState("");
  const [bio, setBio] = useState("");
  const [location, setLocation] = useState("");
  const [accent, setAccent] = useState("aurora");
  const [themeId, setThemeId] = useState("liquid-aurora");
  const [avatar, setAvatar] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [headline, setHeadline] = useState(0);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const t = setInterval(() => setHeadline((h) => (h + 1) % HEADLINES.length), 3400);
    return () => clearInterval(t);
  }, []);

  const themeVars = useMemo(
    () => THEMES.find((t) => t.id === themeId)?.vars ?? THEMES[0].vars,
    [themeId],
  );

  async function pickAvatar(file: File | undefined) {
    if (!file) return;
    if (file.size > 5.5 * 1024 * 1024) {
      setError("Picture must be under 5 MB");
      return;
    }
    setAvatar(await fileToDataUrl(file));
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      let avatarUrl = "";
      if (mode === "signup" && avatar) {
        const up = await fetch("/api/upload", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ dataUrl: avatar, bucket: "avatars", kind: "image" }),
        });
        // Avatar upload needs a session; signup creates it, so stash the data URL instead.
        if (up.ok) avatarUrl = (await up.json()).url as string;
      }

      const payload =
        mode === "login"
          ? { action: "login", identifier, password }
          : {
              action: "signup",
              name,
              handle,
              email,
              password: signupPass,
              bio,
              location,
              accent,
              themeId,
              avatarUrl,
            };

      const res = await fetch("/api/auth", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const json = await res.json();
      if (!res.ok) {
        setError(json.error ?? "Something went wrong");
        setBusy(false);
        return;
      }

      // Now that a session exists, persist the avatar binary.
      if (mode === "signup" && avatar && !avatarUrl) {
        const up = await fetch("/api/upload", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ dataUrl: avatar, bucket: "avatars", kind: "image" }),
        });
        if (up.ok) {
          const { url } = await up.json();
          await fetch("/api/me", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ action: "profile", avatarUrl: url }),
          });
        }
      }
      onDone();
    } catch {
      setError("Network hiccup. Try again.");
      setBusy(false);
    }
  }

  const canSubmit =
    mode === "login"
      ? identifier.trim().length > 2 && password.length >= 6
      : name.trim().length > 1 && handle.trim().length > 2 && email.includes("@") && signupPass.length >= 6;

  return (
    <div className="relative h-full w-full overflow-hidden" style={themeVars as React.CSSProperties}>
      <Backdrop bubbles={22} />

      <div className="scroller relative z-10 h-full">
        <div className="mx-auto flex min-h-full w-full max-w-6xl flex-col items-center gap-8 px-5 py-10 lg:flex-row lg:items-center lg:gap-14 lg:py-0">
          {/* Hero */}
          <div className="w-full max-w-xl lg:flex-1">
            <div className="flex items-center gap-3">
              <div
                className="grid h-12 w-12 place-items-center rounded-2xl"
                style={{ background: accentGradient(accent), boxShadow: "0 12px 30px -10px var(--p)" }}
              >
                <IconSpark size={24} className="text-black/80" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.28em] text-[color:var(--muted)]">
                  Convo
                </p>
                <p className="-mt-0.5 text-2xl font-black leading-none grad-text">NEXUS</p>
              </div>
            </div>

            <h1 className="mt-8 text-4xl font-black leading-[1.05] tracking-tight sm:text-6xl">
              <span className="grad-text">{HEADLINES[headline]}</span>
            </h1>
            <p className="mt-5 max-w-md text-[15px] leading-relaxed text-[color:var(--muted)]">
              One place for stories, a living feed, private groups and calls that never drop.
              Media lives in the Supabase file store, everything else streams in real time.
            </p>

            <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4">
              {[
                ["12h", "story life"],
                ["60fps", "on budget phones"],
                ["10", "dynamic themes"],
                ["E2E", "encrypted backups"],
              ].map(([a, b]) => (
                <div key={b} className="glass-soft px-3 py-3">
                  <p className="text-lg font-black grad-text">{a}</p>
                  <p className="text-[11px] uppercase tracking-wider text-[color:var(--muted)]">{b}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Card */}
          <div className="w-full max-w-md lg:w-[420px]">
            <div className="glass anim-up p-6 sm:p-7">
              <div className="mb-6 flex rounded-2xl p-1" style={{ background: "color-mix(in srgb, var(--fg) 7%, transparent)" }}>
                {(["login", "signup"] as Mode[]).map((m) => (
                  <button
                    key={m}
                    onClick={() => {
                      setMode(m);
                      setError(null);
                    }}
                    className="tap relative flex-1 rounded-xl py-2.5 text-sm font-bold capitalize transition-colors"
                    style={
                      mode === m
                        ? { background: accentGradient(accent), color: "#07070f" }
                        : { color: "var(--muted)" }
                    }
                  >
                    {m === "login" ? "Sign in" : "Create account"}
                  </button>
                ))}
              </div>

              {mode === "login" ? (
                <div className="space-y-3">
                  <Field label="Username or email" icon={<IconUser size={17} />}>
                    <input
                      value={identifier}
                      onChange={(e) => setIdentifier(e.target.value)}
                      placeholder="rakibul"
                      className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                    />
                  </Field>
                  <Field label="Password" icon={<IconLock size={17} />}>
                    <input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                    />
                  </Field>
                  <p className="pt-1 text-[11px] leading-relaxed text-[color:var(--muted)]">
                    Demo world is pre-loaded. Any seeded username works with password
                    <span className="font-semibold text-[color:var(--fg)]"> nexus1234</span>.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center gap-4">
                    <button
                      onClick={() => fileRef.current?.click()}
                      className="tap relative grid h-20 w-20 shrink-0 place-items-center overflow-hidden rounded-2xl"
                      style={{ background: avatar ? "#000" : accentGradient(accent) }}
                    >
                      {avatar ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={avatar} alt="avatar" className="h-full w-full object-cover" />
                      ) : (
                        <span className="text-2xl font-black text-black/70">
                          {name ? initials(name) : <IconImage size={22} />}
                        </span>
                      )}
                      <span
                        className="absolute inset-x-0 bottom-0 py-1 text-center text-[10px] font-bold uppercase tracking-wider"
                        style={{ background: "rgba(0,0,0,.45)" }}
                      >
                        photo
                      </span>
                    </button>
                    <input
                      ref={fileRef}
                      type="file"
                      accept="image/*"
                      hidden
                      onChange={(e) => pickAvatar(e.target.files?.[0])}
                    />
                    <div className="flex-1 space-y-2">
                      <Field label="Full name">
                        <input
                          value={name}
                          onChange={(e) => setName(e.target.value)}
                          placeholder="Rakibul Islam"
                          className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                        />
                      </Field>
                      <Field label="Username">
                        <input
                          value={handle}
                          onChange={(e) => setHandle(e.target.value.toLowerCase())}
                          placeholder="rakib.dev"
                          className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                        />
                      </Field>
                    </div>
                  </div>

                  <Field label="Email">
                    <input
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@mail.com"
                      className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                    />
                  </Field>
                  <div className="grid grid-cols-2 gap-2">
                    <Field label="Password">
                      <input
                        type="password"
                        value={signupPass}
                        onChange={(e) => setSignupPass(e.target.value)}
                        placeholder="6+ characters"
                        className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                      />
                    </Field>
                    <Field label="City">
                      <input
                        value={location}
                        onChange={(e) => setLocation(e.target.value)}
                        placeholder="Khulna"
                        className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                      />
                    </Field>
                  </div>
                  <Field label="Bio">
                    <input
                      value={bio}
                      onChange={(e) => setBio(e.target.value)}
                      placeholder="One line about you"
                      className="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--muted)]"
                    />
                  </Field>

                  <div>
                    <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-[color:var(--muted)]">
                      Accent
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {ACCENT_KEYS.map((key) => (
                        <button
                          key={key}
                          onClick={() => setAccent(key)}
                          className="tap grid h-8 w-8 place-items-center rounded-full"
                          style={{
                            background: accentGradient(key),
                            boxShadow: accent === key ? "0 0 0 2.5px var(--fg)" : "none",
                          }}
                          aria-label={key}
                        >
                          {accent === key && <IconCheck size={14} className="text-black/80" />}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-[color:var(--muted)]">
                      Starting theme
                    </p>
                    <div className="no-bar flex gap-2 overflow-x-auto pb-1">
                      {THEMES.map((t) => (
                        <button
                          key={t.id}
                          onClick={() => setThemeId(t.id)}
                          className="chip tap shrink-0 px-3 py-1.5 text-[11px] font-semibold"
                          style={
                            themeId === t.id
                              ? { borderColor: "var(--p)", background: "color-mix(in srgb, var(--p) 22%, transparent)" }
                              : undefined
                          }
                        >
                          {t.name}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {error && (
                <p className="anim-pop mt-4 rounded-xl px-3 py-2 text-xs font-semibold"
                   style={{ background: "rgba(255,77,109,.14)", color: "#ff9db0" }}>
                  {error}
                </p>
              )}

              <button
                disabled={!canSubmit || busy}
                onClick={submit}
                className="btn-primary mt-5 w-full py-3.5 text-sm disabled:cursor-not-allowed disabled:opacity-45"
              >
                {busy ? "Working…" : mode === "login" ? "Enter Nexus" : "Create my account"}
              </button>

              <p className="mt-4 text-center text-[11px] leading-relaxed text-[color:var(--muted)]">
                Profile pictures are stored in the file store bucket
                <span className="font-semibold text-[color:var(--fg)]"> avatars</span> and stay in sync
                across every device.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  icon,
  children,
}: {
  label: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <label className="field block px-3.5 py-2.5">
      <span className="mb-0.5 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
        {icon}
        {label}
      </span>
      {children}
    </label>
  );
}
