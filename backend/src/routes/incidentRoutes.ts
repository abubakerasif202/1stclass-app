import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireDriverIdentity, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';
import { Incident } from '../types';

const router = Router();

// 1. Driver Reports Incident
router.post('/driver/incidents', authenticate, requireDriverIdentity(), idempotencyMiddleware, async (req, res) => {
  const { driverId, vehicleId, jobId, category, description, severity, evidenceIds = [], latitude, longitude } = req.body;

  if (!driverId || !category || !description) {
    res.status(400).json({ error: 'Driver ID, category, and description are required' });
    return;
  }
  const driver = await db.get<any>('drivers', driverId);
  if (!driver) { res.status(404).json({ error: 'Driver not found' }); return; }
  if (vehicleId && vehicleId !== driver.currentVehicleId) {
    res.status(403).json({ error: 'Forbidden: Incident vehicle is not assigned to this driver' }); return;
  }
  const job = jobId ? await db.get<any>('jobs', jobId) : undefined;
  if (jobId && job?.assignedDriverId !== driverId) {
    res.status(403).json({ error: 'Forbidden: Incident job is not assigned to this driver' }); return;
  }
  const evidence = await Promise.all(Array.isArray(evidenceIds) ? evidenceIds.map(id => db.get<any>('evidenceMetadata', String(id))) : []);
  if (!Array.isArray(evidenceIds) || evidence.some(item => !item || item.driverId !== driverId || (jobId && item.jobId !== jobId))) {
    res.status(403).json({ error: 'Forbidden: Incident evidence is not owned by this driver/job' });
    return;
  }

  const id = randomUUID();
  const now = Date.now();

  const incident: Incident = {
    id,
    driverId,
    vehicleId: vehicleId || null,
    jobId: jobId || null,
    category,
    description,
    severity: severity || 'MEDIUM',
    evidenceIds,
    status: 'OPEN',
    latitude: latitude || null,
    longitude: longitude || null,
    reportedAt: now,
    acknowledgedAt: null,
    acknowledgedBy: null,
    resolvedAt: null,
    resolvedBy: null,
    opsNotes: ''
  };

  await db.transaction(async () => {
    await db.put('incidents', incident.id, incident);
    if (vehicleId && (category === 'BREAKDOWN' || category === 'ACCIDENT')) {
      const vehicle = await db.get<any>('vehicles', vehicleId);
      if (vehicle) { vehicle.status = 'DEFECT'; await db.put('vehicles', vehicleId, vehicle); }
    }
    await db.recordAuditAsync(
      driverId, driver.name || 'Driver', 'OPERATIONS', 'REPORT_INCIDENT', 'INCIDENT', incident.id,
      null, incident, `Reported ${category} incident: ${description}`
    );
  });

  realtimeBroadcaster.broadcast('incident.created', { incident });

  res.status(201).json({ success: true, incident });
});

// 2. Dispatcher: List Incidents
router.get('/incidents', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const { status, category } = req.query;
  let list = await db.list<Incident>('incidents');

  if (status && status !== 'ALL') {
    list = list.filter(i => i.status === status);
  }
  if (category && category !== 'ALL') {
    list = list.filter(i => i.category === category);
  }

  list.sort((a, b) => b.reportedAt - a.reportedAt);

  // Enrich with driver and vehicle name
  const enriched = await Promise.all(list.map(async inc => ({
    ...inc,
    driverName: (await db.get<any>('drivers', inc.driverId))?.name || inc.driverId,
    vehicleRego: inc.vehicleId ? ((await db.get<any>('vehicles', inc.vehicleId))?.rego || inc.vehicleId) : 'N/A',
    jobReference: inc.jobId ? ((await db.get<any>('jobs', inc.jobId))?.reference || inc.jobId) : 'No Job'
  })));

  res.json({ incidents: enriched, count: enriched.length });
});

// 3. Dispatcher: Acknowledge Incident
router.post('/incidents/:id/acknowledge', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const incident = await db.get<Incident>('incidents', id);
  if (!incident) {
    res.status(404).json({ error: `Incident ${id} not found` });
    return;
  }

  incident.status = 'ACKNOWLEDGED';
  incident.acknowledgedAt = Date.now();
  incident.acknowledgedBy = req.user?.name || 'Dispatcher';

  await db.transaction(async () => {
    await db.put('incidents', incident.id, incident);
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'ACKNOWLEDGE_INCIDENT', 'INCIDENT', incident.id, { status: 'OPEN' }, { status: 'ACKNOWLEDGED' });
  });

  realtimeBroadcaster.broadcast('incident.updated', { incident });

  res.json({ success: true, incident });
});

// 4. Dispatcher: Resolve Incident
router.post('/incidents/:id/resolve', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const { opsNotes } = req.body;

  const incident = await db.get<Incident>('incidents', id);
  if (!incident) {
    res.status(404).json({ error: `Incident ${id} not found` });
    return;
  }

  incident.status = 'RESOLVED';
  incident.resolvedAt = Date.now();
  incident.resolvedBy = req.user?.name || 'Dispatcher';
  incident.opsNotes = opsNotes || incident.opsNotes;

  await db.transaction(async () => {
    await db.put('incidents', incident.id, incident);
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'RESOLVE_INCIDENT', 'INCIDENT', incident.id, { status: 'ACKNOWLEDGED' }, { status: 'RESOLVED', opsNotes: incident.opsNotes });
  });

  realtimeBroadcaster.broadcast('incident.updated', { incident });

  res.json({ success: true, incident });
});

export default router;
