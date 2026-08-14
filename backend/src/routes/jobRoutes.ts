import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireAssignedDriver, requireDriver, requireRole } from '../middleware/auth';
import { realtimeBroadcaster } from '../sse';
import { Job, JobStatus, Priority, TimelineEvent } from '../types';
import { pushNotifications } from '../push/PushNotificationService';

const router = Router();
const DRIVER_TRANSITIONS: Partial<Record<JobStatus, JobStatus[]>> = {
  ASSIGNED: ['ACCEPTED'],
  ACCEPTED: ['IN_PROGRESS'],
  IN_PROGRESS: ['AT_PICKUP'],
  AT_PICKUP: ['PICKED_UP'],
  PICKED_UP: ['EN_ROUTE_DELIVERY'],
  EN_ROUTE_DELIVERY: ['AT_DELIVERY'],
  AT_DELIVERY: ['DELIVERED'],
  DELIVERED: ['COMPLETED']
};

// 1. Get Jobs for Driver
router.get('/driver/jobs', authenticate, requireDriver, async (req: AuthenticatedRequest, res) => {
  const driverId = req.user!.driverId!;
  const allJobs = await db.list<Job>('jobs');
  const driverJobs = driverId
    ? allJobs.filter(j => j.assignedDriverId === driverId)
    : allJobs;

  res.json({
    jobs: driverJobs,
    total: driverJobs.length
  });
});

// 2. Driver Update Job Status
router.post('/driver/jobs/:id/status', authenticate, requireAssignedDriver, idempotencyMiddleware, async (req, res) => {
  const { id } = req.params;
  const { toStatus, changedAt, latitude, longitude, notes } = req.body;

  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job with ID ${id} not found` });
    return;
  }

  const previousStatus = job.status;
  const allowed = DRIVER_TRANSITIONS[previousStatus] || [];
  if (!allowed.includes(toStatus as JobStatus)) {
    res.status(409).json({
      error: 'Invalid job lifecycle transition',
      code: 'JOB_TRANSITION_INVALID',
      fromStatus: previousStatus,
      toStatus
    });
    return;
  }
  job.status = toStatus as JobStatus;
  job.revision += 1;
  job.serverUpdatedAt = Date.now();

  // Create timeline event
  const timelineEvent: TimelineEvent = {
    id: `tl-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
    type: toStatus,
    description: notes || `Driver updated status to ${toStatus}`,
    timestamp: changedAt || Date.now(),
    actor: job.assignedDriverId ? ((await db.get<any>('drivers', job.assignedDriverId))?.name || 'Driver') : 'Driver',
    lat: latitude,
    lng: longitude
  };
  job.timeline.push(timelineEvent);

  const committed = await db.transaction(async () => {
    const saved = await db.put('jobs', job.id, job, { expectedRevision: job.revision - 1 });
    if (!saved) return false;
    if (job.assignedVehicleId) {
      const vehicle = await db.get<any>('vehicles', job.assignedVehicleId);
      if (vehicle) {
        vehicle.status = toStatus === 'COMPLETED' || toStatus === 'CANCELLED' ? 'AVAILABLE' : 'ON_JOB';
        await db.put('vehicles', vehicle.id, vehicle);
      }
    }
    const actor = job.assignedDriverId ? await db.get<any>('drivers', job.assignedDriverId) : undefined;
    await db.recordAuditAsync(job.assignedDriverId || 'system', actor?.name || 'Driver', 'OPERATIONS',
      'JOB_STATUS_CHANGE', 'JOB', job.id, { status: previousStatus }, { status: job.status, revision: job.revision }, notes);
    return true;
  });
  if (!committed) { res.status(409).json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT' }); return; }

  // Broadcast realtime event
  realtimeBroadcaster.broadcast('job.status_changed', {
    jobId: job.id,
    reference: job.reference,
    fromStatus: previousStatus,
    toStatus: job.status,
    revision: job.revision,
    timestamp: Date.now(),
    job
  });

  res.json({
    success: true,
    jobId: job.id,
    status: job.status,
    revision: job.revision,
    serverUpdatedAt: job.serverUpdatedAt
  });
});

