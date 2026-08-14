import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireDriverIdentity, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';
import { VehicleDefect } from '../types';

const router = Router();

// 1. List All Vehicles
router.get('/vehicles', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const { status } = req.query;
  let list = await db.list<any>('vehicles');

  if (status && status !== 'ALL') {
    list = list.filter(v => v.status === status);
  }

  const defects = await db.list<any>('vehicleDefects');
  const enriched = await Promise.all(list.map(async v => ({
    ...v,
    driverName: v.currentDriverId ? ((await db.get<any>('drivers', v.currentDriverId))?.name || v.currentDriverId) : 'Unassigned',
    activeDefects: defects.filter(d => d.vehicleId === v.id && d.status !== 'RESOLVED')
  })));

  res.json({ vehicles: enriched, count: enriched.length });
});

// 2. Single Vehicle Detail
router.get('/vehicles/:id', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const { id } = req.params;
  const vehicle = await db.get<any>('vehicles', id);
  if (!vehicle) {
    res.status(404).json({ error: `Vehicle ${id} not found` });
    return;
  }

  const defects = (await db.list<any>('vehicleDefects')).filter(d => d.vehicleId === vehicle.id);
  const driver = vehicle.currentDriverId ? await db.get<any>('drivers', vehicle.currentDriverId) : null;

  res.json({
    vehicle: {
      ...vehicle,
      driverName: driver?.name || 'Unassigned',
      defects
    }
  });
});

// 3. Driver Reports Pre-Start Defect
router.post('/driver/vehicle/:id/defect', authenticate, requireDriverIdentity(), idempotencyMiddleware, async (req, res) => {
  const { id } = req.params;
  const { driverId, shiftId, defectDescription, severity, evidenceIds = [] } = req.body;

  const vehicle = await db.get<any>('vehicles', id);
  if (!vehicle) {
    res.status(404).json({ error: `Vehicle ${id} not found` });
    return;
  }
  if (vehicle.currentDriverId && vehicle.currentDriverId !== driverId) {
    res.status(403).json({ error: 'Forbidden: Vehicle is assigned to another driver' });
    return;
  }
  const evidence = await Promise.all(Array.isArray(evidenceIds) ? evidenceIds.map(id => db.get<any>('evidenceMetadata', String(id))) : []);
  if (!Array.isArray(evidenceIds) || evidence.some(item => !item || item.driverId !== driverId)) {
    res.status(403).json({ error: 'Forbidden: Defect evidence is not owned by this driver' });
    return;
  }

  const defect: VehicleDefect = {
    id: randomUUID(),
    vehicleId: vehicle.id,
    driverId: driverId || 'DRV-UNKNOWN',
    shiftId: shiftId || 'SHIFT-UNKNOWN',
    defectDescription: defectDescription || 'Defect reported during pre-start check',
    severity: severity || 'MEDIUM',
    evidenceIds,
    status: 'NEW',
    reportedAt: Date.now(),
    resolvedAt: null,
    resolvedBy: null
  };

  vehicle.status = 'DEFECT';
  vehicle.activeDefectCount += 1;
  await db.transaction(async () => {
    await db.put('vehicleDefects', defect.id, defect);
    await db.put('vehicles', vehicle.id, vehicle);
    const driver = await db.get<any>('drivers', driverId || '');
    await db.recordAuditAsync(driverId || 'driver', driver?.name || 'Driver', 'OPERATIONS', 'REPORT_DEFECT',
      'DEFECT', defect.id, null, defect, `Reported defect for ${vehicle.rego}: ${defect.defectDescription}`);
  });

  realtimeBroadcaster.broadcast('vehicle.defect_reported', { defect, vehicle });

  res.status(201).json({ success: true, defect });
});

// 4. Dispatcher: Update Defect Status
router.post('/vehicles/:id/defects/:defectId/status', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS'), async (req: AuthenticatedRequest, res) => {
  const { id, defectId } = req.params;
  const { status } = req.body;

  const defect = await db.get<any>('vehicleDefects', defectId);
  if (!defect || defect.vehicleId !== id) {
    res.status(404).json({ error: `Defect ${defectId} not found for vehicle ${id}` });
    return;
  }

  const prev = defect.status;
  defect.status = status;
  let changedVehicle: any;
  if (status === 'RESOLVED') {
    defect.resolvedAt = Date.now();
    defect.resolvedBy = req.user?.name || 'Dispatcher';

    changedVehicle = await db.get<any>('vehicles', id);
    if (changedVehicle) {
      changedVehicle.activeDefectCount = Math.max(0, changedVehicle.activeDefectCount - 1);
      if (changedVehicle.activeDefectCount === 0) {
        changedVehicle.status = changedVehicle.currentDriverId ? 'ON_JOB' : 'AVAILABLE';
      }
    }
  }

  await db.transaction(async () => {
    await db.put('vehicleDefects', defect.id, defect);
    if (changedVehicle) await db.put('vehicles', changedVehicle.id, changedVehicle);
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'DEFECT_STATUS_UPDATE', 'DEFECT', defect.id, { status: prev }, { status: defect.status });
  });

  res.json({ success: true, defect });
});

export default router;
