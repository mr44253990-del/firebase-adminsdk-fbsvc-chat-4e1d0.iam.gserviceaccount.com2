import { and, desc, eq, gt, inArray, lt, ne, notInArray, or } from "drizzle-orm";
import { db } from "@/db";
import {
  blocks,
  comments,
  conversations,
  follows,
  members,
  messages,
  notes,
  notifications,
  postViews,
  posts,
  reactions,
  stories,
  storyViews,
  users,
} from "@/db/schema";

const ONLINE_WINDOW = 3 * 60_000;

export type PublicUser = {
  id: string;
  handle: string;
  name: string;
  avatarUrl: string;
  accent: string;
  bio: string;
  location: string;
  verified: boolean;
  lastSeen: number;
  online: boolean;
  activityVisible: boolean;
};

export type CommentItem = {
  id: string;
  body: string;
  createdAt: number;
  author: PublicUser;
};

export type FeedPost = {
  id: string;
  body: string;
  mediaUrl: string | null;
  mediaKind: string | null;
  visibility: string;
  views: number;
  edited: boolean;
  createdAt: number;
  updatedAt: number;
  author: PublicUser;
  reactionCounts: Record<string, number>;
  reactionTotal: number;
  myReaction: string | null;
  commentCount: number;
  comments: CommentItem[];
};

export type StoryItem = {
  id: string;
  mediaUrl: string;
  mediaKind: string;
  caption: string;
  accent: string;
  createdAt: number;
  expiresAt: number;
  viewCount: number;
  myReaction: string;
  seen: boolean;
};

export type StoryGroup = { author: PublicUser; items: StoryItem[]; seen: boolean };

export type ChatSummary = {
  id: string;
  kind: string;
  title: string;
  coverUrl: string;
  accent: string;
  updatedAt: number;
  people: PublicUser[];
  lastMessage: { body: string; senderId: string; createdAt: number; mediaKind: string | null } | null;
  unread: number;
  role: string;
};

export type MessageItem = {
  id: string;
  conversationId: string;
  senderId: string;
  body: string;
  mediaUrl: string | null;
  mediaKind: string | null;
  replyToId: string | null;
  replyPreview: { name: string; body: string } | null;
  forwardedFrom: string | null;
  reaction: string;
  edited: boolean;
  deleted: boolean;
  createdAt: number;
};

export type NotificationItem = {
  id: string;
  kind: string;
  body: string;
  targetId: string;
  isRead: boolean;
  createdAt: number;
  actor: PublicUser | null;
};

export function toPublic(u: typeof users.$inferSelect): PublicUser {
  const online = u.activityVisible && Date.now() - u.lastSeen < ONLINE_WINDOW;
  return {
    id: u.id,
    handle: u.handle,
    name: u.name,
    avatarUrl: u.avatarUrl,
    accent: u.accent,
    bio: u.bio,
    location: u.location,
    verified: u.verified,
    lastSeen: u.activityVisible ? u.lastSeen : 0,
    online,
    activityVisible: u.activityVisible,
  };
}

async function userMap(ids: string[]): Promise<Map<string, PublicUser>> {
  const unique = Array.from(new Set(ids.filter(Boolean)));
  if (!unique.length) return new Map();
  const rows = await db.select().from(users).where(inArray(users.id, unique));
  return new Map(rows.map((r) => [r.id, toPublic(r)]));
}

export async function blockedIds(viewerId: string): Promise<string[]> {
  const rows = await db
    .select()
    .from(blocks)
    .where(or(eq(blocks.userId, viewerId), eq(blocks.blockedId, viewerId)));
  return rows.map((r) => (r.userId === viewerId ? r.blockedId : r.userId));
}