// 3. Driver Submit POD Completion
router.post('/driver/jobs/:id/pod', authenticate, requireAssignedDriver, idempotencyMiddleware, async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const { recipientName, evidenceIds, notes: driverNotes, completedAt, latitude, longitude } = req.body;

  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job with ID ${id} not found` });
    return;
  }
  if (job.status !== 'AT_DELIVERY' && job.status !== 'DELIVERED') {
    res.status(409).json({ error: 'POD is only accepted at delivery', code: 'POD_STATE_INVALID' });
    return;
  }
  const evidence = Array.isArray(evidenceIds)
    ? await Promise.all(evidenceIds.map((evidenceId: string) => db.get<any>('evidenceMetadata', evidenceId)))
    : [];
  if (evidence.length === 0 || evidence.some(item => !item || item.jobId !== id || item.driverId !== req.user!.driverId)) {
    res.status(400).json({ error: 'POD evidence is missing or does not belong to this job', code: 'POD_EVIDENCE_INVALID' });
    return;
  }
  const signature = evidence.find(item => item?.type === 'DELIVERY_SIGNATURE');
  const photos = evidence.filter(item => item?.type === 'DELIVERY_PHOTO');
  if (!signature || photos.length === 0) {
    res.status(400).json({ error: 'POD requires a delivery signature and photo', code: 'POD_EVIDENCE_INCOMPLETE' });
    return;
  }

  job.pod = {
    recipientName: recipientName || 'Authorized Consignee',
    signatureEvidenceId: signature.evidenceId,
    photoEvidenceIds: photos.map(item => item!.evidenceId),
    driverNotes: driverNotes || '',
    completedAt: completedAt || Date.now(),
    latitude,
    longitude,
    status: 'COMPLETE'
  };

  const previousStatus = job.status;
  job.status = 'COMPLETED';
  job.revision += 1;
  job.serverUpdatedAt = Date.now();

  const timelineEvent: TimelineEvent = {
    id: `tl-${Date.now()}`,
    type: 'COMPLETED',
    description: `POD completed by ${job.pod.recipientName}. Delivery complete.`,
    timestamp: completedAt || Date.now(),
    actor: job.assignedDriverId ? ((await db.get<any>('drivers', job.assignedDriverId))?.name || 'Driver') : 'Driver',
    lat: latitude,
    lng: longitude
  };
  job.timeline.push(timelineEvent);

  const committed = await db.transaction(async () => {
    const saved = await db.put('jobs', job.id, job, { expectedRevision: job.revision - 1 });
    if (!saved) return false;
    if (job.assignedVehicleId) {
      const vehicle = await db.get<any>('vehicles', job.assignedVehicleId);
      if (vehicle) { vehicle.status = 'AVAILABLE'; await db.put('vehicles', vehicle.id, vehicle); }
    }
    const actor = job.assignedDriverId ? await db.get<any>('drivers', job.assignedDriverId) : undefined;
    await db.recordAuditAsync(job.assignedDriverId || 'system', actor?.name || 'Driver', 'OPERATIONS',
      'POD_COMPLETED', 'JOB', job.id, { status: previousStatus }, { status: 'COMPLETED', pod: job.pod });
    return true;
  });
  if (!committed) { res.status(409).json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT' }); return; }

  realtimeBroadcaster.broadcast('pod.completed', {
    jobId: job.id,
    reference: job.reference,
    recipientName: job.pod.recipientName,
    completedAt: job.pod.completedAt,
    job
  });

  res.json({
    success: true,
    jobId: job.id,
    status: 'COMPLETED',
    revision: job.revision,
    pod: job.pod
  });
});

// 4. Dispatcher: List All Jobs (with Search & Filters)
router.get('/jobs', authenticate, requireRole('ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'), async (req, res) => {
  const { status, priority, driverId, vehicleId, search } = req.query;
  let jobs = await db.list<Job>('jobs');

  if (status && status !== 'ALL') {
    jobs = jobs.filter(j => j.status === status);
  }
  if (priority && priority !== 'ALL') {
    jobs = jobs.filter(j => j.priority === priority);
  }
  if (driverId) {
    jobs = jobs.filter(j => j.assignedDriverId === driverId);
  }
  if (vehicleId) {
    jobs = jobs.filter(j => j.assignedVehicleId === vehicleId);
  }
  if (search && typeof search === 'string' && search.trim().length > 0) {
    const query = search.toLowerCase().trim();
    jobs = jobs.filter(j =>
      j.reference.toLowerCase().includes(query) ||
      j.pickup.companyName.toLowerCase().includes(query) ||
      j.pickup.suburb.toLowerCase().includes(query) ||
      j.delivery.companyName.toLowerCase().includes(query) ||
      j.delivery.suburb.toLowerCase().includes(query) ||
      j.freightDescription.toLowerCase().includes(query)
    );
  }

  // Sort by serverUpdatedAt descending
  jobs.sort((a, b) => b.serverUpdatedAt - a.serverUpdatedAt);

  res.json({
    jobs,
    total: jobs.length
  });
});

// 5. Dispatcher: Get Single Job Detail
router.get('/jobs/:id', authenticate, async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job ${id} not found` });
    return;
  }
  if (req.user?.role === 'DRIVER' && job.assignedDriverId !== req.user.driverId) {
    res.status(403).json({ error: 'Forbidden: Job is not assigned to this driver' });
    return;
  }
  res.json({ job });
});

