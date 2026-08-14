import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { db } from '../db';
import { UserRole } from '../types';
import { createHash } from 'node:crypto';

const DISPATCH_ROLES: readonly UserRole[] = ['ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'];

interface BaseAccessTokenClaims {
  id: string;
  name: string;
  type: 'access';
  email?: string;
  driverId?: string;
  csrf?: string;
}

export interface DriverAccessTokenClaims extends BaseAccessTokenClaims {
  role: 'DRIVER';
  driverId: string;
}

export interface DispatcherAccessTokenClaims extends BaseAccessTokenClaims {
  role: UserRole;
  email: string;
}

export type AccessTokenClaims = DriverAccessTokenClaims | DispatcherAccessTokenClaims;

function jwtSecret(): string {
  const configured = process.env.JWT_SECRET;
  if (configured && configured.length >= 32) return configured;
  if (process.env.NODE_ENV === 'test') {
    return 'test-only-jwt-secret-not-valid-outside-tests';
  }
  throw new Error('JWT_SECRET must be configured with at least 32 characters');
}

export interface AuthenticatedRequest extends Request {
  user?: AccessTokenClaims;
}

export function generateToken(payload: object, expiresIn: string = '24h'): string {
  return jwt.sign(payload, jwtSecret(), {
    expiresIn,
    issuer: '1stclass-transport-api',
    audience: '1stclass-tms'
  } as any);
}

export function verifyRefreshToken(token: string): { id: string; driverId: string; sessionId: string } {
  const decoded = jwt.verify(token, jwtSecret(), {
    issuer: '1stclass-transport-api', audience: '1stclass-tms'
  }) as {
    id?: unknown;
    driverId?: unknown;
    type?: unknown;
    jti?: unknown;
  };
  if (
    decoded.type !== 'refresh' ||
    typeof decoded.id !== 'string' ||
    typeof decoded.driverId !== 'string' ||
    decoded.id !== decoded.driverId ||
    typeof decoded.jti !== 'string'
  ) {
    throw new Error('Invalid refresh token claims');
  }
  return { id: decoded.id, driverId: decoded.driverId, sessionId: decoded.jti };
}

function isAccessTokenClaims(decoded: unknown): decoded is AccessTokenClaims {
  if (!decoded || typeof decoded !== 'object') return false;
  const claims = decoded as Record<string, unknown>;
  if (
    claims.type !== 'access' ||
    typeof claims.id !== 'string' ||
    typeof claims.name !== 'string' ||
    typeof claims.role !== 'string'
  ) {
    return false;
  }
  if (claims.role === 'DRIVER') {
    return typeof claims.driverId === 'string' && claims.driverId === claims.id;
  }
  return DISPATCH_ROLES.includes(claims.role as UserRole) && typeof claims.email === 'string';
}

function readDispatcherSessionCookie(req: Request): string | undefined {
  const prefix = 'tms_dispatch_session=';
  const value = req.headers.cookie
    ?.split(';')
    .map(cookie => cookie.trim())
    .find(cookie => cookie.startsWith(prefix));
  return value?.slice(prefix.length);
}

export async function authenticate(req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  const cookieToken = readDispatcherSessionCookie(req);
  const token = authHeader?.startsWith('Bearer ') ? authHeader.substring(7) : cookieToken;
  if (!token) {
    res.status(401).json({ error: 'Unauthorized: Missing or invalid token header' });
    return;
  }
  try {
    const decoded = jwt.verify(token, jwtSecret(), {
      issuer: '1stclass-transport-api', audience: '1stclass-tms'
    });
    if (!isAccessTokenClaims(decoded)) throw new Error('Invalid access token claims');
    if (token === cookieToken && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) {
      const csrf = req.header('X-CSRF-Token');
      if (!decoded.csrf || csrf !== decoded.csrf) {
        res.status(403).json({ error: 'Forbidden: CSRF validation failed' });
        return;
      }
    }
    if (decoded.role === 'DRIVER') {
      const driver = await db.get<{ active: boolean }>('drivers', decoded.driverId);
      if (!driver?.active) throw new Error('Driver disabled');
    } else {
      const users = await db.list<{ id: string; active: boolean }>('dispatchUsers');
      const user = users.find(candidate => candidate.id === decoded.id);
      if (!user?.active) throw new Error('User disabled');
    }
    req.user = decoded;
    next();
  } catch (err) {
    res.status(401).json({ error: 'Unauthorized: Expired or invalid token' });
  }
}

