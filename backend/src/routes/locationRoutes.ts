import { Router } from 'express';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireDriver, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';
import { LocationTelemetry } from '../types';

const router = Router();

// Ingest Driver Batch Location Points
router.post('/driver/locations', authenticate, requireDriver, idempotencyMiddleware, async (req: AuthenticatedRequest, res) => {
  const points = req.body.points as any[];
  if (!points || !Array.isArray(points)) {
    res.status(400).json({ error: 'Array of location points required' });
    return;
  }
  if (points.some(point => point.driverId !== req.user!.driverId)) {
    res.status(403).json({ error: 'Forbidden: Cross-driver telemetry denied' });
    return;
  }
  const driver = await db.get<any>('drivers', req.user!.driverId!);
  if (!driver) { res.status(404).json({ error: 'Driver not found' }); return; }
  if (points.some(point =>
    typeof point.latitude !== 'number' || !Number.isFinite(point.latitude) || point.latitude < -90 || point.latitude > 90 ||
    typeof point.longitude !== 'number' || !Number.isFinite(point.longitude) || point.longitude < -180 || point.longitude > 180
  )) { res.status(400).json({ error: 'Valid latitude and longitude are required' }); return; }
  if (points.some(point => point.vehicleId && point.vehicleId !== driver.currentVehicleId)) {
    res.status(403).json({ error: 'Forbidden: Telemetry vehicle is not assigned to this driver' }); return;
  }
  const pointJobs = await Promise.all(points.filter(point => point.jobId).map(point => db.get<any>('jobs', point.jobId)));
  if (pointJobs.some((job, index) => job?.assignedDriverId !== points.filter(point => point.jobId)[index].driverId)) {
    res.status(403).json({ error: 'Forbidden: Telemetry job is not assigned to this driver' }); return;
  }

  const now = Date.now();
  await db.transaction(async () => {
  for (const p of points) {
    const telemetry: LocationTelemetry = {
      driverId: p.driverId,
      vehicleId: p.vehicleId || null,
      jobId: p.jobId || null,
      latitude: p.latitude,
      longitude: p.longitude,
      accuracyMeters: p.accuracyMeters || 5.0,
      speedMetersPerSecond: p.speedMetersPerSecond !== undefined ? p.speedMetersPerSecond : null,
      bearingDegrees: p.bearingDegrees !== undefined ? p.bearingDegrees : null,
      altitudeMeters: p.altitudeMeters !== undefined ? p.altitudeMeters : null,
      batteryLevel: p.batteryLevel !== undefined ? p.batteryLevel : null,
      networkState: p.networkState || 'CELLULAR',
      source: p.source || 'FUSED_LOCATION',
      recordedAt: p.recordedAt || now,
      receivedAt: now
    };

    await db.put('latestLocations', p.driverId, telemetry);
    await db.put('telemetry', `${p.driverId}:${telemetry.recordedAt}:${Math.random().toString(36).slice(2, 8)}`, telemetry);

    // Update driver last seen & active vehicle
    driver.lastSeen = now;
    if (p.vehicleId) driver.currentVehicleId = p.vehicleId;
    if (p.jobId) driver.activeJobId = p.jobId;

    // Broadcast realtime event
    realtimeBroadcaster.broadcast('driver.location_updated', {
      driverId: p.driverId,
      telemetry,
      driverName: driver?.name || p.driverId
    });
  }
  await db.put('drivers', driver.id, driver);
  });

  await db.pruneTelemetry(Number(process.env.TELEMETRY_RETENTION_DAYS || 0), Number(process.env.TELEMETRY_MAX_POINTS || 500000));

  res.json({
    success: true,
    acknowledgedCount: points.length,
    timestamp: now
  });
});

// Dispatcher: Get Latest Fleet Locations Enriched
router.get('/fleet/locations/latest', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const now = Date.now();
  const locations = await db.list<LocationTelemetry>('latestLocations');
  const fleet = await Promise.all(locations.map(async loc => {
    const driver = await db.get<any>('drivers', loc.driverId);
    const vehicle = loc.vehicleId ? await db.get<any>('vehicles', loc.vehicleId) : (driver?.currentVehicleId ? await db.get<any>('vehicles', driver.currentVehicleId) : null);
    const job = loc.jobId ? await db.get<any>('jobs', loc.jobId) : (driver?.activeJobId ? await db.get<any>('jobs', driver.activeJobId) : null);

    const ageSeconds = Math.max(0, Math.floor((now - loc.recordedAt) / 1000));
    const isStale = ageSeconds > 120; // 2 minutes threshold

    let speedKmh = 0;
    if (loc.speedMetersPerSecond !== null && loc.speedMetersPerSecond !== undefined) {
      speedKmh = Math.round(loc.speedMetersPerSecond * 3.6);
    }

    let movementStatus: string = 'STATIONARY';
    if (job?.status === 'AT_PICKUP') movementStatus = 'AT_PICKUP';
    else if (job?.status === 'AT_DELIVERY') movementStatus = 'AT_DELIVERY';
    else if (speedKmh > 5) movementStatus = 'MOVING';
    else if (driver?.shiftStatus === 'ON_BREAK') movementStatus = 'ON_BREAK';
    else if (driver?.shiftStatus === 'OFF_DUTY') movementStatus = 'OFFLINE';

    return {
      driverId: loc.driverId,
      driverName: driver?.name || loc.driverId,
      driverPhone: driver?.phone || '',
      shiftStatus: driver?.shiftStatus || 'OFF_DUTY',
      vehicleId: vehicle?.id || loc.vehicleId || 'N/A',
      vehicleRego: vehicle?.rego || 'N/A',
      vehicleModel: vehicle?.makeModel || 'N/A',
      jobId: job?.id || null,
      jobReference: job?.reference || 'No Active Job',
      jobStatus: job?.status || null,
      latitude: loc.latitude,
      longitude: loc.longitude,
      accuracyMeters: loc.accuracyMeters,
      speedKmh,
      bearingDegrees: loc.bearingDegrees,
      batteryLevel: loc.batteryLevel,
      networkState: loc.networkState,
      recordedAt: loc.recordedAt,
      ageSeconds,
      isStale,
      movementStatus
    };
  }));

  res.json({ fleet, count: fleet.length });
});

// Dispatcher: Get Driver Route History
router.get('/fleet/locations/history/:driverId', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req, res) => {
  const { driverId } = req.params;
  const history = (await db.list<LocationTelemetry>('telemetry'))
    .filter(l => l.driverId === driverId)
    .sort((a, b) => a.recordedAt - b.recordedAt)
    .slice(-100); // Last 100 points
  res.json({ driverId, history, count: history.length });
});

export default router;
