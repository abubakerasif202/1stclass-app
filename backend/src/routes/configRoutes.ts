import { Router } from 'express';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';

const router = Router();

// Get App Config (Public for mobile app & dispatcher)
router.get('/app/config', async (_req, res) => {
  res.json((await db.get('appConfig', 'singleton')) || db.appConfig);
});

// Update App Config (Admin only)
router.put('/app/config', authenticate, requireRole('ADMIN'), async (req: AuthenticatedRequest, res) => {
  const { minSupportedAppVersion, latestAppVersion, supportPhoneNumber, supportEmail, features } = req.body;
  const config = (await db.get<typeof db.appConfig>('appConfig', 'singleton')) || db.appConfig;

  const prev = JSON.parse(JSON.stringify(config));

  if (minSupportedAppVersion) config.minSupportedAppVersion = minSupportedAppVersion;
  if (latestAppVersion) config.latestAppVersion = latestAppVersion;
  if (supportPhoneNumber) config.supportPhoneNumber = supportPhoneNumber;
  if (supportEmail) config.supportEmail = supportEmail;
  if (features) config.features = { ...config.features, ...features };
  await db.transaction(async () => {
    await db.put('appConfig', 'singleton', config);
    await db.recordAuditAsync(
      req.user?.id || 'admin', req.user?.name || 'Administrator', 'ADMIN', 'UPDATE_CONFIG',
      'CONFIG', 'app_config', prev, config, 'Updated remote app configuration and feature flags'
    );
  });

  realtimeBroadcaster.broadcast('config.updated', { config });

  res.json({ success: true, config });
});

export default router;
