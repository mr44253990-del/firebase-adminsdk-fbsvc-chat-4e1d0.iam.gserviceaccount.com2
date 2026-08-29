import { NextResponse } from "next/server";
import { eq, or } from "drizzle-orm";
import { db } from "@/db";
import { follows, users } from "@/db/schema";
import { createSession, destroySession, hashPassword, newId, verifyPassword } from "@/lib/auth";
import { ensureSeed } from "@/lib/seed";

export async function POST(request: Request) {
  await ensureSeed();
  const payload = (await request.json().catch(() => ({}))) as Record<string, string>;
  const action = payload.action ?? "login";

  if (action === "logout") {
    await destroySession();
    return NextResponse.json({ ok: true });
  }

  if (action === "signup") {
    const name = (payload.name ?? "").trim();
    const handleRaw = (payload.handle ?? "").trim().toLowerCase().replace(/[^a-z0-9._]/g, "");
    const email = (payload.email ?? "").trim().toLowerCase();
    const password = payload.password ?? "";
    if (name.length < 2) return NextResponse.json({ error: "Name is too short" }, { status: 400 });
    if (handleRaw.length < 3) return NextResponse.json({ error: "Username needs 3+ characters" }, { status: 400 });
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return NextResponse.json({ error: "Enter a valid email" }, { status: 400 });
    if (password.length < 6) return NextResponse.json({ error: "Password needs 6+ characters" }, { status: 400 });

    const clash = await db
      .select()
      .from(users)
      .where(or(eq(users.handle, handleRaw), eq(users.email, email)))
      .limit(1);
    if (clash.length) return NextResponse.json({ error: "Username or email is already taken" }, { status: 409 });

    const id = newId("u");
    const now = Date.now();
    await db.insert(users).values({
      id,
      handle: handleRaw,
      name,
      email,
      passwordHash: hashPassword(password),
      bio: payload.bio ?? "",
      location: payload.location ?? "",
      avatarUrl: payload.avatarUrl ?? "",
      accent: payload.accent ?? "aurora",
      themeId: payload.themeId ?? "liquid-aurora",
      lastSeen: now,
      createdAt: now,
    });

    const everyone = await db.select({ id: users.id }).from(users);
    const rows = everyone
      .filter((u) => u.id !== id)
      .flatMap((u) => [
        { id: newId("f"), followerId: id, followingId: u.id, createdAt: now },
        { id: newId("f"), followerId: u.id, followingId: id, createdAt: now },
      ]);
    if (rows.length) await db.insert(follows).values(rows);

    await createSession(id);
    return NextResponse.json({ ok: true, id });
  }

  const identifier = (payload.identifier ?? "").trim().toLowerCase();
  const password = payload.password ?? "";
  const found = await db
    .select()
    .from(users)
    .where(or(eq(users.email, identifier), eq(users.handle, identifier)))
    .limit(1);
  const user = found[0];
  if (!user || !verifyPassword(password, user.passwordHash)) {
    return NextResponse.json({ error: "Wrong credentials. Try again." }, { status: 401 });
  }
  await db.update(users).set({ lastSeen: Date.now() }).where(eq(users.id, user.id));
  await createSession(user.id);
  return NextResponse.json({ ok: true, id: user.id });
}
