import { Router } from 'express';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, generateToken, verifyRefreshToken } from '../middleware/auth';
import { createHash, randomUUID } from 'node:crypto';
import { verifyCredential } from '../security/credentials';
import { loginRateLimit, refreshRateLimit } from '../middleware/rateLimits';

const router = Router();
const ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000;
const REFRESH_TOKEN_TTL_MS = 7 * 24 * 60 * 60 * 1000;

// Driver Login
const tokenHash = (token: string) => createHash('sha256').update(token).digest('hex');

async function issueDriverTokens(driverId: string, name: string) {
  const now = Date.now();
  const sessionId = randomUUID();
  const token = generateToken({ id: driverId, driverId, name, role: 'DRIVER', type: 'access' }, '15m');
  const refreshToken = generateToken(
    { id: driverId, driverId, type: 'refresh', jti: sessionId }, '7d'
  );
  await db.put('refreshSessions', sessionId, {
    id: sessionId,
    userId: driverId,
    driverId,
    tokenHash: tokenHash(refreshToken),
    expiresAt: now + REFRESH_TOKEN_TTL_MS,
    revokedAt: null,
    replacedBy: null,
    createdAt: now
  });
  return { token, refreshToken, sessionId, expiresAt: now + ACCESS_TOKEN_TTL_MS };
}

router.post('/driver/login', loginRateLimit, async (req, res) => {
  const { driverId, pin, deviceId, appVersion, pushToken } = req.body;

  if (!driverId || !pin) {
    res.status(400).json({ error: 'Driver ID and PIN are required' });
    return;
  }

  const driver = await db.get<{ id: string; name: string; active: boolean; pinHash: string; appVersion: string; pushToken: string | null; lastSeen: number; shiftStatus: string; currentVehicleId: string | null }>('drivers', driverId);
  if (!driver || !driver.active || !verifyCredential(String(pin), driver.pinHash)) {
    res.status(401).json({ error: 'Invalid Driver ID or PIN' });
    return;
  }

  // Update device metadata if provided
  if (appVersion) driver.appVersion = appVersion;
  if (pushToken) driver.pushToken = pushToken;
  driver.lastSeen = Date.now();
  await db.put('drivers', driver.id, driver);

  const { token, refreshToken, expiresAt } = await issueDriverTokens(driver.id, driver.name);

  res.json({
    token,
    refreshToken,
    driverId: driver.id,
    name: driver.name,
    shiftStatus: driver.shiftStatus,
    currentVehicleId: driver.currentVehicleId,
    expiresAt
  });
});

// Driver Refresh
router.post('/driver/refresh', refreshRateLimit, async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) {
    res.status(400).json({ error: 'Refresh token is required' });
    return;
  }
  try {
    const claims = verifyRefreshToken(refreshToken);
    const driver = await db.get<{ id: string; name: string; active: boolean }>('drivers', claims.driverId);
    const session = await db.get<{ revokedAt: number | null; expiresAt: number; tokenHash: string; replacedBy: string | null }>('refreshSessions', claims.sessionId);
    if (!driver?.active || !session || session.revokedAt || session.expiresAt <= Date.now() ||
        session.tokenHash !== tokenHash(refreshToken)) {
      res.status(401).json({ error: 'Invalid refresh token subject' });
      return;
    }
    const rotated = await issueDriverTokens(driver.id, driver.name);
    session.revokedAt = Date.now();
    session.replacedBy = rotated.sessionId;
    await db.put('refreshSessions', claims.sessionId, session);
    res.json({
      token: rotated.token,
      refreshToken: rotated.refreshToken,
      expiresAt: rotated.expiresAt
    });
  } catch {
    res.status(401).json({ error: 'Expired or invalid refresh token' });
  }
});

// Dispatcher Login
router.post('/dispatch/login', loginRateLimit, async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    res.status(400).json({ error: 'Email and password are required' });
    return;
  }

  const user = (await db.list<{ id: string; email: string; name: string; role: any; passwordHash: string; active: boolean }>('dispatchUsers'))
    .find(candidate => candidate.email.toLowerCase() === String(email).toLowerCase());
  if (!user || !user.active || !verifyCredential(String(password), user.passwordHash)) {
    res.status(401).json({ error: 'Invalid operations email or password' });
    return;
  }

  const csrfToken = randomUUID();
  const token = generateToken({
    id: user.id,
    email: user.email,
    name: user.name,
    role: user.role,
    type: 'access',
    csrf: csrfToken
  }, '15m');
  res.cookie('tms_dispatch_session', token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging',
    sameSite: 'strict',
    maxAge: ACCESS_TOKEN_TTL_MS,
    path: '/'
  });

  res.json({
    csrfToken,
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role
    },
    expiresAt: Date.now() + ACCESS_TOKEN_TTL_MS
  });
});

router.post('/dispatch/logout', authenticate, (req, res) => {
  res.clearCookie('tms_dispatch_session', { path: '/' });
  res.json({ success: true });
});

router.post('/driver/logout', authenticate, async (req: AuthenticatedRequest, res) => {
  const driverId = req.user?.driverId;
  const sessions = await db.list<{ id: string; driverId: string | null; revokedAt: number | null }>('refreshSessions');
  for (const session of sessions) {
    if (session.driverId === driverId && !session.revokedAt) {
      session.revokedAt = Date.now();
      await db.put('refreshSessions', session.id, session);
    }
  }
  res.json({ success: true });
});

// Get Current User Profile
router.get('/dispatch/me', authenticate, (req: AuthenticatedRequest, res) => {
  if (!req.user) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }
  if (req.user.role === 'DRIVER') {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }
  const { csrf, ...user } = req.user;
  res.json({ user, csrfToken: csrf });
});

export default router;
