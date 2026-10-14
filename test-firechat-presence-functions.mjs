import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const {
  PRESENCE_TOPIC,
  buildAdminUsersResponse,
  buildPresenceProbeMessage
} = require('./functions/presence-core.cjs');

const now = 1_800_000_000_000;
const response = buildAdminUsersResponse({
  now,
  limit: 2,
  cursor: 0,
  query: '',
  profiles: [
    { __id: 'u1', name: 'Online One', username: 'online', fcmToken: 'must-not-leak' },
    { __id: 'u2', name: 'Background Two', username: 'background', fcmToken: '' },
    { __id: 'u3', name: 'Offline Three', username: 'offline', fcmToken: 'another-secret-token' }
  ],
  presences: [
    { __id: 'u1', isOnline: true, foreground: true, onlineUntil: now + 60_000, lastActive: now - 10_000, lastProbeReceivedAt: now - 20_000 },
    { __id: 'u2', isOnline: false, foreground: false, lastPushReceivedAt: now - 60_000 }
  ],
  probeMeta: { lastProbeAt: { seconds: now / 1000, nanoseconds: 0 } }
});

assert.equal(response.ok, true);
assert.equal(response.totalUsers, 3);
assert.equal(response.activeUsers, 2);
assert.equal(response.foregroundUsers, 1);
assert.equal(response.reachableUsers, 1);
assert.equal(response.registeredDevices, 2);
assert.equal(response.lastProbeAt, now);
assert.equal(response.users[0].uid, 'u1');
assert.equal(response.users[0].status, 'online');
assert.equal(response.users[1].uid, 'u2');
assert.equal(response.users[1].status, 'reachable');
assert.equal(Object.hasOwn(response.users[0], 'fcmToken'), false);
assert.equal(response.nextCursor, '2');
assert.equal(buildAdminUsersResponse({ profiles: response.users, query: 'does-not-exist' }).users.length, 0);

const message = buildPresenceProbeMessage('probe-123', now);
assert.equal(message.topic, PRESENCE_TOPIC);
assert.equal(message.data.notificationType, 'presence_probe');
assert.equal(message.data.sentAt, String(now));
assert.equal(message.notification, undefined, 'the reachability probe must remain silent/data-only');
assert.equal(message.android.priority, 'normal');
assert.equal(message.android.ttl, 60_000);

console.log('Firebase Functions presence checks passed: silent FCM payload, topic, pagination, status summaries, and token privacy.');
