"use client";

import { useMemo } from "react";
import type { CSSProperties, ReactNode } from "react";
import { accentGradient } from "@/lib/themes";
import { initials } from "@/lib/format";
import { IconVerified } from "@/components/icons";

/** Aurora canvas + rising bubble field. Pure CSS transforms, GPU only. */
export function Backdrop({ bubbles = 16 }: { bubbles?: number }) {
  const items = useMemo(
    () =>
      Array.from({ length: bubbles }, (_, i) => {
        const seed = (i * 9301 + 49297) % 233280;
        const r = seed / 233280;
        const r2 = ((i + 3) * 4177 + 7561) % 100 / 100;
        return {
          key: i,
          left: `${Math.round(r * 96)}%`,
          size: 10 + Math.round(r2 * 46),
          duration: `${16 + Math.round(r * 22)}s`,
          delay: `${-Math.round(r2 * 24)}s`,
          dx: `${(r - 0.5) * 12}vw`,
          opacity: 0.25 + r2 * 0.4,
        };
      }),
    [bubbles],
  );

  return (
    <div className="aurora" aria-hidden>
      <div className="aurora-blob blob-1" />
      <div className="aurora-blob blob-2" />
      <div className="aurora-blob blob-3" />
      {items.map((b) => (
        <span
          key={b.key}
          className="bubble"
          style={{
            left: b.left,
            width: b.size,
            height: b.size,
            animationDuration: b.duration,
            animationDelay: b.delay,
            opacity: b.opacity,
            ["--dx" as string]: b.dx,
          }}
        />
      ))}
      <div
        className="absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(circle at 1px 1px, rgba(255,255,255,.055) 1px, transparent 0)",
          backgroundSize: "26px 26px",
        }}
      />
    </div>
  );
}

type AvatarProps = {
  name: string;
  src?: string | null;
  accent?: string;
  size?: number;
  ring?: "none" | "aurora" | "seen";
  online?: boolean;
  verified?: boolean;
  onClick?: () => void;
  className?: string;
};

export function Avatar({
  name,
  src,
  accent = "aurora",
  size = 44,
  ring = "none",
  online,
  verified,
  onClick,
  className = "",
}: AvatarProps) {
  const pad = ring === "none" ? 0 : 3;
  const inner = size - pad * 2;
  return (
    <div
      className={`relative shrink-0 ${onClick ? "cursor-pointer tap" : ""} ${className}`}
      style={{ width: size, height: size }}
      onClick={onClick}
    >
      <div
        className={`h-full w-full rounded-full ${ring === "aurora" ? "ring-aurora" : ""}`}
        style={{
          padding: pad,
          background:
            ring === "seen"
              ? "color-mix(in srgb, var(--fg) 18%, transparent)"
              : ring === "none"
                ? "transparent"
                : undefined,
        }}
      >
        <div
          className="grid h-full w-full place-items-center overflow-hidden rounded-full"
          style={{
            background: src ? "rgba(0,0,0,.3)" : accentGradient(accent),
            boxShadow: "inset 0 0 0 1px rgba(255,255,255,.16)",
          }}
        >
          {src ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={src} alt={name} loading="lazy" className="h-full w-full object-cover" />
          ) : (
            <span
              className="font-bold tracking-tight text-black/80"
              style={{ fontSize: Math.max(11, inner * 0.38) }}
            >
              {initials(name)}
            </span>
          )}
        </div>
      </div>
      {online && (
        <span
          className="absolute rounded-full"
          style={{
            width: Math.max(9, size * 0.24),
            height: Math.max(9, size * 0.24),
            right: 0,
            bottom: 0,
            background: "#3ddc84",
            boxShadow: "0 0 0 2.5px var(--bg), 0 0 10px 2px rgba(61,220,132,.6)",
          }}
        />
      )}
      {verified && (
        <span
          className="absolute -right-1 -top-1 grid place-items-center rounded-full"
          style={{ background: "var(--bg)", padding: 1 }}
        >
          <IconVerified size={Math.max(12, size * 0.32)} className="text-[color:var(--s)]" />
        </span>
      )}
    </div>
  );
}

export function Shimmer({ className = "", style }: { className?: string; style?: CSSProperties }) {
  return <div className={`skeleton ${className}`} style={style} />;
}

export function PostSkeleton() {
  return (
    <div className="glass p-5">
      <div className="flex items-center gap-3">
        <Shimmer className="h-11 w-11 rounded-full" />
        <div className="flex-1 space-y-2">
          <Shimmer className="h-3 w-32" />
          <Shimmer className="h-2.5 w-20" />
        </div>
      </div>
      <div className="mt-4 space-y-2">
        <Shimmer className="h-3 w-full" />
        <Shimmer className="h-3 w-4/5" />
      </div>
      <Shimmer className="mt-4 h-52 w-full rounded-2xl" />
    </div>
  );
}

export function Sheet({
  open,
  onClose,
  title,
  children,
  wide,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  wide?: boolean;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div
        className="absolute inset-0"
        style={{ background: "rgba(3,4,12,.62)", backdropFilter: "blur(8px)" }}
        onClick={onClose}
      />
      <div
        className={`glass anim-up relative z-10 max-h-[88vh] w-full overflow-hidden ${wide ? "sm:max-w-2xl" : "sm:max-w-lg"}`}
        style={{ borderRadius: "28px 28px 0 0" }}
      >
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <h3 className="text-base font-bold">{title}</h3>
          <button onClick={onClose} className="chip tap p-2" aria-label="Close">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <div className="scroller max-h-[74vh] px-5 py-4">{children}</div>
      </div>
    </div>
  );
}

export function Toast({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="pointer-events-none fixed bottom-24 left-1/2 z-[60] -translate-x-1/2">
      <div className="glass anim-pop px-5 py-3 text-sm font-semibold" style={{ borderRadius: 999 }}>
        {message}
      </div>
    </div>
  );
}
