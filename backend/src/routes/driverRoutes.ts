import { Router } from 'express';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireDriver, requireDriverIdentity, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';

const router = Router();

// 1. Dispatcher: List Drivers
router.get('/drivers', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const { shiftStatus } = req.query;
  let list = await db.list<any>('drivers');

  if (shiftStatus && shiftStatus !== 'ALL') {
    list = list.filter(d => d.shiftStatus === shiftStatus);
  }

  const enriched = await Promise.all(list.map(async d => {
    const { pinHash: _pinHash, pushToken: _pushToken, ...safeDriver } = d;
    const vehicle = d.currentVehicleId ? await db.get<any>('vehicles', d.currentVehicleId) : null;
    const activeJob = d.activeJobId ? await db.get<any>('jobs', d.activeJobId) : null;
    const latestLoc = await db.get<any>('latestLocations', d.id);

    return {
      ...safeDriver,
      vehicleRego: vehicle?.rego || 'No Vehicle',
      activeJobReference: activeJob?.reference || 'No Active Job',
      activeJobStatus: activeJob?.status || null,
      latestLocation: latestLoc || null,
      pushEnabled: Boolean(d.pushToken)
    };
  }));

  res.json({ drivers: enriched, count: enriched.length });
});

// 2. Driver Profile
router.get('/driver/profile', authenticate, requireDriver, requireDriverIdentity('query'), async (req: AuthenticatedRequest, res) => {
  const driverId = req.user!.driverId!;
  const driver = driverId ? await db.get<any>('drivers', driverId) : (await db.list<any>('drivers'))[0];
  if (!driver) {
    res.status(404).json({ error: 'Driver profile not found' });
    return;
  }
  const { pinHash: _pinHash, ...safeDriver } = driver;
  res.json({ driver: safeDriver });
});

// 3. Shift Events (Start / End Shift)
router.post('/driver/shifts/:id/events', authenticate, requireDriverIdentity(), idempotencyMiddleware, async (req, res) => {
  const { id } = req.params;
  const { driverId, eventType, timestamp, startOdometer, endOdometer, vehicleId } = req.body;

  const driver = await db.get<any>('drivers', driverId);
  if (!driver) { res.status(404).json({ error: 'Driver not found' }); return; }
  if (eventType !== 'SHIFT_STARTED' && driver.currentShiftId !== id) {
    res.status(403).json({ error: 'Forbidden: Shift does not belong to this driver' }); return;
  }
  if (vehicleId) {
    const vehicle = await db.get<any>('vehicles', vehicleId);
    if (!vehicle || (vehicle.currentDriverId && vehicle.currentDriverId !== driverId)) {
      res.status(403).json({ error: 'Forbidden: Vehicle is assigned to another driver' }); return;
    }
  }
  {
    if (eventType === 'SHIFT_STARTED') {
      driver.shiftStatus = 'ON_DUTY';
      driver.currentShiftId = id;
      if (vehicleId) driver.currentVehicleId = vehicleId;
    } else if (eventType === 'SHIFT_ENDED') {
      driver.shiftStatus = 'OFF_DUTY';
      driver.currentShiftId = null;
      driver.currentVehicleId = null;
      driver.activeJobId = null;
    }
  }
  await db.put('drivers', driver.id, driver);

  realtimeBroadcaster.broadcast('driver.shift_event', {
    driverId,
    shiftId: id,
    eventType,
    timestamp: timestamp || Date.now()
  });

  res.json({ success: true, shiftId: id, eventType });
});

// 4. Driver Pre-Start Inspection
router.post('/driver/shifts/:id/inspection', authenticate, requireDriverIdentity(), idempotencyMiddleware, async (req, res) => {
  const { id } = req.params;
  const { driverId, vehicleId, odometer, passed, defectItems } = req.body;

  const driver = await db.get<any>('drivers', driverId);
  if (!driver || driver.currentShiftId !== id) {
    res.status(403).json({ error: 'Forbidden: Inspection shift does not belong to this driver' }); return;
  }
  if (!vehicleId || driver.currentVehicleId !== vehicleId) {
    res.status(403).json({ error: 'Forbidden: Inspection vehicle is not assigned to this driver' }); return;
  }

  if (vehicleId) {
    const vehicle = await db.get<any>('vehicles', vehicleId);
    if (vehicle) {
      vehicle.lastPreStartAt = Date.now();
      if (odometer) vehicle.odometer = Number(odometer);
      if (!passed) vehicle.status = 'DEFECT';
      await db.put('vehicles', vehicle.id, vehicle);
    }
  }

  res.json({ success: true, shiftId: id, inspectionRecorded: true });
});

// 5. Device Registration
router.post('/devices/register', authenticate, requireDriverIdentity(), async (req, res) => {
  const { deviceId, driverId, appVersion, platform, pushToken } = req.body;
  if (!deviceId || !driverId) {
    res.status(400).json({ error: 'deviceId and driverId are required' });
    return;
  }

  const driver = await db.get<any>('drivers', driverId);
  if (driver) {
    if (appVersion) driver.appVersion = appVersion;
    if (pushToken) driver.pushToken = pushToken;
    driver.lastSeen = Date.now();
    await db.put('drivers', driver.id, driver);
  }
  const now = Date.now();
  for (const candidate of await db.list<any>('deviceRegistrations')) {
    const registeredDeviceId = candidate.deviceId;
    if (pushToken && candidate.pushToken === pushToken && registeredDeviceId !== deviceId) {
      candidate.pushToken = null;
      candidate.pushEnabled = false;
      candidate.updatedAt = now;
      await db.put('deviceRegistrations', registeredDeviceId, candidate);
    }
  }
  await db.put('deviceRegistrations', deviceId, {
    deviceId,
    driverId,
    platform: platform || 'android',
    appVersion: appVersion || null,
    pushToken: pushToken || null,
    pushEnabled: Boolean(pushToken),
    lastSeenAt: now,
    updatedAt: now
  });

  res.json({ success: true, registered: true, deviceId });
});

// 6. Push Token Update
router.post('/devices/push-token', authenticate, requireDriverIdentity(), async (req, res) => {
  const { deviceId, driverId, pushToken } = req.body;
  const driver = await db.get<any>('drivers', driverId);
  if (driver && pushToken) {
    driver.pushToken = pushToken;
    driver.lastSeen = Date.now();
    await db.put('drivers', driver.id, driver);
  }
  const registration = await db.get<any>('deviceRegistrations', deviceId);
  if (!registration || registration.driverId !== driverId) {
    res.status(404).json({ error: 'Device registration not found' });
    return;
  }
  for (const candidate of await db.list<any>('deviceRegistrations')) {
    const registeredDeviceId = candidate.deviceId;
    if (candidate.pushToken === pushToken && registeredDeviceId !== deviceId) {
      candidate.pushToken = null;
      candidate.pushEnabled = false;
      candidate.updatedAt = Date.now();
      await db.put('deviceRegistrations', registeredDeviceId, candidate);
    }
  }
  registration.pushToken = pushToken;
  registration.pushEnabled = true;
  registration.lastSeenAt = Date.now();
  registration.updatedAt = Date.now();
  await db.put('deviceRegistrations', deviceId, registration);
  res.json({ success: true, updated: true });
});

export default router;
