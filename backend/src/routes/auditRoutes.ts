import { Router } from 'express';
import { db } from '../db';
import { authenticate, requireRole } from '../middleware/auth';

const router = Router();

// Dispatcher: Get Audit Logs
router.get('/audit/logs', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req, res) => {
  const { entityType, entityId, limit } = req.query;
  let logs = await db.list<any>('auditLogs');

  if (entityType) {
    logs = logs.filter(l => l.entityType === entityType);
  }
  if (entityId) {
    logs = logs.filter(l => l.entityId === entityId);
  }

  const max = limit ? parseInt(limit as string, 10) : 100;
  logs = logs.sort((a, b) => b.timestamp - a.timestamp);
  const result = logs.slice(0, max);

  res.json({ logs: result, count: result.length, total: logs.length });
});

export default router;
