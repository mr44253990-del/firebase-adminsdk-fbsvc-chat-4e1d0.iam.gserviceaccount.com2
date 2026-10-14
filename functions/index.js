'use strict';

const crypto = require('node:crypto');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { logger } = require('firebase-functions');
const { defineString } = require('firebase-functions/params');
const { onRequest } = require('firebase-functions/v2/https');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const {
  PRESENCE_TOPIC,
  buildAdminUsersResponse,
  buildPresenceProbeMessage
} = require('./presence-core.cjs');

initializeApp();

const REGION = 'asia-south1';
const ADMIN_EMAIL = defineString('ADMIN_EMAIL', { default: 'mr4425390@gmail.com' });
const VERSION = '1.0.0';

/**
 * Sends one silent FCM data message every five minutes. This is a presence
 * signal, not a user-visible notification. Android may delay or skip delivery.
 */
exports.presenceProbeEveryFiveMinutes = onSchedule({
  schedule: '*/5 * * * *',
  timeZone: 'Asia/Dhaka',
  region: REGION,
  timeoutSeconds: 60,
  memory: '256MiB',
  maxInstances: 1
}, async () => {
  const result = await runPresenceProbe('scheduled');
  logger.info('Five-minute FCM presence probe submitted', result);
});

/**
 * Drop-in HTTPS API for the app's presence dashboard. Paste this function's
 * base URL into Admin → Presence / 5-min probe (without /health).
 */
exports.presenceAdmin = onRequest({
  region: REGION,
  timeoutSeconds: 60,
  memory: '256MiB',
  maxInstances: 5
}, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  const rawPath = String(req.path || '/').replace(/\/+$/, '') || '/';
  const path = rawPath === '/presenceAdmin' ? '/' : rawPath.startsWith('/presenceAdmin/') ? rawPath.slice('/presenceAdmin'.length) : rawPath;

  if (req.method === 'GET' && path === '/health') {
    return res.status(200).json({
      ok: true,
      service: 'Convo Chat Firebase Presence Monitor',
      provider: 'firebase-functions',
      version: VERSION,
      topicConfigured: true,
      probeIntervalMinutes: 5,
      userCollection: 'users',
      timestamp: Date.now()
    });
  }

  if (req.method === 'GET' && path === '/admin/users') {
    const caller = await requireAdmin(req, res);
    if (!caller) return;
    try {
      const limit = req.query.limit;
      const cursor = req.query.cursor;
      const query = String(req.query.q || '').trim().slice(0, 100);
      const db = getFirestore();
      const [profileSnapshot, presenceSnapshot, probeSnapshot] = await Promise.all([
        db.collection('users').get(),
        db.collection('presence').get(),
        db.collection('presence_monitor').doc('state').get()
      ]);
      const profiles = profileSnapshot.docs.map((doc) => ({ ...doc.data(), __id: doc.id }));
      const presences = presenceSnapshot.docs.map((doc) => ({ ...doc.data(), __id: doc.id }));
      const probeMeta = probeSnapshot.exists ? probeSnapshot.data() : {};
      return res.status(200).json(buildAdminUsersResponse({
        profiles,
        presences,
        probeMeta,
        now: Date.now(),
        limit,
        cursor,
        query
      }));
    } catch (error) {
      logger.error('Could not load the admin presence list', { message: error?.message });
      return res.status(500).json({ ok: false, error: 'Could not load Firebase user presence' });
    }
  }

  if (req.method === 'POST' && path === '/admin/probe') {
    const caller = await requireAdmin(req, res);
    if (!caller) return;
    try {
      const result = await runPresenceProbe('admin-test');
      return res.status(200).json({ ok: true, ...result });
    } catch (error) {
      logger.error('Manual presence probe failed', { message: error?.message });
      return res.status(502).json({ ok: false, error: 'FCM presence probe could not be sent' });
    }
  }

  return res.status(404).json({ ok: false, error: 'Not found' });
});

async function requireAdmin(req, res) {
  const authorization = String(req.get('Authorization') || '');
  if (!authorization.startsWith('Bearer ')) {
    res.status(401).json({ ok: false, error: 'Unauthorized Firebase caller' });
    return null;
  }
  try {
    const decoded = await getAuth().verifyIdToken(authorization.slice(7));
    const email = String(decoded.email || '').trim().toLowerCase().replace(/\.+$/, '');
    const allowedEmail = String(ADMIN_EMAIL.value() || '').trim().toLowerCase().replace(/\.+$/, '');
    if (email !== allowedEmail) {
      res.status(403).json({ ok: false, error: 'Admin only' });
      return null;
    }
    return decoded;
  } catch (_) {
    res.status(401).json({ ok: false, error: 'Unauthorized Firebase caller' });
    return null;
  }
}

async function runPresenceProbe(source) {
  const now = Date.now();
  const probeId = crypto.randomUUID();
  const messageId = await getMessaging().send(buildPresenceProbeMessage(probeId, now));
  await getFirestore().collection('presence_monitor').doc('state').set({
    lastProbeAt: now,
    lastProbeId: probeId,
    lastProbeMessage: messageId,
    lastProbeTopic: PRESENCE_TOPIC,
    lastProbeStatus: 'sent',
    lastProbeSource: source
  }, { merge: true });
  return { probeId, sentAt: now, topic: PRESENCE_TOPIC, fcmMessageId: messageId };
}
