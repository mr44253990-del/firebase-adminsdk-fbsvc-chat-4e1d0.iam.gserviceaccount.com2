import { NextResponse } from "next/server";
import { and, eq, gt } from "drizzle-orm";
import { db } from "@/db";
import { members, messages, notifications, users } from "@/db/schema";
import { getSessionUser, touchPresence } from "@/lib/auth";
import { ensureSeed } from "@/lib/seed";
import { toPublic } from "@/lib/queries";

export const dynamic = "force-dynamic";

export async function GET() {
  await ensureSeed();
  const user = await getSessionUser();
  if (!user) return NextResponse.json({ user: null });
  await touchPresence(user.id);

  const mine = await db.select().from(members).where(eq(members.userId, user.id));
  let unreadChats = 0;
  let unreadMessages = 0;
  for (const m of mine) {
    const rows = await db
      .select({ id: messages.id })
      .from(messages)
      .where(
        and(
          eq(messages.conversationId, m.conversationId),
          gt(messages.createdAt, m.lastReadAt),
          eq(messages.deleted, false),
        ),
      );
    const count = rows.length;
    if (count > 0) {
      unreadChats += 1;
      unreadMessages += count;
    }
  }
  const notes = await db
    .select({ id: notifications.id })
    .from(notifications)
    .where(and(eq(notifications.userId, user.id), eq(notifications.isRead, false)));

  return NextResponse.json({
    user: {
      ...toPublic(user),
      email: user.email,
      themeId: user.themeId,
      readReceipts: user.readReceipts,
      reduceMotion: user.reduceMotion,
    },
    badges: { chats: unreadChats, messages: unreadMessages, notifications: notes.length },
  });
}

export async function POST() {
  const user = await getSessionUser();
  if (!user) return NextResponse.json({ ok: false }, { status: 401 });
  await db.update(users).set({ lastSeen: Date.now() }).where(eq(users.id, user.id));
  return NextResponse.json({ ok: true });
}
