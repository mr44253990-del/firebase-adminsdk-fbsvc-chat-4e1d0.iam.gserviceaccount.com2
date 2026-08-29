import { NextResponse } from "next/server";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { notifications } from "@/db/schema";
import { requireUser } from "@/lib/auth";
import { getNotifications } from "@/lib/queries";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const user = await requireUser();
    return NextResponse.json({ items: await getNotifications(user.id) });
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
}

export async function POST(request: Request) {
  let user;
  try {
    user = await requireUser();
  } catch {
    return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  }
  const body = (await request.json().catch(() => ({}))) as Record<string, string>;
  if (body.action === "clear") {
    await db.delete(notifications).where(eq(notifications.userId, user.id));
    return NextResponse.json({ ok: true });
  }
  await db.update(notifications).set({ isRead: true }).where(eq(notifications.userId, user.id));
  return NextResponse.json({ ok: true });
}
