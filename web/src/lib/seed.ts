import { sql } from "drizzle-orm";
import { db } from "@/db";
import {
  comments,
  conversations,
  follows,
  members,
  messages,
  notifications,
  notes,
  postViews,
  posts,
  reactions,
  seedState,
  stories,
  storyViews,
  users,
} from "@/db/schema";
import { hashPassword } from "@/lib/auth";

const SEED_VERSION = 4;
const MIN = 60_000;
const HOUR = 3_600_000;

const P = (id: number) =>
  `https://images.pexels.com/photos/${id}/pexels-photo-${id}.jpeg?auto=compress&cs=tinysrgb&fit=crop&h=900&w=900`;
const L = (id: number) =>
  `https://images.pexels.com/photos/${id}/pexels-photo-${id}.jpeg?auto=compress&cs=tinysrgb&fit=crop&h=760&w=1200`;

type SeedUser = {
  id: string;
  handle: string;
  name: string;
  bio: string;
  location: string;
  avatarUrl: string;
  accent: string;
  themeId: string;
  minutesAgo: number;
  verified?: boolean;
};

const SEED_USERS: SeedUser[] = [
  {
    id: "u_rakib",
    handle: "rakibul",
    name: "Rakibul Islam",
    bio: "Android engineer. Building Convo Nexus. Kapilmuni College.",
    location: "Kapilmuni, Khulna",
    avatarUrl: P(31420959),
    accent: "aurora",
    themeId: "liquid-aurora",
    minutesAgo: 0,
    verified: true,
  },
  {
    id: "u_nadia",
    handle: "nadia.h",
    name: "Nadia Hasan",
    bio: "Product designer · motion nerd · tea addict",
    location: "Dhaka",
    avatarUrl: P(34014938),
    accent: "candy",
    themeId: "glass-rose",
    minutesAgo: 2,
  },
  {
    id: "u_arif",
    handle: "arif.dev",
    name: "Arif Chowdhury",
    bio: "Backend + WebRTC. I make calls not drop.",
    location: "Chattogram",
    avatarUrl: P(30144736),
    accent: "mint",
    themeId: "glass-ocean",
    minutesAgo: 9,
  },
  {
    id: "u_samira",
    handle: "samira",
    name: "Samira Rahman",
    bio: "Photography, night streets, and long walks.",
    location: "Sylhet",
    avatarUrl: P(17080001),
    accent: "ice",
    themeId: "midnight-orchid",
    minutesAgo: 44,
  },
  {
    id: "u_tanvir",
    handle: "tanvir",
    name: "Tanvir Ahmed",
    bio: "Guitar, lo-fi beats, and Kotlin coroutines.",
    location: "Rajshahi",
    avatarUrl: P(33418887),
    accent: "gold",
    themeId: "royal-gold",
    minutesAgo: 130,
  },
  {
    id: "u_farhana",
    handle: "farhana",
    name: "Farhana Akter",
    bio: "CS student. Loves clean typography.",
    location: "Khulna",
    avatarUrl: P(28397702),
    accent: "violet",
    themeId: "obsidian-neon",
    minutesAgo: 6,
  },
  {
    id: "u_imran",
    handle: "imran",
    name: "Imran Kabir",
    bio: "Studio engineer. Mixing at 2am.",
    location: "Dhaka",
    avatarUrl: P(18197120),
    accent: "sunset",
    themeId: "cyber-lime",
    minutesAgo: 320,
  },
  {
    id: "u_priya",
    handle: "priya",
    name: "Priya Das",
    bio: "Traveller. 14 districts down, 50 to go.",
    location: "Barishal",
    avatarUrl: P(30089260),
    accent: "lime",
    themeId: "forest-glass",
    minutesAgo: 1,
  },
];

