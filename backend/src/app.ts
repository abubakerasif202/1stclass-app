import cors from 'cors';
import express, { Express } from 'express';
import multer from 'multer';
import { randomUUID } from 'node:crypto';
import { realtimeBroadcaster } from './sse';
import { db } from './db';

import auditRoutes from './routes/auditRoutes';
import authRoutes from './routes/authRoutes';
import configRoutes from './routes/configRoutes';
import driverRoutes from './routes/driverRoutes';
import evidenceRoutes from './routes/evidenceRoutes';
import { evidenceStorageHealth } from './routes/evidenceRoutes';
import { pushNotifications } from './push/PushNotificationService';
import { authenticate, requireRole } from './middleware/auth';
import { withRequestId } from './requestContext';
import incidentRoutes from './routes/incidentRoutes';
import jobRoutes from './routes/jobRoutes';
import locationRoutes from './routes/locationRoutes';
import messageRoutes from './routes/messageRoutes';
import vehicleRoutes from './routes/vehicleRoutes';

export function createApp(): Express {
  const app = express();

  app.disable('x-powered-by');
  app.use((req, res, next) => {
    const incoming = req.header('X-Request-ID');
    const requestId = incoming && /^[A-Za-z0-9._-]{1,100}$/.test(incoming) ? incoming : randomUUID();
    res.locals.requestId = requestId;
    res.setHeader('X-Request-ID', requestId);
    withRequestId(requestId, next);
  });

  const configuredOrigins = (process.env.CORS_ORIGIN || '')
    .split(',')
    .map(origin => origin.trim())
    .filter(Boolean);
  if (process.env.NODE_ENV === 'production' && configuredOrigins.length === 0) {
    throw new Error('CORS_ORIGIN must name at least one trusted dispatcher origin in production');
  }
  const allowedOrigins = configuredOrigins.length > 0
    ? configuredOrigins
    : ['http://localhost:5173'];

  app.use(cors({
    origin(origin, callback) {
      if (!origin || allowedOrigins.includes(origin)) return callback(null, true);
      return callback(new Error('Origin is not allowed by CORS'));
    },
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Idempotency-Key', 'X-CSRF-Token', 'X-Request-ID'],
    credentials: true
  }));

  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  app.use((req, res, next) => {
    if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) return next();
    if (db.usingPostgres) return next();
    const originalJson = res.json.bind(res);
    res.json = ((body: any) => {
      void db.persist().then(() => originalJson(body)).catch(next);
      return res;
    }) as typeof res.json;
    next();
  });

  // Health check
  app.get(['/health', '/health/live', '/v1/health'], (_req, res) => res.json({ status: 'UP' }));
  app.get('/health/ready', authenticate, requireRole('ADMIN', 'OPERATIONS'), async (_req, res, next) => {
    try {
      const [database, storage, push] = await Promise.all([
        db.health(), evidenceStorageHealth(), pushNotifications.health()
      ]);
      const ready = database && storage && push;
      res.status(ready ? 200 : 503).json({ status: ready ? 'READY' : 'NOT_READY', dependencies: {
        database: database ? 'UP' : 'DOWN', storage: storage ? 'UP' : 'DOWN', push: push ? 'UP' : 'DOWN'
      }});
    } catch (error) { next(error); }
  });

  // Realtime SSE stream
  app.get('/v1/events/stream', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), (req, res) => {
    const clientId = `client-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;
    realtimeBroadcaster.subscribe(clientId, res);
  });

  // API Routes
  app.use('/v1/auth', authRoutes);
  app.use('/v1', jobRoutes);
  app.use('/v1', locationRoutes);
  app.use('/v1', incidentRoutes);
  app.use('/v1', vehicleRoutes);
  app.use('/v1', driverRoutes);
  app.use('/v1', messageRoutes);
  app.use('/v1', auditRoutes);
  app.use('/v1', configRoutes);
  app.use('/v1', evidenceRoutes);

  // 404 handler
  app.use((req, res) => {
    res.status(404).json({ error: 'Route not found', code: 'ROUTE_NOT_FOUND', requestId: res.locals.requestId });
  });

  app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    if (res.headersSent) return;
    if (error instanceof multer.MulterError && error.code === 'LIMIT_FILE_SIZE') {
      res.status(413).json({ error: 'Evidence file exceeds the 10 MiB limit', code: 'UPLOAD_TOO_LARGE', requestId: res.locals.requestId });
      return;
    }
    if (process.env.NODE_ENV !== 'test') {
      console.error(JSON.stringify({ requestId: res.locals.requestId, error: error instanceof Error ? error.name : 'Error' }));
    }
    res.status(500).json({ error: 'Internal server error', code: 'INTERNAL_ERROR', requestId: res.locals.requestId });
  });

  return app;
}