// 6. Dispatcher: Create Job
router.post('/jobs/create', authenticate, requireRole('ADMIN', 'DISPATCHER'), idempotencyMiddleware, async (req: AuthenticatedRequest, res) => {
  const {
    reference,
    priority,
    assignedDriverId,
    assignedVehicleId,
    pickup,
    delivery,
    pickupWindowStart,
    pickupWindowEnd,
    deliveryWindowStart,
    deliveryWindowEnd,
    freightDescription,
    itemCount,
    specialInstructions,
    dangerousGoods
  } = req.body;

  if (!reference || !pickup || !delivery || !freightDescription) {
    res.status(400).json({ error: 'Missing required job parameters (reference, pickup, delivery, freight)' });
    return;
  }
  if (!['LOW', 'NORMAL', 'HIGH', 'URGENT'].includes(priority || 'NORMAL')) {
    res.status(400).json({ error: 'Invalid job priority' }); return;
  }
  const validLocation = (value: any) => value && typeof value.companyName === 'string' &&
    typeof value.address === 'string' && value.companyName.trim() && value.address.trim() &&
    (value.lat === null || (typeof value.lat === 'number' && value.lat >= -90 && value.lat <= 90)) &&
    (value.lng === null || (typeof value.lng === 'number' && value.lng >= -180 && value.lng <= 180));
  if (!validLocation(pickup) || !validLocation(delivery)) {
    res.status(400).json({ error: 'Pickup and delivery locations are invalid' }); return;
  }
  const assignedDriver = assignedDriverId ? await db.get<any>('drivers', assignedDriverId) : null;
  const assignedVehicle = assignedVehicleId ? await db.get<any>('vehicles', assignedVehicleId) : null;
  if (assignedDriverId && (!assignedDriver || !assignedDriver.active)) {
    res.status(400).json({ error: 'Assigned driver does not exist or is disabled' }); return;
  }
  if (assignedVehicleId && !assignedVehicle) {
    res.status(400).json({ error: 'Assigned vehicle does not exist' }); return;
  }
  if (assignedVehicle?.currentDriverId && assignedVehicle.currentDriverId !== assignedDriverId) {
    res.status(409).json({ error: 'Assigned vehicle is already allocated' }); return;
  }

  const id = randomUUID();
  const now = Date.now();
  const initialStatus: JobStatus = assignedDriverId ? 'ASSIGNED' : 'ASSIGNED';

  const newJob: Job = {
    id,
    reference: reference.toUpperCase(),
    status: initialStatus,
    priority: (priority as Priority) || 'NORMAL',
    assignedDriverId: assignedDriverId || null,
    assignedVehicleId: assignedVehicleId || null,
    pickup,
    delivery,
    pickupWindowStart: pickupWindowStart || '08:00',
    pickupWindowEnd: pickupWindowEnd || '10:00',
    deliveryWindowStart: deliveryWindowStart || '12:00',
    deliveryWindowEnd: deliveryWindowEnd || '14:00',
    freightDescription,
    itemCount: Number(itemCount) || 1,
    specialInstructions: specialInstructions || '',
    dangerousGoods: !!dangerousGoods,
    revision: 1,
    serverUpdatedAt: now,
    timeline: [
      {
        id: `tl-${now}`,
        type: 'CREATED',
        description: `Job created by ${req.user?.name || 'Dispatcher'}`,
        timestamp: now,
        actor: req.user?.name || 'Dispatcher'
      }
    ],
    pod: null,
    createdAt: now
  };

  if (assignedDriverId) {
    const driver = await db.get<any>('drivers', assignedDriverId);
    newJob.timeline.push({
      id: `tl-${now}-assigned`,
      type: 'ASSIGNED',
      description: `Job assigned to ${driver?.name || assignedDriverId}`,
      timestamp: now,
      actor: req.user?.name || 'Dispatcher'
    });
  }

  await db.transaction(async () => {
    await db.put('jobs', newJob.id, newJob);
    if (assignedDriver) {
      assignedDriver.activeJobId = newJob.id;
      if (assignedVehicleId) assignedDriver.currentVehicleId = assignedVehicleId;
      await db.put('drivers', assignedDriver.id, assignedDriver);
    }
    if (assignedVehicle) {
      assignedVehicle.currentDriverId = assignedDriverId || null;
      assignedVehicle.status = 'ON_JOB';
      await db.put('vehicles', assignedVehicle.id, assignedVehicle);
    }
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'CREATE_JOB', 'JOB', newJob.id, null, newJob, `Created new job ${newJob.reference}`);
  });

  realtimeBroadcaster.broadcast('job.created', { job: newJob });
  if (newJob.assignedDriverId) {
    await pushNotifications.sendToDriver(newJob.assignedDriverId, 'NEW_JOB', {
      jobId: newJob.id, reference: newJob.reference
    });
  }

  res.status(201).json({ success: true, job: newJob });
});

