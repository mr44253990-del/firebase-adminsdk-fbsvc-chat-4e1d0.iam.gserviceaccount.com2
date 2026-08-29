import { NextResponse } from "next/server";
import { and, eq } from "drizzle-orm";
import { db } from "@/db";
import { media, stories, storyViews } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";
import { pushNotification } from "@/lib/queries";

const TWELVE_HOURS = 12 * 3_600_000;

export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const body = (await request.json().catch(() => ({}))) as Record<string, string>;
  const action = body.action ?? "create";
  const now = Date.now();

  if (action === "create") {
    if (!body.mediaUrl) return NextResponse.json({ error: "Pick a photo or video" }, { status: 400 });
    const id = newId("s");
    await db.insert(stories).values({
      id,
      authorId: user.id,
      mediaUrl: body.mediaUrl,
      mediaKind: body.mediaKind ?? "image",
      caption: body.caption ?? "",
      accent: body.accent ?? user.accent,
      createdAt: now,
      expiresAt: now + TWELVE_HOURS,
    });
    return NextResponse.json({ ok: true, id, expiresAt: now + TWELVE_HOURS });
  }

  const storyId = body.storyId ?? "";
  const rows = await db.select().from(stories).where(eq(stories.id, storyId)).limit(1);
  const story = rows[0];
  if (!story) return NextResponse.json({ error: "Story expired" }, { status: 404 });

  if (action === "view" || action === "react") {
    const reaction = action === "react" ? body.reaction ?? "love" : "";
    const seen = await db
      .select()
      .from(storyViews)
      .where(and(eq(storyViews.storyId, storyId), eq(storyViews.userId, user.id)))
      .limit(1);
    if (seen[0]) {
      if (action === "react") {
        await db.update(storyViews).set({ reaction }).where(eq(storyViews.id, seen[0].id));
      }
    } else {
      await db.insert(storyViews).values({
        id: newId("sv"),
        storyId,
        userId: user.id,
        reaction,
        createdAt: now,
      });
    }
    if (action === "react") {
      await pushNotification(story.authorId, user.id, "story", storyId, `reacted ${reaction} to your story`);
    }
    return NextResponse.json({ ok: true });
  }

  if (action === "delete") {
    if (story.authorId !== user.id) return NextResponse.json({ error: "Not your story" }, { status: 403 });
    if (story.mediaUrl.startsWith("/api/media/")) {
      await db.delete(media).where(eq(media.id, story.mediaUrl.slice("/api/media/".length)));
    }
    await db.delete(storyViews).where(eq(storyViews.storyId, storyId));
    await db.delete(stories).where(eq(stories.id, storyId));
    return NextResponse.json({ ok: true });
  }

  return NextResponse.json({ error: "Unknown action" }, { status: 400 });
}
