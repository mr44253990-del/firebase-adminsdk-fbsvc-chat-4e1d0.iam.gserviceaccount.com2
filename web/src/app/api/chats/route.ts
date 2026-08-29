import { NextResponse } from "next/server";
import { and, eq, inArray } from "drizzle-orm";
import { db } from "@/db";
import { conversations, media, members, messages } from "@/db/schema";
import { newId, requireUser } from "@/lib/auth";
import { ensureSeed } from "@/lib/seed";
import { getChats, getMessages, pushNotification } from "@/lib/queries";

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
  const chatId = url.searchParams.get("id");
  if (chatId) {
    const mine = await db
      .select()
      .from(members)
      .where(and(eq(members.conversationId, chatId), eq(members.userId, user.id)))
      .limit(1);
    if (!mine.length) return NextResponse.json({ error: "Not a member" }, { status: 403 });
    return NextResponse.json({ messages: await getMessages(chatId) });
  }
  return NextResponse.json({ chats: await getChats(user.id) });
}

export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const action = (body.action as string) ?? "";
  const now = Date.now();
  const str = (key: string) => (typeof body[key] === "string" ? (body[key] as string) : "");

  if (action === "open-dm") {
    const otherId = str("targetId");
    if (!otherId || otherId === user.id) return NextResponse.json({ error: "Bad target" }, { status: 400 });
    const mine = await db.select().from(members).where(eq(members.userId, user.id));
    const theirs = await db.select().from(members).where(eq(members.userId, otherId));
    const sharedIds = mine.filter((m) => theirs.some((t) => t.conversationId === m.conversationId));
    if (sharedIds.length) {
      const dms = await db
        .select()
        .from(conversations)
        .where(
          and(
            inArray(conversations.id, sharedIds.map((s) => s.conversationId)),
            eq(conversations.kind, "dm"),
          ),
        )
        .limit(1);
      if (dms[0]) return NextResponse.json({ ok: true, id: dms[0].id });
    }
    const id = newId("cv");
    await db.insert(conversations).values({
      id,
      kind: "dm",
      title: "",
      coverUrl: "",
      accent: user.accent,
      createdBy: user.id,
      createdAt: now,
      updatedAt: now,
    });
    await db.insert(members).values([
      { id: newId("m"), conversationId: id, userId: user.id, role: "member", lastReadAt: now, joinedAt: now },
      { id: newId("m"), conversationId: id, userId: otherId, role: "member", lastReadAt: 0, joinedAt: now },
    ]);
    return NextResponse.json({ ok: true, id });
  }

  if (action === "create-group") {
    const title = str("title").trim() || "New group";
    const ids = Array.isArray(body.memberIds) ? (body.memberIds as string[]) : [];
    const id = newId("cv");
    await db.insert(conversations).values({
      id,
      kind: "group",
      title,
      coverUrl: str("coverUrl"),
      accent: str("accent") || user.accent,
      createdBy: user.id,
      createdAt: now,
      updatedAt: now,
    });
    const unique = Array.from(new Set([user.id, ...ids]));
    await db.insert(members).values(
      unique.map((uid) => ({
        id: newId("m"),
        conversationId: id,
        userId: uid,
        role: uid === user.id ? "owner" : "member",
        lastReadAt: uid === user.id ? now : 0,
        joinedAt: now,
      })),
    );
    return NextResponse.json({ ok: true, id });
  }

  const chatId = str("chatId");
  if (action === "send" || action === "read" || action === "update-group" || action === "leave") {
    const mine = await db
      .select()
      .from(members)
      .where(and(eq(members.conversationId, chatId), eq(members.userId, user.id)))
      .limit(1);
    if (!mine.length) return NextResponse.json({ error: "Not a member" }, { status: 403 });

    if (action === "read") {
      await db.update(members).set({ lastReadAt: now }).where(eq(members.id, mine[0].id));
      return NextResponse.json({ ok: true });
    }

    if (action === "leave") {
      await db.delete(members).where(eq(members.id, mine[0].id));
      return NextResponse.json({ ok: true });
    }

    if (action === "update-group") {
      await db
        .update(conversations)
        .set({
          title: str("title") || undefined,
          coverUrl: typeof body.coverUrl === "string" ? (body.coverUrl as string) : undefined,
          accent: str("accent") || undefined,
          updatedAt: now,
        })
        .where(eq(conversations.id, chatId));
      const add = Array.isArray(body.addMemberIds) ? (body.addMemberIds as string[]) : [];
      if (add.length) {
        const existing = await db.select().from(members).where(eq(members.conversationId, chatId));
        const fresh = add.filter((id) => !existing.some((e) => e.userId === id));
        if (fresh.length) {
          await db.insert(members).values(
            fresh.map((uid) => ({
              id: newId("m"),
              conversationId: chatId,
              userId: uid,
              role: "member",
              lastReadAt: 0,
              joinedAt: now,
            })),
          );
        }
      }
      return NextResponse.json({ ok: true });
    }

    const text = str("body").trim();
    const mediaUrl = str("mediaUrl");
    if (!text && !mediaUrl) return NextResponse.json({ error: "Empty message" }, { status: 400 });
    const id = newId("mg");
    await db.insert(messages).values({
      id,
      conversationId: chatId,
      senderId: user.id,
      body: text,
      mediaUrl: mediaUrl || null,
      mediaKind: str("mediaKind") || null,
      replyToId: str("replyToId") || null,
      forwardedFrom: str("forwardedFrom") || null,
      reaction: "",
      edited: false,
      deleted: false,
      createdAt: now,
    });
    await db.update(conversations).set({ updatedAt: now }).where(eq(conversations.id, chatId));
    await db.update(members).set({ lastReadAt: now }).where(eq(members.id, mine[0].id));

    const convo = (await db.select().from(conversations).where(eq(conversations.id, chatId)).limit(1))[0];
    // Group chats intentionally do not raise notifications — only 1:1 threads do.
    if (convo?.kind === "dm") {
      const others = await db.select().from(members).where(eq(members.conversationId, chatId));
      for (const m of others) {
        if (m.userId !== user.id) {
          await pushNotification(m.userId, user.id, "message", chatId, "sent you a message");
        }
      }
    }
    return NextResponse.json({ ok: true, id });
  }

  const messageId = str("messageId");
  const rows = await db.select().from(messages).where(eq(messages.id, messageId)).limit(1);
  const msg = rows[0];
  if (!msg) return NextResponse.json({ error: "Message not found" }, { status: 404 });

  if (action === "react-message") {
    const reaction = str("reaction");
    await db
      .update(messages)
      .set({ reaction: msg.reaction === reaction ? "" : reaction })
      .where(eq(messages.id, messageId));
    return NextResponse.json({ ok: true });
  }

  if (msg.senderId !== user.id) return NextResponse.json({ error: "Not your message" }, { status: 403 });

  if (action === "edit-message") {
    await db.update(messages).set({ body: str("body"), edited: true }).where(eq(messages.id, messageId));
    return NextResponse.json({ ok: true });
  }

  if (action === "delete-message") {
    if (msg.mediaUrl?.startsWith("/api/media/")) {
      await db.delete(media).where(eq(media.id, msg.mediaUrl.slice("/api/media/".length)));
    }
    await db
      .update(messages)
      .set({ deleted: true, body: "", mediaUrl: null, mediaKind: null })
      .where(eq(messages.id, messageId));
    return NextResponse.json({ ok: true });
  }

  if (action === "forward-message") {
    const targetChat = str("targetChatId");
    const mine = await db
      .select()
      .from(members)
      .where(and(eq(members.conversationId, targetChat), eq(members.userId, user.id)))
      .limit(1);
    if (!mine.length) return NextResponse.json({ error: "Not a member" }, { status: 403 });
    await db.insert(messages).values({
      id: newId("mg"),
      conversationId: targetChat,
      senderId: user.id,
      body: msg.body,
      mediaUrl: msg.mediaUrl,
      mediaKind: msg.mediaKind,
      replyToId: null,
      forwardedFrom: msg.senderId,
      reaction: "",
      edited: false,
      deleted: false,
      createdAt: now,
    });
    await db.update(conversations).set({ updatedAt: now }).where(eq(conversations.id, targetChat));
    return NextResponse.json({ ok: true });
  }

  return NextResponse.json({ error: "Unknown action" }, { status: 400 });
}