// 7. Dispatcher: Update Job Details (Revision handling)
router.put('/jobs/:id', authenticate, requireRole('ADMIN', 'DISPATCHER'), async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job ${id} not found` });
    return;
  }
  if (job.status === 'COMPLETED' || job.status === 'CANCELLED') {
    res.status(409).json({
      error: 'Completed or cancelled jobs cannot be edited',
      code: 'JOB_TERMINAL_STATE',
      currentRevision: job.revision
    });
    return;
  }

  const previous = JSON.parse(JSON.stringify(job));
  const requestedRevision = Number(req.body.expectedRevision ?? req.body.revision);
  if (!Number.isInteger(requestedRevision) || requestedRevision !== job.revision) {
    res.status(409).json({
      error: 'Job was updated by another session',
      code: 'JOB_REVISION_CONFLICT',
      currentRevision: job.revision
    });
    return;
  }
  const {
    priority,
    pickup,
    delivery,
    pickupWindowStart,
    pickupWindowEnd,
    deliveryWindowStart,
    deliveryWindowEnd,
    freightDescription,
    itemCount,
    specialInstructions,
    dangerousGoods
  } = req.body;

  const isWindow = (value: unknown) => typeof value === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
  const isLocation = (value: unknown) => {
    if (!value || typeof value !== 'object') return false;
    const location = value as Record<string, unknown>;
    return typeof location.address === 'string' && typeof location.suburb === 'string' &&
      typeof location.companyName === 'string' && typeof location.contactName === 'string' &&
      typeof location.contactPhone === 'string' &&
      (location.lat === null || (typeof location.lat === 'number' && Number.isFinite(location.lat) && location.lat >= -90 && location.lat <= 90)) &&
      (location.lng === null || (typeof location.lng === 'number' && Number.isFinite(location.lng) && location.lng >= -180 && location.lng <= 180));
  };
  if (
    (priority !== undefined && !['LOW', 'NORMAL', 'HIGH', 'URGENT'].includes(priority)) ||
    (pickup !== undefined && !isLocation(pickup)) || (delivery !== undefined && !isLocation(delivery)) ||
    (pickupWindowStart !== undefined && !isWindow(pickupWindowStart)) || (pickupWindowEnd !== undefined && !isWindow(pickupWindowEnd)) ||
    (deliveryWindowStart !== undefined && !isWindow(deliveryWindowStart)) || (deliveryWindowEnd !== undefined && !isWindow(deliveryWindowEnd)) ||
    (freightDescription !== undefined && (typeof freightDescription !== 'string' || !freightDescription.trim())) ||
    (itemCount !== undefined && (!Number.isInteger(Number(itemCount)) || Number(itemCount) < 1)) ||
    (specialInstructions !== undefined && typeof specialInstructions !== 'string') ||
    (dangerousGoods !== undefined && typeof dangerousGoods !== 'boolean')
  ) {
    res.status(400).json({ error: 'Invalid job edit payload', code: 'JOB_EDIT_INVALID' });
    return;
  }

  const updates = {
    priority,
    pickup,
    delivery,
    pickupWindowStart,
    pickupWindowEnd,
    deliveryWindowStart,
    deliveryWindowEnd,
    freightDescription,
    itemCount: itemCount === undefined ? undefined : Number(itemCount),
    specialInstructions,
    dangerousGoods
  };
  const changedFields = Object.entries(updates)
    .filter(([field, value]) => value !== undefined && JSON.stringify(job[field as keyof typeof job]) !== JSON.stringify(value))
    .map(([field]) => field);

  if (priority !== undefined) job.priority = priority;
  if (pickup !== undefined) job.pickup = pickup;
  if (delivery !== undefined) job.delivery = delivery;
  if (pickupWindowStart !== undefined) job.pickupWindowStart = pickupWindowStart;
  if (pickupWindowEnd !== undefined) job.pickupWindowEnd = pickupWindowEnd;
  if (deliveryWindowStart !== undefined) job.deliveryWindowStart = deliveryWindowStart;
  if (deliveryWindowEnd !== undefined) job.deliveryWindowEnd = deliveryWindowEnd;
  if (freightDescription !== undefined) job.freightDescription = freightDescription;
  if (itemCount !== undefined) job.itemCount = Number(itemCount);
  if (specialInstructions !== undefined) job.specialInstructions = specialInstructions;
  if (dangerousGoods !== undefined) job.dangerousGoods = dangerousGoods;

  job.revision += 1;
  job.serverUpdatedAt = Date.now();

  job.timeline.push({
    id: `tl-${Date.now()}`,
    type: 'UPDATED',
    description: `Job details updated by ${req.user?.name || 'Dispatcher'} (Rev ${job.revision})`,
    timestamp: Date.now(),
    actor: req.user?.name || 'Dispatcher'
  });

  const committed = await db.transaction(async () => {
    const saved = await db.put('jobs', job.id, job, { expectedRevision: requestedRevision });
    if (!saved) return false;
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'UPDATE_JOB', 'JOB', job.id, previous, job,
      `Updated job ${job.reference} to revision ${job.revision}. Changed fields: ${changedFields.join(', ') || 'none'}.`);
    return true;
  });
  if (!committed) {
    const current = await db.get<Job>('jobs', id);
    res.status(409).json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT', currentRevision: current?.revision });
    return;
  }

  realtimeBroadcaster.broadcast('job.updated', { job });
  if (job.assignedDriverId) {
    void pushNotifications.sendToDriver(job.assignedDriverId, 'JOB_UPDATED', {
      jobId: job.id, reference: job.reference, revision: String(job.revision)
    }).catch(error => {
      if (process.env.NODE_ENV !== 'test') {
        console.error(JSON.stringify({ event: 'job_update_notification_failed', jobId: job.id, error: error instanceof Error ? error.name : 'Error' }));
      }
    });
  }

  res.json({ success: true, job });
});

// 8. Dispatcher: Reassign Job
router.post('/jobs/:id/reassign', authenticate, requireRole('ADMIN', 'DISPATCHER'), idempotencyMiddleware, async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const { newDriverId, newVehicleId, reason } = req.body;

  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job ${id} not found` });
    return;
  }

  const previousDriverId = job.assignedDriverId;
  const previousVehicleId = job.assignedVehicleId;
  const newDriver = newDriverId ? await db.get<any>('drivers', newDriverId) : null;
  const newVehicle = newVehicleId ? await db.get<any>('vehicles', newVehicleId) : null;
  if (!newDriverId || !newDriver || !newDriver.active) {
    res.status(400).json({ error: 'A valid active driver is required' }); return;
  }
  if (newVehicleId && !newVehicle) {
    res.status(400).json({ error: 'Reassignment vehicle does not exist' }); return;
  }
  if (newVehicle?.currentDriverId && newVehicle.currentDriverId !== newDriverId && newVehicle.id !== job.assignedVehicleId) {
    res.status(409).json({ error: 'Reassignment vehicle is already allocated' }); return;
  }

  job.assignedDriverId = newDriverId || null;
  job.assignedVehicleId = newVehicleId || null;
  job.revision += 1;
  job.serverUpdatedAt = Date.now();
  const previousDriver = previousDriverId ? await db.get<any>('drivers', previousDriverId) : null;
  if (previousDriver?.activeJobId === job.id) previousDriver.activeJobId = null;
  const previousVehicle = previousVehicleId ? await db.get<any>('vehicles', previousVehicleId) : null;
  if (previousVehicle) {
    previousVehicle.currentDriverId = null;
    previousVehicle.status = 'AVAILABLE';
  }
  if (newDriver) {
    newDriver.activeJobId = job.id;
    newDriver.currentVehicleId = newVehicleId || null;
  }
  if (newVehicle) {
    newVehicle.currentDriverId = newDriverId || null;
    newVehicle.status = 'ON_JOB';
  }

  const desc = `Reassigned from ${previousDriverId || 'Unassigned'} to ${newDriver?.name || newDriverId} (${newVehicle?.rego || 'No vehicle'}). Reason: ${reason || 'Operational adjustment'}`;
  job.timeline.push({
    id: `tl-${Date.now()}`,
    type: 'REASSIGNED',
    description: desc,
    timestamp: Date.now(),
    actor: req.user?.name || 'Dispatcher'
  });

  const committed = await db.transaction(async () => {
    const saved = await db.put('jobs', job.id, job, { expectedRevision: job.revision - 1 });
    if (!saved) return false;
    if (previousDriver) await db.put('drivers', previousDriver.id, previousDriver);
    if (previousVehicle) await db.put('vehicles', previousVehicle.id, previousVehicle);
    if (newDriver) await db.put('drivers', newDriver.id, newDriver);
    if (newVehicle) await db.put('vehicles', newVehicle.id, newVehicle);
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'REASSIGN_JOB', 'JOB', job.id, { assignedDriverId: previousDriverId },
      { assignedDriverId: newDriverId, assignedVehicleId: newVehicleId, revision: job.revision }, desc);
    return true;
  });
  if (!committed) { res.status(409).json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT' }); return; }

  realtimeBroadcaster.broadcast('job.reassigned', {
    jobId: job.id,
    previousDriverId,
    newDriverId,
    job
  });
  if (previousDriverId) {
    await pushNotifications.sendToDriver(previousDriverId, 'JOB_UPDATED', {
      jobId: job.id, reference: job.reference, assignment: 'removed'
    });
  }
  if (newDriverId) {
    await pushNotifications.sendToDriver(newDriverId, 'NEW_JOB', {
      jobId: job.id, reference: job.reference
    });
  }

  res.json({ success: true, job });
});

