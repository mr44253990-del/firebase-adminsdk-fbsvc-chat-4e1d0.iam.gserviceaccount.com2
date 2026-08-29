import { NextResponse } from "next/server";
import { and, eq, sql } from "drizzle-orm";
import { db } from "@/db";
import { comments, media, postViews, posts, reactions } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";
import { pushNotification } from "@/lib/queries";

async function dropMedia(url: string | null | undefined) {
  if (!url) return;
  const id = url.startsWith("/api/media/") ? url.slice("/api/media/".length) : null;
  if (id) await db.delete(media).where(eq(media.id, id));
}

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
    const text = (body.body ?? "").trim();
    if (!text && !body.mediaUrl) return NextResponse.json({ error: "Write something first" }, { status: 400 });
    const id = newId("p");
    await db.insert(posts).values({
      id,
      authorId: user.id,
      body: text,
      mediaUrl: body.mediaUrl ?? null,
      mediaKind: body.mediaKind ?? null,
      visibility: body.visibility === "private" ? "private" : "public",
      views: 0,
      edited: false,
      createdAt: now,
      updatedAt: now,
    });
    return NextResponse.json({ ok: true, id });
  }

  const postId = body.postId ?? "";
  const rows = await db.select().from(posts).where(eq(posts.id, postId)).limit(1);
  const post = rows[0];
  if (!post) return NextResponse.json({ error: "Post not found" }, { status: 404 });

  if (action === "react") {
    const kind = body.kind ?? "like";
    const existing = await db
      .select()
      .from(reactions)
      .where(and(eq(reactions.postId, postId), eq(reactions.userId, user.id)))
      .limit(1);
    if (existing[0]?.kind === kind) {
      await db.delete(reactions).where(eq(reactions.id, existing[0].id));
      return NextResponse.json({ ok: true, myReaction: null });
    }
    if (existing[0]) {
      await db.update(reactions).set({ kind, createdAt: now }).where(eq(reactions.id, existing[0].id));
    } else {
      await db.insert(reactions).values({ id: newId("r"), postId, userId: user.id, kind, createdAt: now });
    }
    await pushNotification(post.authorId, user.id, "reaction", postId, `reacted ${kind} to your post`);
    return NextResponse.json({ ok: true, myReaction: kind });
  }

  if (action === "comment") {
    const text = (body.body ?? "").trim();
    if (!text) return NextResponse.json({ error: "Empty comment" }, { status: 400 });
    await db.insert(comments).values({ id: newId("c"), postId, userId: user.id, body: text, createdAt: now });
    await pushNotification(post.authorId, user.id, "comment", postId, "commented on your post");
    return NextResponse.json({ ok: true });
  }

  if (action === "view") {
    const seen = await db
      .select()
      .from(postViews)
      .where(and(eq(postViews.postId, postId), eq(postViews.userId, user.id)))
      .limit(1);
    if (!seen.length) {
      await db.insert(postViews).values({ id: newId("v"), postId, userId: user.id, createdAt: now });
      await db.update(posts).set({ views: sql`${posts.views} + 1` }).where(eq(posts.id, postId));
    }
    return NextResponse.json({ ok: true });
  }

  if (post.authorId !== user.id) return NextResponse.json({ error: "Not your post" }, { status: 403 });

  if (action === "update") {
    await db
      .update(posts)
      .set({
        body: body.body !== undefined ? body.body : post.body,
        visibility: body.visibility === "private" ? "private" : body.visibility === "public" ? "public" : post.visibility,
        edited: true,
        updatedAt: now,
      })
      .where(eq(posts.id, postId));
    return NextResponse.json({ ok: true });
  }

  if (action === "delete") {
    await dropMedia(post.mediaUrl);
    await db.delete(reactions).where(eq(reactions.postId, postId));
    await db.delete(comments).where(eq(comments.postId, postId));
    await db.delete(postViews).where(eq(postViews.postId, postId));
    await db.delete(posts).where(eq(posts.id, postId));
    return NextResponse.json({ ok: true });
  }

  return NextResponse.json({ error: "Unknown action" }, { status: 400 });
}
