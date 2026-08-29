import { NextResponse } from "next/server";
import { getSessionUser } from "@/lib/auth";
import { ensureSeed } from "@/lib/seed";
import { getFeed, getNotes, getStories } from "@/lib/queries";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  await ensureSeed();
  const user = await getSessionUser();
  if (!user) return NextResponse.json({ error: "Sign in first" }, { status: 401 });
  const url = new URL(request.url);
  const part = url.searchParams.get("part");

  if (part === "stories") return NextResponse.json({ stories: await getStories(user.id) });
  if (part === "posts") return NextResponse.json({ posts: await getFeed(user.id) });

  const [stories, posts, notes] = await Promise.all([
    getStories(user.id),
    getFeed(user.id),
    getNotes(),
  ]);
  return NextResponse.json({ stories, posts, notes });
}