export function requireDriver(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  if (req.user?.role !== 'DRIVER' || !req.user.driverId) {
    res.status(403).json({ error: 'Forbidden: Driver authentication required' });
    return;
  }
  next();
}

export function requireDriverIdentity(source: 'body' | 'query' = 'body') {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (req.user?.role !== 'DRIVER' || !req.user.driverId) {
      res.status(403).json({ error: 'Forbidden: Driver authentication required' });
      return;
    }
    const claimed = source === 'body' ? req.body?.driverId : req.query?.driverId;
    if (claimed && claimed !== req.user.driverId) {
      res.status(403).json({ error: 'Forbidden: Cross-driver access denied' });
      return;
    }
    next();
  };
}

export async function requireAssignedDriver(req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> {
  if (req.user?.role !== 'DRIVER' || !req.user.driverId) {
    res.status(403).json({ error: 'Forbidden: Driver authentication required' });
    return;
  }
  const job = await db.get<{ assignedDriverId: string | null }>('jobs', req.params.id);
  if (!job || job.assignedDriverId !== req.user.driverId) {
    res.status(403).json({ error: 'Forbidden: Job is not assigned to this driver' });
    return;
  }
  next();
}

export function requireRole(...allowedRoles: UserRole[]) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (!req.user || !req.user.role) {
      res.status(403).json({ error: 'Forbidden: User role not found' });
      return;
    }
    if (req.user.role === 'DRIVER' || !allowedRoles.includes(req.user.role)) {
      res.status(403).json({
        error: `Forbidden: Role '${req.user.role}' is not authorized for this operation. Required: ${allowedRoles.join(', ')}`
      });
      return;
    }
    next();
  };
}

export async function idempotencyMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  const idempotencyKey = req.headers['x-idempotency-key'] as string;
  if (!idempotencyKey) {
    next();
    return;
  }

  const authenticated = req as AuthenticatedRequest;
  const actor = authenticated.user?.id || authenticated.user?.driverId || 'anonymous';
  const scope = `${actor}:${req.method}:${req.baseUrl}${req.path}:${idempotencyKey}`;
  const fingerprint = createHash('sha256').update(JSON.stringify(req.body || null)).digest('hex');
  let existing = await db.readIdempotency(scope);
  if (existing) {
    if (existing.requestFingerprint !== fingerprint) {
      res.status(409).json({ error: 'Idempotency key reused with different request', code: 'IDEMPOTENCY_CONFLICT' });
      return;
    }
    if (!existing.statusCode || existing.statusCode < 100) {
      res.status(409).json({ error: 'Idempotency request is already in progress', code: 'IDEMPOTENCY_IN_PROGRESS' });
      return;
    }
    res.status(existing.statusCode).json(existing.response);
    return;
  }

  const expiresAt = Date.now() + Number(process.env.IDEMPOTENCY_TTL_MS || 86400000);
  if (!await db.reserveIdempotency(scope, fingerprint, expiresAt)) {
    existing = await db.readIdempotency(scope);
    if (existing?.requestFingerprint !== fingerprint) {
      res.status(409).json({ error: 'Idempotency key reused with different request', code: 'IDEMPOTENCY_CONFLICT' });
    } else {
      res.status(409).json({ error: 'Idempotency request is already in progress', code: 'IDEMPOTENCY_IN_PROGRESS' });
    }
    return;
  }

  // Intercept json send to cache response
  const originalJson = res.json.bind(res);
  res.json = (body: any) => {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      void db.completeIdempotency(scope, res.statusCode, body).then(
        () => originalJson(body),
        next
      );
      return res;
    }
    return originalJson(body);
  };

  next();
}