export async function ensureSeed(): Promise<void> {
  const existing = await db.select().from(seedState).limit(1);
  if (existing[0]?.version === SEED_VERSION) return;

  await db.execute(sql`
    TRUNCATE TABLE users, sessions, posts, reactions, comments, post_views, stories,
      story_views, conversations, members, messages, notifications, blocks, follows,
      notes, media, seed_state
  `);

  const now = Date.now();
  const pw = hashPassword("nexus1234");

  await db.insert(users).values(
    SEED_USERS.map((u) => ({
      id: u.id,
      handle: u.handle,
      name: u.name,
      email: `${u.handle.replace(/[^a-z0-9]/g, "")}@nexus.app`,
      passwordHash: pw,
      bio: u.bio,
      location: u.location,
      avatarUrl: u.avatarUrl,
      accent: u.accent,
      themeId: u.themeId,
      activityVisible: true,
      readReceipts: true,
      reduceMotion: false,
      verified: !!u.verified,
      lastSeen: now - u.minutesAgo * MIN,
      createdAt: now - 90 * 24 * HOUR,
    })),
  );

  // Everyone follows everyone else — keeps the demo feed full.
  const followRows = [];
  for (const a of SEED_USERS) {
    for (const b of SEED_USERS) {
      if (a.id === b.id) continue;
      followRows.push({
        id: `f_${a.id}_${b.id}`,
        followerId: a.id,
        followingId: b.id,
        createdAt: now - 30 * 24 * HOUR,
      });
    }
  }
  await db.insert(follows).values(followRows);

  const seedPosts = [
    {
      id: "p_1",
      authorId: "u_nadia",
      body:
        "Spent the night re-drawing the glass system for Convo Nexus. Every panel now bends the light behind it instead of faking a blur. Swipe through and tell me which corner radius feels right.",
      mediaUrl: L(2337920),
      mediaKind: "image",
      visibility: "public",
      views: 1284,
      minutesAgo: 12,
    },
    {
      id: "p_2",
      authorId: "u_rakib",
      body:
        "Shipped it. Stories, feed, groups, voice notes and WebRTC calling in one build — and it still holds 60fps on a 4-year-old phone. The trick was moving every animation into the draw phase.",
      mediaUrl: L(1628043),
      mediaKind: "image",
      visibility: "public",
      views: 3410,
      minutesAgo: 38,
    },
    {
      id: "p_3",
      authorId: "u_samira",
      body: "Rain, neon and an empty street. My favourite combination.",
      mediaUrl: L(18995427),
      mediaKind: "image",
      visibility: "public",
      views: 762,
      minutesAgo: 95,
    },
    {
      id: "p_4",
      authorId: "u_arif",
      body:
        "Presence probe rewritten: the socket now reports online/offline from the network layer, so you show as online even when the app is in the background. Last-seen accuracy is down to 4 seconds.",
      mediaUrl: null,
      mediaKind: null,
      visibility: "public",
      views: 512,
      minutesAgo: 180,
    },
    {
      id: "p_5",
      authorId: "u_priya",
      body: "District 14. The food alone was worth the 9 hour bus ride.",
      mediaUrl: L(6038297),
      mediaKind: "image",
      visibility: "public",
      views: 2098,
      minutesAgo: 300,
    },
    {
      id: "p_6",
      authorId: "u_tanvir",
      body: "New loop. Headphones on, volume up. Bass is doing something illegal at 0:42.",
      mediaUrl: L(18976506),
      mediaKind: "image",
      visibility: "public",
      views: 940,
      minutesAgo: 420,
    },
    {
      id: "p_7",
      authorId: "u_farhana",
      body: "Private note to self: finish the compiler assignment before Thursday. No excuses.",
      mediaUrl: null,
      mediaKind: null,
      visibility: "private",
      views: 3,
      minutesAgo: 60,
    },
    {
      id: "p_8",
      authorId: "u_imran",
      body: "Studio night. Twelve takes and take one was the best one, as always.",
      mediaUrl: L(29084942),
      mediaKind: "image",
      visibility: "public",
      views: 1560,
      minutesAgo: 700,
    },
  ];

  await db.insert(posts).values(
    seedPosts.map((p) => ({
      id: p.id,
      authorId: p.authorId,
      body: p.body,
      mediaUrl: p.mediaUrl,
      mediaKind: p.mediaKind,
      visibility: p.visibility,
      views: p.views,
      edited: false,
      createdAt: now - p.minutesAgo * MIN,
      updatedAt: now - p.minutesAgo * MIN,
    })),
  );

  const kinds = ["like", "love", "fire", "wow", "haha"];
  const reactionRows: (typeof reactions.$inferInsert)[] = [];
  const viewRows: (typeof postViews.$inferInsert)[] = [];
  seedPosts.forEach((p, pi) => {
    if (p.visibility === "private") return;
    SEED_USERS.forEach((u, ui) => {
      if (u.id === p.authorId) return;
      if ((pi + ui) % 3 === 0) {
        reactionRows.push({
          id: `r_${p.id}_${u.id}`,
          postId: p.id,
          userId: u.id,
          kind: kinds[(pi + ui) % kinds.length],
          createdAt: now - (p.minutesAgo - 2) * MIN,
        });
      }
      viewRows.push({
        id: `v_${p.id}_${u.id}`,
        postId: p.id,
        userId: u.id,
        createdAt: now - (p.minutesAgo - 1) * MIN,
      });
    });
  });
  await db.insert(reactions).values(reactionRows);
  await db.insert(postViews).values(viewRows);

  await db.insert(comments).values([
    { id: "c_1", postId: "p_1", userId: "u_rakib", body: "28dp. Trust me, I measured it on six devices.", createdAt: now - 10 * MIN },
    { id: "c_2", postId: "p_1", userId: "u_farhana", body: "The specular edge on the top border is the detail that sells it.", createdAt: now - 8 * MIN },
    { id: "c_3", postId: "p_2", userId: "u_arif", body: "The draw-phase trick is criminally underrated.", createdAt: now - 30 * MIN },
    { id: "c_4", postId: "p_2", userId: "u_priya", body: "Installed. It feels like butter.", createdAt: now - 22 * MIN },
    { id: "c_5", postId: "p_3", userId: "u_nadia", body: "This belongs on a poster.", createdAt: now - 80 * MIN },
    { id: "c_6", postId: "p_5", userId: "u_samira", body: "Sending me the itinerary, right?", createdAt: now - 240 * MIN },
  ]);

  const storyRows = [
    { id: "s_1", authorId: "u_nadia", mediaUrl: L(2337920), caption: "Late night pixels", accent: "candy", minutesAgo: 22 },
    { id: "s_2", authorId: "u_priya", mediaUrl: L(6038297), caption: "Street food run", accent: "lime", minutesAgo: 55 },
    { id: "s_3", authorId: "u_samira", mediaUrl: L(18995427), caption: "Rain on glass", accent: "ice", minutesAgo: 90 },
    { id: "s_4", authorId: "u_tanvir", mediaUrl: L(18976506), caption: "Soundcheck", accent: "gold", minutesAgo: 140 },
    { id: "s_5", authorId: "u_rakib", mediaUrl: L(1628043), caption: "Build 4.2.1 is live", accent: "aurora", minutesAgo: 5 },
    { id: "s_6", authorId: "u_imran", mediaUrl: L(29084942), caption: "Mixing till sunrise", accent: "sunset", minutesAgo: 200 },
  ];
  await db.insert(stories).values(
    storyRows.map((s) => ({
      id: s.id,
      authorId: s.authorId,
      mediaUrl: s.mediaUrl,
      mediaKind: "image",
      caption: s.caption,
      accent: s.accent,
      createdAt: now - s.minutesAgo * MIN,
      expiresAt: now - s.minutesAgo * MIN + 12 * HOUR,
    })),
  );
  await db.insert(storyViews).values([
    { id: "sv_1", storyId: "s_1", userId: "u_arif", reaction: "fire", createdAt: now - 18 * MIN },
    { id: "sv_2", storyId: "s_1", userId: "u_priya", reaction: "love", createdAt: now - 16 * MIN },
    { id: "sv_3", storyId: "s_5", userId: "u_nadia", reaction: "wow", createdAt: now - 3 * MIN },
  ]);

  await db.insert(notes).values([
    { id: "n_1", userId: "u_farhana", body: "Ask me about type scales", createdAt: now - 20 * MIN, expiresAt: now + 20 * HOUR },
    { id: "n_2", userId: "u_arif", body: "On call duty tonight", createdAt: now - 60 * MIN, expiresAt: now + 18 * HOUR },
    { id: "n_3", userId: "u_priya", body: "Back in Dhaka Friday", createdAt: now - 200 * MIN, expiresAt: now + 10 * HOUR },
  ]);

  // Conversations: DMs for Rakibul + one design group.
  const dm = (id: string, other: string, updatedMinutes: number) => ({
    id,
    kind: "dm",
    title: "",
    coverUrl: "",
    accent: "violet",
    createdBy: "u_rakib",
    createdAt: now - 20 * 24 * HOUR,
    updatedAt: now - updatedMinutes * MIN,
  });

  await db.insert(conversations).values([
    dm("cv_nadia", "u_nadia", 3),
    dm("cv_arif", "u_arif", 26),
    dm("cv_samira", "u_samira", 210),
    dm("cv_priya", "u_priya", 640),
    {
      id: "cv_group_design",
      kind: "group",
      title: "Nexus Design Lab",
      coverUrl: L(16807908),
      accent: "aurora",
      createdBy: "u_rakib",
      createdAt: now - 40 * 24 * HOUR,
      updatedAt: now - 11 * MIN,
    },
    {
      id: "cv_group_night",
      kind: "group",
      title: "Night Shift Devs",
      coverUrl: L(705805),
      accent: "mint",
      createdBy: "u_arif",
      createdAt: now - 22 * 24 * HOUR,
      updatedAt: now - 95 * MIN,
    },
  ]);

  const memberRows: (typeof members.$inferInsert)[] = [];
  const addMember = (cid: string, uid: string, role: string, lastReadAt: number) =>
    memberRows.push({
      id: `m_${cid}_${uid}`,
      conversationId: cid,
      userId: uid,
      role,
      lastReadAt,
      muted: false,
      joinedAt: now - 20 * 24 * HOUR,
    });

  addMember("cv_nadia", "u_rakib", "member", now - 40 * MIN);
  addMember("cv_nadia", "u_nadia", "member", now);
  addMember("cv_arif", "u_rakib", "member", now - 60 * MIN);
  addMember("cv_arif", "u_arif", "member", now);
  addMember("cv_samira", "u_rakib", "member", now);
  addMember("cv_samira", "u_samira", "member", now);
  addMember("cv_priya", "u_rakib", "member", now);
  addMember("cv_priya", "u_priya", "member", now);
  ["u_rakib", "u_nadia", "u_farhana", "u_tanvir"].forEach((u) =>
    addMember("cv_group_design", u, u === "u_rakib" ? "owner" : "member", u === "u_rakib" ? now - 30 * MIN : now),
  );
  ["u_arif", "u_rakib", "u_imran", "u_priya"].forEach((u) =>
    addMember("cv_group_night", u, u === "u_arif" ? "owner" : "member", now),
  );
  await db.insert(members).values(memberRows);

  const msg = (
    id: string,
    cid: string,
    sender: string,
    body: string,
    minutesAgo: number,
    extra: Partial<typeof messages.$inferInsert> = {},
  ): typeof messages.$inferInsert => ({
    id,
    conversationId: cid,
    senderId: sender,
    body,
    mediaUrl: null,
    mediaKind: null,
    replyToId: null,
    forwardedFrom: null,
    reaction: "",
    edited: false,
    deleted: false,
    createdAt: now - minutesAgo * MIN,
    ...extra,
  });

  await db.insert(messages).values([
    msg("mg_1", "cv_nadia", "u_rakib", "Did you see the new story ring animation?", 46),
    msg("mg_2", "cv_nadia", "u_nadia", "Yes! The sweep gradient is so good.", 44),
    msg("mg_3", "cv_nadia", "u_nadia", "Sending the updated spec now.", 40),
    msg("mg_4", "cv_nadia", "u_nadia", "Also — can we make the unread glow a little softer?", 3, { reaction: "love" }),
    msg("mg_5", "cv_arif", "u_arif", "Call quality logs from last night look clean.", 70),
    msg("mg_6", "cv_arif", "u_rakib", "Perfect. Ship it to the beta channel.", 66),
    msg("mg_7", "cv_arif", "u_arif", "Done. Also fixed the reconnect loop.", 26, { replyToId: "mg_6" }),
    msg("mg_8", "cv_samira", "u_samira", "Photos are uploaded to the shared album.", 210),
    msg("mg_9", "cv_priya", "u_priya", "Back Friday, let's do the meetup then.", 640),
    msg("mg_10", "cv_group_design", "u_farhana", "Type scale v3 pushed. 1.25 ratio everywhere.", 30),
    msg("mg_11", "cv_group_design", "u_tanvir", "The new empty states look incredible.", 20),
    msg("mg_12", "cv_group_design", "u_nadia", "Cover image for the group updated.", 11, {
      mediaUrl: L(16807908),
      mediaKind: "image",
    }),
    msg("mg_13", "cv_group_night", "u_imran", "Anyone else still awake?", 100),
    msg("mg_14", "cv_group_night", "u_arif", "Always.", 95),
  ]);

  await db.insert(notifications).values([
    { id: "nt_1", userId: "u_rakib", actorId: "u_nadia", kind: "message", targetId: "cv_nadia", body: "sent you a message", isRead: false, createdAt: now - 3 * MIN },
    { id: "nt_2", userId: "u_rakib", actorId: "u_priya", kind: "reaction", targetId: "p_2", body: "reacted fire to your post", isRead: false, createdAt: now - 14 * MIN },
    { id: "nt_3", userId: "u_rakib", actorId: "u_arif", kind: "comment", targetId: "p_2", body: "commented on your post", isRead: false, createdAt: now - 29 * MIN },
    { id: "nt_4", userId: "u_rakib", actorId: "u_farhana", kind: "follow", targetId: "u_farhana", body: "started following you", isRead: true, createdAt: now - 6 * HOUR },
    { id: "nt_5", userId: "u_rakib", actorId: "u_samira", kind: "story", targetId: "s_3", body: "reacted to your story", isRead: true, createdAt: now - 9 * HOUR },
  ]);

  await db.insert(seedState).values({ id: "seed", version: SEED_VERSION });
}