export async function getFeed(viewerId: string, limit = 30): Promise<FeedPost[]> {
  const hidden = await blockedIds(viewerId);
  const visibilityFilter = or(eq(posts.visibility, "public"), eq(posts.authorId, viewerId));
    const where = hidden.length
    ? and(visibilityFilter, notInArray(posts.authorId, hidden))
    : visibilityFilter;

  const rows = await db
    .select()
    .from(posts)
    .where(where)
    .orderBy(desc(posts.createdAt))
    .limit(limit);
  if (!rows.length) return [];

  const ids = rows.map((r) => r.id);
  const [reactionRows, commentRows] = await Promise.all([
    db.select().from(reactions).where(inArray(reactions.postId, ids)),
    db.select().from(comments).where(inArray(comments.postId, ids)).orderBy(comments.createdAt),
  ]);

  const authors = await userMap([
    ...rows.map((r) => r.authorId),
    ...commentRows.map((c) => c.userId),
  ]);

  const fallback: PublicUser = {
    id: "unknown",
    handle: "unknown",
    name: "Unknown",
    avatarUrl: "",
    accent: "aurora",
    bio: "",
    location: "",
    verified: false,
    lastSeen: 0,
    online: false,
    activityVisible: false,
  };

  return rows.map((p) => {
    const rs = reactionRows.filter((r) => r.postId === p.id);
    const counts: Record<string, number> = {};
    rs.forEach((r) => {
      counts[r.kind] = (counts[r.kind] ?? 0) + 1;
    });
    const cs = commentRows.filter((c) => c.postId === p.id);
    return {
      id: p.id,
      body: p.body,
      mediaUrl: p.mediaUrl,
      mediaKind: p.mediaKind,
      visibility: p.visibility,
      views: p.views,
      edited: p.edited,
      createdAt: p.createdAt,
      updatedAt: p.updatedAt,
      author: authors.get(p.authorId) ?? fallback,
      reactionCounts: counts,
      reactionTotal: rs.length,
      myReaction: rs.find((r) => r.userId === viewerId)?.kind ?? null,
      commentCount: cs.length,
      comments: cs.slice(-4).map((c) => ({
        id: c.id,
        body: c.body,
        createdAt: c.createdAt,
        author: authors.get(c.userId) ?? fallback,
      })),
    };
  });
}

export async function getStories(viewerId: string): Promise<StoryGroup[]> {
  const now = Date.now();
  await db.delete(stories).where(lt(stories.expiresAt, now));
  const hidden = await blockedIds(viewerId);
  const rows = await db
    .select()
    .from(stories)
    .where(gt(stories.expiresAt, now))
    .orderBy(desc(stories.createdAt));
  const visible = rows.filter((r) => !hidden.includes(r.authorId));
  if (!visible.length) return [];

  const views = await db
    .select()
    .from(storyViews)
    .where(inArray(storyViews.storyId, visible.map((r) => r.id)));
  const authors = await userMap(visible.map((r) => r.authorId));

  const grouped = new Map<string, StoryItem[]>();
  visible.forEach((s) => {
    const vs = views.filter((v) => v.storyId === s.id);
    const mine = vs.find((v) => v.userId === viewerId);
    const item: StoryItem = {
      id: s.id,
      mediaUrl: s.mediaUrl,
      mediaKind: s.mediaKind,
      caption: s.caption,
      accent: s.accent,
      createdAt: s.createdAt,
      expiresAt: s.expiresAt,
      viewCount: vs.length,
      myReaction: mine?.reaction ?? "",
      seen: !!mine,
    };
    const list = grouped.get(s.authorId) ?? [];
    list.push(item);
    grouped.set(s.authorId, list);
  });

  const groups: StoryGroup[] = [];
  grouped.forEach((items, authorId) => {
    const author = authors.get(authorId);
    if (!author) return;
    groups.push({
      author,
      items: items.sort((a, b) => a.createdAt - b.createdAt),
      seen: items.every((i) => i.seen),
    });
  });
  groups.sort((a, b) => {
    if (a.author.id === viewerId) return -1;
    if (b.author.id === viewerId) return 1;
    if (a.seen !== b.seen) return a.seen ? 1 : -1;
    return b.items[b.items.length - 1].createdAt - a.items[a.items.length - 1].createdAt;
  });
  return groups;
}

export async function getNotes(): Promise<{ author: PublicUser; body: string }[]> {
  const now = Date.now();
  await db.delete(notes).where(lt(notes.expiresAt, now));
  const rows = await db.select().from(notes).where(gt(notes.expiresAt, now));
  const authors = await userMap(rows.map((r) => r.userId));
  return rows
    .map((r) => ({ author: authors.get(r.userId)!, body: r.body }))
    .filter((r) => !!r.author);
}

export async function getPeople(viewerId: string): Promise<{
  people: PublicUser[];
  following: string[];
  blocked: string[];
}> {
  const rows = await db.select().from(users).where(ne(users.id, viewerId)).orderBy(desc(users.lastSeen));
  const [followRows, blockRows] = await Promise.all([
    db.select().from(follows).where(eq(follows.followerId, viewerId)),
    db.select().from(blocks).where(eq(blocks.userId, viewerId)),
  ]);
  return {
    people: rows.map(toPublic),
    following: followRows.map((f) => f.followingId),
    blocked: blockRows.map((b) => b.blockedId),
  };
}