// 9. Dispatcher: Cancel Job
router.post('/jobs/:id/cancel', authenticate, requireRole('ADMIN', 'DISPATCHER'), idempotencyMiddleware, async (req: AuthenticatedRequest, res) => {
  const { id } = req.params;
  const { reason } = req.body;

  const job = await db.get<Job>('jobs', id);
  if (!job) {
    res.status(404).json({ error: `Job ${id} not found` });
    return;
  }

  const previousStatus = job.status;
  job.status = 'CANCELLED';
  job.cancellationReason = reason || 'Cancelled by Operations Dispatch';
  job.cancelledAt = Date.now();
  job.cancelledBy = req.user?.name || 'Dispatcher';
  job.revision += 1;
  job.serverUpdatedAt = Date.now();

  job.timeline.push({
    id: `tl-${Date.now()}`,
    type: 'CANCELLED',
    description: `Job cancelled: ${job.cancellationReason}`,
    timestamp: Date.now(),
    actor: req.user?.name || 'Dispatcher'
  });

  const committed = await db.transaction(async () => {
    const saved = await db.put('jobs', job.id, job, { expectedRevision: job.revision - 1 });
    if (!saved) return false;
    if (job.assignedVehicleId) {
      const vehicle = await db.get<any>('vehicles', job.assignedVehicleId);
      if (vehicle) { vehicle.status = 'AVAILABLE'; await db.put('vehicles', vehicle.id, vehicle); }
    }
    if (job.assignedDriverId) {
      const driver = await db.get<any>('drivers', job.assignedDriverId);
      if (driver?.activeJobId === job.id) { driver.activeJobId = null; await db.put('drivers', driver.id, driver); }
    }
    await db.recordAuditAsync(req.user?.id || 'admin', req.user?.name || 'Dispatcher', req.user?.role || 'DISPATCHER',
      'CANCEL_JOB', 'JOB', job.id, { status: previousStatus }, { status: 'CANCELLED', reason: job.cancellationReason },
      `Job cancelled: ${job.cancellationReason}`);
    return true;
  });
  if (!committed) { res.status(409).json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT' }); return; }

  realtimeBroadcaster.broadcast('job.cancelled', {
    jobId: job.id,
    reason: job.cancellationReason,
    job
  });
  if (job.assignedDriverId) {
    await pushNotifications.sendToDriver(job.assignedDriverId, 'JOB_CANCELLED', {
      jobId: job.id, reference: job.reference
    });
  }

  res.json({ success: true, job });
});

export default router;
