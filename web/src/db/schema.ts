import {
  pgTable,
  text,
  integer,
  boolean,
  bigint,
  timestamp,
  uniqueIndex,
  index,
} from "drizzle-orm/pg-core";

/** Binary store. Mirrors the Supabase Storage bucket layout used by the Android client. */
export const media = pgTable("media", {
  id: text("id").primaryKey(),
  ownerId: text("owner_id").notNull().default(""),
  bucket: text("bucket").notNull().default("posts"),
  path: text("path").notNull().default(""),
  mimeType: text("mime_type").notNull().default("image/jpeg"),
  kind: text("kind").notNull().default("image"),
  remoteUrl: text("remote_url"),
  data: text("data"),
  sizeBytes: integer("size_bytes").notNull().default(0),
  createdAt: bigint("created_at", { mode: "number" }).notNull(),
});

export const users = pgTable(
  "users",
  {
    id: text("id").primaryKey(),
    handle: text("handle").notNull(),
    name: text("name").notNull(),
    email: text("email").notNull(),
    passwordHash: text("password_hash").notNull(),
    bio: text("bio").notNull().default(""),
    location: text("location").notNull().default(""),
    avatarUrl: text("avatar_url").notNull().default(""),
    accent: text("accent").notNull().default("aurora"),
    themeId: text("theme_id").notNull().default("liquid-aurora"),
    activityVisible: boolean("activity_visible").notNull().default(true),
    readReceipts: boolean("read_receipts").notNull().default(true),
    reduceMotion: boolean("reduce_motion").notNull().default(false),
    verified: boolean("verified").notNull().default(false),
    lastSeen: bigint("last_seen", { mode: "number" }).notNull().default(0),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [
    uniqueIndex("users_handle_idx").on(t.handle),
    uniqueIndex("users_email_idx").on(t.email),
  ],
);

export const sessions = pgTable("sessions", {
  token: text("token").primaryKey(),
  userId: text("user_id").notNull(),
  createdAt: bigint("created_at", { mode: "number" }).notNull(),
});

export const posts = pgTable(
  "posts",
  {
    id: text("id").primaryKey(),
    authorId: text("author_id").notNull(),
    body: text("body").notNull().default(""),
    mediaUrl: text("media_url"),
    mediaKind: text("media_kind"),
    visibility: text("visibility").notNull().default("public"),
    views: integer("views").notNull().default(0),
    edited: boolean("edited").notNull().default(false),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
    updatedAt: bigint("updated_at", { mode: "number" }).notNull(),
  },
  (t) => [index("posts_created_idx").on(t.createdAt)],
);

export const reactions = pgTable(
  "reactions",
  {
    id: text("id").primaryKey(),
    postId: text("post_id").notNull(),
    userId: text("user_id").notNull(),
    kind: text("kind").notNull().default("like"),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("reactions_unique_idx").on(t.postId, t.userId)],
);

export const comments = pgTable(
  "comments",
  {
    id: text("id").primaryKey(),
    postId: text("post_id").notNull(),
    userId: text("user_id").notNull(),
    body: text("body").notNull(),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [index("comments_post_idx").on(t.postId)],
);

export const postViews = pgTable(
  "post_views",
  {
    id: text("id").primaryKey(),
    postId: text("post_id").notNull(),
    userId: text("user_id").notNull(),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("post_views_unique_idx").on(t.postId, t.userId)],
);

/** Stories self-destruct 12 hours after publish (or instantly when the owner deletes). */
export const stories = pgTable("stories", {
  id: text("id").primaryKey(),
  authorId: text("author_id").notNull(),
  mediaUrl: text("media_url").notNull(),
  mediaKind: text("media_kind").notNull().default("image"),
  caption: text("caption").notNull().default(""),
  accent: text("accent").notNull().default("violet"),
  createdAt: bigint("created_at", { mode: "number" }).notNull(),
  expiresAt: bigint("expires_at", { mode: "number" }).notNull(),
});

export const storyViews = pgTable(
  "story_views",
  {
    id: text("id").primaryKey(),
    storyId: text("story_id").notNull(),
    userId: text("user_id").notNull(),
    reaction: text("reaction").notNull().default(""),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("story_views_unique_idx").on(t.storyId, t.userId)],
);

export const conversations = pgTable("conversations", {
  id: text("id").primaryKey(),
  kind: text("kind").notNull().default("dm"),
  title: text("title").notNull().default(""),
  coverUrl: text("cover_url").notNull().default(""),
  accent: text("accent").notNull().default("violet"),
  createdBy: text("created_by").notNull().default(""),
  createdAt: bigint("created_at", { mode: "number" }).notNull(),
  updatedAt: bigint("updated_at", { mode: "number" }).notNull(),
});

export const members = pgTable(
  "members",
  {
    id: text("id").primaryKey(),
    conversationId: text("conversation_id").notNull(),
    userId: text("user_id").notNull(),
    role: text("role").notNull().default("member"),
    lastReadAt: bigint("last_read_at", { mode: "number" }).notNull().default(0),
    muted: boolean("muted").notNull().default(false),
    joinedAt: bigint("joined_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("members_unique_idx").on(t.conversationId, t.userId)],
);

export const messages = pgTable(
  "messages",
  {
    id: text("id").primaryKey(),
    conversationId: text("conversation_id").notNull(),
    senderId: text("sender_id").notNull(),
    body: text("body").notNull().default(""),
    mediaUrl: text("media_url"),
    mediaKind: text("media_kind"),
    replyToId: text("reply_to_id"),
    forwardedFrom: text("forwarded_from"),
    reaction: text("reaction").notNull().default(""),
    edited: boolean("edited").notNull().default(false),
    deleted: boolean("deleted").notNull().default(false),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [index("messages_convo_idx").on(t.conversationId, t.createdAt)],
);

export const notifications = pgTable(
  "notifications",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull(),
    actorId: text("actor_id").notNull().default(""),
    kind: text("kind").notNull(),
    targetId: text("target_id").notNull().default(""),
    body: text("body").notNull().default(""),
    isRead: boolean("is_read").notNull().default(false),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [index("notifications_user_idx").on(t.userId, t.createdAt)],
);

export const blocks = pgTable(
  "blocks",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull(),
    blockedId: text("blocked_id").notNull(),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("blocks_unique_idx").on(t.userId, t.blockedId)],
);

export const follows = pgTable(
  "follows",
  {
    id: text("id").primaryKey(),
    followerId: text("follower_id").notNull(),
    followingId: text("following_id").notNull(),
    createdAt: bigint("created_at", { mode: "number" }).notNull(),
  },
  (t) => [uniqueIndex("follows_unique_idx").on(t.followerId, t.followingId)],
);

export const notes = pgTable("notes", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull(),
  body: text("body").notNull(),
  createdAt: bigint("created_at", { mode: "number" }).notNull(),
  expiresAt: bigint("expires_at", { mode: "number" }).notNull(),
});

export const seedState = pgTable("seed_state", {
  id: text("id").primaryKey(),
  version: integer("version").notNull().default(0),
  createdAt: timestamp("created_at").defaultNow(),
});
