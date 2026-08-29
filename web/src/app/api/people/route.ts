import { NextResponse } from "next/server";
import { and, eq } from "drizzle-orm";
import { db } from "@/db";
import { blocks, follows, posts, users } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";
import { ensureSeed } from "@/lib/seed";
import { getFeed, getPeople, getProfileStats, pushNotification, toPublic } from "@/lib/queries";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  await ensureSeed();
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const url = new URL(request.url);
  const profileId = url.searchParams.get("profile");

  if (profileId) {
    const rows = await db.select().from(users).where(eq(users.id, profileId)).limit(1);
    const target = rows[0];
    if (!target) return NextResponse.json({ error: "No such profile" }, { status: 404 });
    const [stats, feed, followRow] = await Promise.all([
      getProfileStats(target.id),
      getFeed(user.id, 60),
      db
        .select()
        .from(follows)
        .where(and(eq(follows.followerId, user.id), eq(follows.followingId, target.id)))
        .limit(1),
    ]);
    const own = target.id === user.id;
    const authored = own
      ? (await db.select().from(posts).where(eq(posts.authorId, target.id))).map((p) => p.id)
      : [];
    return NextResponse.json({
      profile: toPublic(target),
      stats,
      following: followRow.length > 0,
      link: `convo.nexus/@${target.handle}`,
      posts: feed.filter((p) => p.author.id === target.id || authored.includes(p.id)),
    });
  }

  return NextResponse.json(await getPeople(user.id));
}

export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const body = (await request.json().catch(() => ({}))) as Record<string, string>;
  const targetId = body.targetId ?? "";
  if (!targetId || targetId === user.id) return NextResponse.json({ error: "Bad target" }, { status: 400 });
  const now = Date.now();

  switch (body.action) {
    case "follow":
      await db
        .insert(follows)
        .values({ id: newId("f"), followerId: user.id, followingId: targetId, createdAt: now })
        .onConflictDoNothing();
      await pushNotification(targetId, user.id, "follow", user.id, "started following you");
      return NextResponse.json({ ok: true });
    case "unfollow":
      await db
        .delete(follows)
        .where(and(eq(follows.followerId, user.id), eq(follows.followingId, targetId)));
      return NextResponse.json({ ok: true });
    case "block":
      await db
        .insert(blocks)
        .values({ id: newId("b"), userId: user.id, blockedId: targetId, createdAt: now })
        .onConflictDoNothing();
      await db
        .delete(follows)
        .where(and(eq(follows.followerId, user.id), eq(follows.followingId, targetId)));
      return NextResponse.json({ ok: true });
    case "unblock":
      await db.delete(blocks).where(and(eq(blocks.userId, user.id), eq(blocks.blockedId, targetId)));
      return NextResponse.json({ ok: true });
    default:
      return NextResponse.json({ error: "Unknown action" }, { status: 400 });
  }
}
