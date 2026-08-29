import { NextResponse } from "next/server";
import { db } from "@/db";
import { media } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";

const MAX_BYTES = 6 * 1024 * 1024;

/**
 * Media ingest. Mirrors the Supabase Storage contract used by the Android client:
 * the caller receives a stable public URL, and deleting the owning record deletes
 * the binary too. Bucket names match the mobile app (avatars/posts/stories/voice/groups).
 */
export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }

  const body = (await request.json().catch(() => ({}))) as {
    dataUrl?: string;
    bucket?: string;
    kind?: string;
  };
  const dataUrl = body.dataUrl ?? "";
  const match = /^data:([^;]+);base64,(.+)$/.exec(dataUrl);
  if (!match) return NextResponse.json({ error: "Unsupported file" }, { status: 400 });

  const mime = match[1];
  const base64 = match[2];
  const size = Math.floor((base64.length * 3) / 4);
  if (size > MAX_BYTES) return NextResponse.json({ error: "File is larger than 6 MB" }, { status: 413 });

  const kind = body.kind ?? (mime.startsWith("video/") ? "video" : mime.startsWith("audio/") ? "audio" : "image");
  const bucket = body.bucket ?? "posts";
  const id = newId("md");
  await db.insert(media).values({
    id,
    ownerId: user.id,
    bucket,
    path: `${user.id}/${id}`,
    mimeType: mime,
    kind,
    data: base64,
    sizeBytes: size,
    createdAt: Date.now(),
  });

  return NextResponse.json({ url: `/api/media/${id}`, kind, id, bucket });
}
