'use strict';

const PRESENCE_TOPIC = 'convo_presence_v1';
const ACTIVE_GRACE_MS = 12 * 60 * 1000;

function toMillis(value) {
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value);
  if (value && typeof value === 'object') {
    const seconds = Number(value.seconds ?? value._seconds);
    const nanos = Number(value.nanoseconds ?? value._nanoseconds ?? 0);
    if (Number.isFinite(seconds)) return seconds * 1000 + Math.floor(nanos / 1e6);
  }
  return 0;
}

function clampInt(value, min, max, fallback) {
  const number = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(number) ? Math.max(min, Math.min(max, number)) : fallback;
}

function buildAdminUsersResponse({ profiles = [], presences = [], probeMeta = {}, now = Date.now(), limit = 50, cursor = 0, query = '' }) {
  const presenceByUid = new Map(presences.map((presence) => [String(presence.__id || ''), presence]));
  const normalizedQuery = String(query || '').trim().toLowerCase().slice(0, 100);
  const allUsers = profiles.map((profile) => {
    const uid = String(profile.uid || profile.__id || '');
    const presence = presenceByUid.get(uid) || {};
    const lastActive = toMillis(presence.lastActive);
    const lastPushReceivedAt = toMillis(presence.lastPushReceivedAt);
    const lastSignalAt = Math.max(lastActive, lastPushReceivedAt);
    const lastProbeReceivedAt = toMillis(presence.lastProbeReceivedAt);
    const onlineUntil = toMillis(presence.onlineUntil);
    const leaseActive = presence.isOnline === true && onlineUntil > now;
    const recentSignal = lastSignalAt > 0 && now - lastSignalAt <= ACTIVE_GRACE_MS;
    const reachable = leaseActive || recentSignal;
    const foreground = reachable && presence.foreground === true;
    return {
      uid,
      name: String(profile.name || profile.username || 'Convo Chat user').slice(0, 120),
      username: String(profile.username || '').slice(0, 80),
      status: foreground ? 'online' : reachable ? 'reachable' : 'offline',
      isActive: reachable,
      foreground,
      lastActive: lastSignalAt,
      lastProbeReceivedAt,
      hasFcmToken: typeof profile.fcmToken === 'string' && profile.fcmToken.length > 0
    };
  }).sort((a, b) => Number(b.isActive) - Number(a.isActive) || b.lastActive - a.lastActive || a.name.localeCompare(b.name));

  const filteredUsers = allUsers.filter((user) => {
    if (!normalizedQuery) return true;
    return user.name.toLowerCase().includes(normalizedQuery) ||
      user.username.toLowerCase().includes(normalizedQuery) ||
      user.uid.toLowerCase().includes(normalizedQuery);
  });
  const safeLimit = clampInt(limit, 1, 100, 50);
  const safeCursor = clampInt(cursor, 0, 1_000_000_000, 0);
  const selectedUsers = filteredUsers.slice(safeCursor, safeCursor + safeLimit);

  return {
    ok: true,
    version: '1.0.0',
    totalUsers: profiles.length,
    activeUsers: allUsers.filter((user) => user.isActive).length,
    foregroundUsers: allUsers.filter((user) => user.foreground).length,
    reachableUsers: allUsers.filter((user) => user.status === 'reachable').length,
    registeredDevices: profiles.filter((profile) => typeof profile.fcmToken === 'string' && profile.fcmToken.length > 0).length,
    lastProbeAt: toMillis(probeMeta.lastProbeAt),
    generatedAt: now,
    query: normalizedQuery,
    users: selectedUsers,
    nextCursor: safeCursor + safeLimit < filteredUsers.length ? String(safeCursor + safeLimit) : null
  };
}

function buildPresenceProbeMessage(probeId, sentAt) {
  return {
    topic: PRESENCE_TOPIC,
    data: {
      title: '',
      body: '',
      notificationType: 'presence_probe',
      probeId: String(probeId),
      sentAt: String(sentAt)
    },
    android: {
      priority: 'normal',
      ttl: 60_000
    }
  };
}

module.exports = { PRESENCE_TOPIC, ACTIVE_GRACE_MS, toMillis, clampInt, buildAdminUsersResponse, buildPresenceProbeMessage };