export async function getChats(viewerId: string): Promise<ChatSummary[]> {
  const mine = await db.select().from(members).where(eq(members.userId, viewerId));
  if (!mine.length) return [];
  const ids = mine.map((m) => m.conversationId);
  const [convos, allMembers, lastMsgs] = await Promise.all([
    db.select().from(conversations).where(inArray(conversations.id, ids)),
    db.select().from(members).where(inArray(members.conversationId, ids)),
    db.select().from(messages).where(inArray(messages.conversationId, ids)).orderBy(messages.createdAt),
  ]);
  const people = await userMap(allMembers.map((m) => m.userId));

  return convos
    .map((c) => {
      const mem = mine.find((m) => m.conversationId === c.id)!;
      const others = allMembers
        .filter((m) => m.conversationId === c.id && (c.kind === "group" || m.userId !== viewerId))
        .map((m) => people.get(m.userId))
        .filter((u): u is PublicUser => !!u);
      const msgs = lastMsgs.filter((m) => m.conversationId === c.id && !m.deleted);
      const last = msgs[msgs.length - 1];
      const unread = msgs.filter((m) => m.senderId !== viewerId && m.createdAt > mem.lastReadAt).length;
      return {
        id: c.id,
        kind: c.kind,
        title: c.title,
        coverUrl: c.coverUrl,
        accent: c.accent,
        updatedAt: last?.createdAt ?? c.updatedAt,
        people: others,
        lastMessage: last
          ? { body: last.body, senderId: last.senderId, createdAt: last.createdAt, mediaKind: last.mediaKind }
          : null,
        unread,
        role: mem.role,
      };
    })
    .sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function getMessages(conversationId: string): Promise<MessageItem[]> {
  const rows = await db
    .select()
    .from(messages)
    .where(eq(messages.conversationId, conversationId))
    .orderBy(messages.createdAt);
  const senders = await userMap(rows.map((r) => r.senderId));
  const byId = new Map(rows.map((r) => [r.id, r]));
  return rows.map((m) => {
    const target = m.replyToId ? byId.get(m.replyToId) : undefined;
    return {
      id: m.id,
      conversationId: m.conversationId,
      senderId: m.senderId,
      body: m.body,
      mediaUrl: m.mediaUrl,
      mediaKind: m.mediaKind,
      replyToId: m.replyToId,
      replyPreview: target
        ? {
            name: senders.get(target.senderId)?.name ?? "Unknown",
            body: target.body || (target.mediaKind ? `[${target.mediaKind}]` : ""),
          }
        : null,
      forwardedFrom: m.forwardedFrom,
      reaction: m.reaction,
      edited: m.edited,
      deleted: m.deleted,
      createdAt: m.createdAt,
    };
  });
}

export async function getNotifications(viewerId: string): Promise<NotificationItem[]> {
  const rows = await db
    .select()
    .from(notifications)
    .where(eq(notifications.userId, viewerId))
    .orderBy(desc(notifications.createdAt))
    .limit(40);
  const actors = await userMap(rows.map((r) => r.actorId));
  return rows.map((n) => ({
    id: n.id,
    kind: n.kind,
    body: n.body,
    targetId: n.targetId,
    isRead: n.isRead,
    createdAt: n.createdAt,
    actor: actors.get(n.actorId) ?? null,
  }));
}

export async function getProfileStats(userId: string) {
  const [postRows, followerRows, followingRows] = await Promise.all([
    db.select().from(posts).where(eq(posts.authorId, userId)),
    db.select().from(follows).where(eq(follows.followingId, userId)),
    db.select().from(follows).where(eq(follows.followerId, userId)),
  ]);
  const ids = postRows.map((p) => p.id);
  const reactionRows = ids.length
    ? await db.select().from(reactions).where(inArray(reactions.postId, ids))
    : [];
  const viewRows = ids.length
    ? await db.select().from(postViews).where(inArray(postViews.postId, ids))
    : [];
  return {
    posts: postRows.length,
    followers: followerRows.length,
    following: followingRows.length,
    reactions: reactionRows.length,
    views: postRows.reduce((sum, p) => sum + p.views, 0) + viewRows.length,
  };
}

export async function pushNotification(
  userId: string,
  actorId: string,
  kind: string,
  targetId: string,
  body: string,
) {
  if (userId === actorId) return;
  await db.insert(notifications).values({
    id: `nt_${Math.random().toString(36).slice(2, 12)}`,
    userId,
    actorId,
    kind,
    targetId,
    body,
    isRead: false,
    createdAt: Date.now(),
  });
}
