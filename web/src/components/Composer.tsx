"use client";

import { useRef, useState } from "react";
import type { PublicUser } from "@/lib/queries";
import { Avatar, Sheet } from "@/components/ui";
import { ACCENT_KEYS, accentGradient } from "@/lib/themes";
import { fileToDataUrl } from "@/lib/format";
import { IconClose, IconGlobe, IconImage, IconLock, IconNote, IconSpark } from "@/components/icons";

type Kind = "post" | "story" | "note";

export function Composer({
  open,
  kind,
  me,
  onClose,
  onDone,
}: {
  open: boolean;
  kind: Kind;
  me: PublicUser;
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState<"public" | "private">("public");
  const [accent, setAccent] = useState(me.accent);
  const [preview, setPreview] = useState<{ dataUrl: string; kind: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  function reset() {
    setBody("");
    setPreview(null);
    setError(null);
    setBusy(false);
  }

  async function pick(file: File | undefined) {
    if (!file) return;
    if (file.size > 5.5 * 1024 * 1024) {
      setError("Keep it under 5 MB so it loads instantly for everyone.");
      return;
    }
    const dataUrl = await fileToDataUrl(file);
    setPreview({ dataUrl, kind: file.type.startsWith("video/") ? "video" : "image" });
    setError(null);
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      let mediaUrl: string | null = null;
      let mediaKind: string | null = null;
      if (preview) {
        const res = await fetch("/api/upload", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            dataUrl: preview.dataUrl,
            bucket: kind === "story" ? "stories" : "posts",
            kind: preview.kind,
          }),
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error ?? "Upload failed");
        mediaUrl = json.url;
        mediaKind = json.kind;
      }

      if (kind === "note") {
        const res = await fetch("/api/me", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "note", body }),
        });
        if (!res.ok) throw new Error("Could not save the note");
        onDone("Note is live for 24 hours");
      } else if (kind === "story") {
        if (!mediaUrl) throw new Error("Pick a photo or video first");
        const res = await fetch("/api/stories", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "create", mediaUrl, mediaKind, caption: body, accent }),
        });
        if (!res.ok) throw new Error("Could not publish the story");
        onDone("Story published — it vanishes in 12 hours");
      } else {
        const res = await fetch("/api/posts", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "create", body, mediaUrl, mediaKind, visibility }),
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error ?? "Could not publish");
        onDone(visibility === "private" ? "Posted privately" : "Posted to the feed");
      }
      reset();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
      setBusy(false);
    }
  }

  const title = kind === "story" ? "New story" : kind === "note" ? "Set a note" : "Create post";

  return (
    <Sheet open={open} onClose={onClose} title={title} wide>
      <div className="flex items-center gap-3">
        <Avatar name={me.name} src={me.avatarUrl} accent={me.accent} size={44} ring="aurora" />
        <div className="min-w-0">
          <p className="text-sm font-bold">{me.name}</p>
          <p className="text-[11px] text-[color:var(--muted)]">
            {kind === "note"
              ? "Visible next to your name for 24 hours"
              : kind === "story"
                ? "Auto-deletes after 12 hours"
                : visibility === "public"
                  ? "Everyone on Nexus can see this"
                  : "Only you can see this"}
          </p>
        </div>
      </div>

      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        rows={kind === "note" ? 2 : 4}
        maxLength={kind === "note" ? 90 : 1200}
        placeholder={
          kind === "note"
            ? "Ask me about…"
            : kind === "story"
              ? "Add a caption"
              : "What is happening?"
        }
        className="field mt-4 w-full resize-none bg-transparent px-4 py-3.5 text-[15px] leading-relaxed outline-none placeholder:text-[color:var(--muted)]"
      />

      {kind !== "note" && (
        <>
          {preview && (
            <div className="anim-pop relative mt-3 overflow-hidden rounded-2xl" style={{ background: "#05060d" }}>
              {preview.kind === "video" ? (
                <video src={preview.dataUrl} muted loop autoPlay playsInline className="max-h-72 w-full object-cover" />
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={preview.dataUrl} alt="" className="max-h-72 w-full object-cover" />
              )}
              <button
                onClick={() => setPreview(null)}
                className="glass-soft tap absolute right-3 top-3 p-2"
                aria-label="Remove media"
              >
                <IconClose size={15} />
              </button>
            </div>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <button onClick={() => fileRef.current?.click()} className="chip tap flex items-center gap-2 px-3.5 py-2 text-[12px] font-semibold">
              <IconImage size={15} /> {preview ? "Change media" : "Photo or video"}
            </button>
            <input
              ref={fileRef}
              type="file"
              accept="image/*,video/*"
              hidden
              onChange={(e) => pick(e.target.files?.[0])}
            />

            {kind === "post" &&
              (["public", "private"] as const).map((v) => (
                <button
                  key={v}
                  onClick={() => setVisibility(v)}
                  className="chip tap flex items-center gap-1.5 px-3.5 py-2 text-[12px] font-semibold capitalize"
                  style={
                    visibility === v
                      ? { borderColor: "var(--p)", background: "color-mix(in srgb, var(--p) 20%, transparent)" }
                      : undefined
                  }
                >
                  {v === "public" ? <IconGlobe size={14} /> : <IconLock size={14} />} {v}
                </button>
              ))}
          </div>

          {kind === "story" && (
            <div className="mt-4">
              <p className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-[color:var(--muted)]">
                <IconSpark size={13} /> Ring colour
              </p>
              <div className="flex flex-wrap gap-2">
                {ACCENT_KEYS.map((key) => (
                  <button
                    key={key}
                    onClick={() => setAccent(key)}
                    aria-label={key}
                    className="tap h-8 w-8 rounded-full"
                    style={{
                      background: accentGradient(key),
                      boxShadow: accent === key ? "0 0 0 2.5px var(--fg)" : "none",
                    }}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {error && (
        <p className="anim-pop mt-3 rounded-xl px-3 py-2 text-xs font-semibold" style={{ background: "rgba(255,77,109,.14)", color: "#ff9db0" }}>
          {error}
        </p>
      )}

      <div className="mt-5 flex gap-2 pb-2">
        <button
          onClick={submit}
          disabled={busy || (kind === "story" ? !preview : kind === "post" ? !body.trim() && !preview : false)}
          className="btn-primary flex-1 py-3 text-sm disabled:opacity-45"
        >
          {busy ? "Publishing…" : kind === "note" ? "Save note" : kind === "story" ? "Share story" : "Publish"}
        </button>
        {kind === "note" && (
          <button
            onClick={async () => {
              setBody("");
              await fetch("/api/me", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action: "note", body: "" }),
              });
              onDone("Note cleared");
              onClose();
            }}
            className="chip tap flex items-center gap-2 px-4 text-[12px] font-semibold"
          >
            <IconNote size={14} /> Clear
          </button>
        )}
      </div>
    </Sheet>
  );
}
