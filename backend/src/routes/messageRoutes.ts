import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';
import { OperationMessage } from '../types';
import { pushNotifications } from '../push/PushNotificationService';

const router = Router();

// 1. List Messages
router.get('/messages', authenticate, async (req: AuthenticatedRequest, res) => {
  const driverId = req.user?.role === 'DRIVER' ? req.user.driverId : req.query.driverId;
  let list = await db.list<OperationMessage>('messages');

  if (driverId) {
    list = list.filter(m => m.driverId === driverId || m.recipientId === driverId || !m.recipientId);
  }

  res.json({ messages: list, count: list.length });
});

// 2. Dispatcher Sends Message
router.post('/messages/send', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req: AuthenticatedRequest, res) => {
  const { driverId, jobId, category, content, isUrgent } = req.body;

  if (!content) {
    res.status(400).json({ error: 'Message content is required' });
    return;
  }

  const message: OperationMessage = {
    id: randomUUID(),
    senderId: req.user?.id || 'dispatcher',
    senderName: req.user?.name || 'Operations Dispatcher',
    recipientId: driverId || null,
    driverId: driverId || null,
    jobId: jobId || null,
    category: category || (isUrgent ? 'URGENT' : 'DISPATCH'),
    content,
    sentAt: Date.now(),
    readAt: null,
    isUrgent: !!isUrgent
  };

  await db.transaction(async () => {
    await db.put('messages', message.id, message);
    await db.recordAuditAsync(
      req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'SEND_MESSAGE', 'MESSAGE', message.id, null, message,
      `Sent ${message.category} message to ${driverId || 'All drivers'}: ${content.substring(0, 40)}...`
    );
  });

  realtimeBroadcaster.broadcast('message.created', { message });
  if (driverId) {
    await pushNotifications.sendToDriver(
      driverId,
      isUrgent ? 'URGENT_NOTICE' : 'DISPATCH_MESSAGE',
      { messageId: message.id, jobId: jobId || '' }
    );
  }

  res.status(201).json({ success: true, message });
});

// 3. Mark Read
router.post('/messages/:id/read', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const msg = await db.get<OperationMessage>('messages', id);
  if (req.user?.role === 'DRIVER' && msg?.driverId !== req.user.driverId && msg?.recipientId !== req.user.driverId) {
    res.status(403).json({ error: 'Forbidden: Message belongs to another driver' });
    return;
  }
  if (msg) {
    msg.readAt = Date.now();
    await db.put('messages', id, msg);
  }
  res.json({ success: true, messageId: id });
});

export default router;
