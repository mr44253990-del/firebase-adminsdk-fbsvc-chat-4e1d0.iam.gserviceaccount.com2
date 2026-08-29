import { NextResponse } from "next/server";
import { sql } from "drizzle-orm";
import { db } from "@/db";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    await db.execute(sql`select 1`);
    return NextResponse.json({ status: "ok", db: "up", app: "convo-nexus" });
  } catch {
    return NextResponse.json({ status: "degraded", db: "down", app: "convo-nexus" }, { status: 200 });
  }
}
