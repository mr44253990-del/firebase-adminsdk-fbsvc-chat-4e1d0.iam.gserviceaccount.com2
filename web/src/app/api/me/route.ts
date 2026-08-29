import { NextResponse } from "next/server";
import { and, eq, ne } from "drizzle-orm";
import { db } from "@/db";
import { blocks, notes, users } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";
import { toPublic } from "@/lib/queries";

export const dynamic = "force-dynamic";

const TWENTY_FOUR_HOURS = 24 * 3_600_000;

export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const action = (body.action as string) ?? "profile";

  if (action === "note") {
    const text = String(body.body ?? "").trim();
    await db.delete(notes).where(eq(notes.userId, user.id));
    if (text) {
      await db.insert(notes).values({
        id: newId("n"),
        userId: user.id,
        body: text.slice(0, 90),
        createdAt: Date.now(),
        expiresAt: Date.now() + TWENTY_FOUR_HOURS,
      });
    }
    return NextResponse.json({ ok: true });
  }

  const patch: Partial<typeof users.$inferInsert> = {};
  if (typeof body.name === "string" && body.name.trim().length >= 2) patch.name = body.name.trim();
  if (typeof body.bio === "string") patch.bio = body.bio.slice(0, 200);
  if (typeof body.location === "string") patch.location = body.location.slice(0, 80);
  if (typeof body.avatarUrl === "string") patch.avatarUrl = body.avatarUrl;
  if (typeof body.accent === "string") patch.accent = body.accent;
  if (typeof body.themeId === "string") patch.themeId = body.themeId;
  if (typeof body.activityVisible === "boolean") patch.activityVisible = body.activityVisible;
  if (typeof body.readReceipts === "boolean") patch.readReceipts = body.readReceipts;
  if (typeof body.reduceMotion === "boolean") patch.reduceMotion = body.reduceMotion;
  if (typeof body.handle === "string") {
    const handle = body.handle.trim().toLowerCase().replace(/[^a-z0-9._]/g, "");
    if (handle.length >= 3) {
      const clash = await db
        .select()
        .from(users)
        .where(and(eq(users.handle, handle), ne(users.id, user.id)))
        .limit(1);
      if (clash.length) return NextResponse.json({ error: "Username is taken" }, { status: 409 });
      patch.handle = handle;
    }
  }

  if (Object.keys(patch).length) {
    await db.update(users).set(patch).where(eq(users.id, user.id));
  }
  const fresh = (await db.select().from(users).where(eq(users.id, user.id)).limit(1))[0];
  const blocked = await db.select().from(blocks).where(eq(blocks.userId, user.id));
  return NextResponse.json({
    user: { ...toPublic(fresh), email: fresh.email, themeId: fresh.themeId, readReceipts: fresh.readReceipts, reduceMotion: fresh.reduceMotion },
    blocked: blocked.map((b) => b.blockedId),
  });
}
