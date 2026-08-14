import request from 'supertest';
import jwt from 'jsonwebtoken';
import { createApp } from '../app';
import { db } from '../db';
import { generateToken } from '../middleware/auth';
import { AuditLogEntry, Job } from '../types';

const app = createApp();
const dispatcher = request.agent(app);

describe('1st Class Express Transport API Backend Tests', () => {
  let dispatcherCsrf: string = '';
  let driverToken: string = '';
  let driverRefreshToken: string = '';
  const podEvidenceIds: string[] = [];

  beforeAll(() => {
    db.seed();
  });

  test('1. GET /v1/health returns UP status', async () => {
    const res = await request(app).get('/v1/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('UP');
    expect(res.body).toEqual({ status: 'UP' });
  });

  test('2. Driver login succeeds with valid PIN', async () => {
    const requestedAt = Date.now();
    const res = await dispatcher
      .post('/v1/auth/driver/login')
      .send({ driverId: 'DRV-101', pin: '8841', deviceId: 'pixel-7', appVersion: '1.0.0' });

    expect(res.status).toBe(200);
    expect(res.body.csrfToken).toBeUndefined();
    expect(res.body.driverId).toBe('DRV-101');
    expect(res.body.expiresAt).toBeGreaterThanOrEqual(requestedAt + 15 * 60 * 1000);
    expect(res.body.expiresAt).toBeLessThanOrEqual(Date.now() + 15 * 60 * 1000);
    expect(jwt.decode(res.body.token)).toMatchObject({ type: 'access', role: 'DRIVER' });
    expect(jwt.decode(res.body.refreshToken)).toMatchObject({ type: 'refresh', driverId: 'DRV-101' });
    driverToken = res.body.token;
    driverRefreshToken = res.body.refreshToken;
  });

  test('3. Driver login fails with invalid PIN', async () => {
    const res = await request(app)
      .post('/v1/auth/driver/login')
      .send({ driverId: 'DRV-101', pin: '0000' });

    expect(res.status).toBe(401);
  });

  test('4. Dispatcher login succeeds with valid password and returns role', async () => {
    const requestedAt = Date.now();
    const res = await dispatcher
      .post('/v1/auth/dispatch/login')
      .send({ email: 'ops@1stclassexpress.com.au', password: 'Dispatch2026!' });

    expect(res.status).toBe(200);
    expect(res.body.token).toBeUndefined();
    expect(res.body.user.role).toBe('ADMIN');
    expect(res.body.expiresAt).toBeGreaterThanOrEqual(requestedAt + 15 * 60 * 1000);
    expect(res.body.expiresAt).toBeLessThanOrEqual(Date.now() + 15 * 60 * 1000);
    expect(res.headers['set-cookie']).toEqual(expect.arrayContaining([
      expect.stringContaining('tms_dispatch_session=')
    ]));
    expect(res.headers['set-cookie']?.[0]).toContain('HttpOnly');
    expect(res.headers['set-cookie']?.[0]).toContain('SameSite=Strict');
    dispatcherCsrf = res.body.csrfToken;
  });

  test('5. Dispatcher cookie mutations require a matching CSRF token', async () => {
    const res = await dispatcher.post('/v1/jobs/create').send({});

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('Forbidden: CSRF validation failed');
  });

  test('6. Dispatcher creates new job', async () => {
    const res = await dispatcher
      .post('/v1/jobs/create')
      .set('X-CSRF-Token', dispatcherCsrf)
      .set('X-Idempotency-Key', 'create-job-999')
      .send({
        reference: '1CE-TEST-999',
        priority: 'URGENT',
        assignedDriverId: 'DRV-101',
        assignedVehicleId: 'VH-101',
        pickup: {
          address: '10 Depot Lane',
          suburb: 'Laverton North VIC',
          lat: -37.83,
          lng: 144.78,
          companyName: 'Laverton DC',
          contactName: 'Dock Manager',
          contactPhone: '03 9111 0000'
        },
        delivery: {
          address: '50 Freight Way',
          suburb: 'Dandenong South VIC',
          lat: -38.01,
          lng: 145.21,
          companyName: 'South Freight Logistics',
          contactName: 'Receiving Dock',
          contactPhone: '03 9222 0000'
        },
        pickupWindowStart: '08:00',
        pickupWindowEnd: '10:00',
        deliveryWindowStart: '12:00',
        deliveryWindowEnd: '14:00',
        freightDescription: '2 Pallets Industrial Tools',
        itemCount: 2,
        specialInstructions: 'Tail lift required'
      });

    expect(res.status).toBe(201);
    expect(res.body.job.reference).toBe('1CE-TEST-999');
    expect(res.body.job.revision).toBe(1);
    expect(res.body.job.status).toBe('ASSIGNED');
  });

  test('6. Driver updates job status through lifecycle and increments revision', async () => {
    const jobId = 'job-101';
    const res = await request(app)
      .post(`/v1/driver/jobs/${jobId}/status`)
      .set('Authorization', `Bearer ${driverToken}`)
      .send({
        toStatus: 'AT_DELIVERY',
        changedAt: Date.now(),
        latitude: -37.81,
        longitude: 144.91,
        notes: 'Arrived at West Melbourne receiving dock'
      });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('AT_DELIVERY');
    expect(res.body.revision).toBeGreaterThan(1);

    const job = db.jobs.get(jobId);
    expect(job?.status).toBe('AT_DELIVERY');
    expect(job?.timeline.some(t => t.type === 'AT_DELIVERY')).toBe(true);
  });

  test('7. Driver submits POD and completes job', async () => {
    const jobId = 'job-101';
    for (const [index, type] of ['DELIVERY_SIGNATURE', 'DELIVERY_PHOTO'].entries()) {
      const upload = await request(app)
        .post('/v1/driver/evidence')
        .set('Authorization', `Bearer ${driverToken}`)
        .set('X-Idempotency-Key', `pod-evidence-${index}`)
        .field('metadata', JSON.stringify({ evidenceId: `pod-${index}`, jobId, driverId: 'DRV-101', type }))
        .attach('file', Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, index]), {
          filename: `pod-${index}.png`, contentType: 'image/png'
        });
      expect(upload.status).toBe(201);
      podEvidenceIds.push(upload.body.evidenceId);
    }
    const res = await request(app)
      .post(`/v1/driver/jobs/${jobId}/pod`)
      .set('Authorization', `Bearer ${driverToken}`)
      .send({
        recipientName: 'Robert Vance',
        evidenceIds: podEvidenceIds,
        notes: 'Signed at Receiving Dock A',
        completedAt: Date.now(),
        latitude: -37.81,
        longitude: 144.91
      });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('COMPLETED');
    expect(res.body.pod.recipientName).toBe('Robert Vance');

    const job = db.jobs.get(jobId);
    expect(job?.status).toBe('COMPLETED');
    expect(job?.pod).toBeDefined();
  });

  test('8. Dispatcher reassigns job to another driver', async () => {
    const jobId = 'job-104';
    const res = await dispatcher
      .post(`/v1/jobs/${jobId}/reassign`)
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({
        newDriverId: 'DRV-102',
        newVehicleId: 'VH-102',
        reason: 'Shift coverage'
      });

    expect(res.status).toBe(200);
    expect(res.body.job.assignedDriverId).toBe('DRV-102');
    expect(res.body.job.assignedVehicleId).toBe('VH-102');
    expect(res.body.job.revision).toBeGreaterThan(1);
  });

  test('9. Ingests batch location telemetry and updates fleet latest locations', async () => {
    const res = await request(app)
      .post('/v1/driver/locations')
      .set('Authorization', `Bearer ${driverToken}`)
      .send({
        points: [
          {
            driverId: 'DRV-101',
            vehicleId: 'VH-101',
            jobId: 'job-101',
            latitude: -37.815,
            longitude: 144.92,
            accuracyMeters: 4.0,
            speedMetersPerSecond: 16.5,
            bearingDegrees: 190.0,
            batteryLevel: 85,
            networkState: 'CELLULAR',
            recordedAt: Date.now()
          }
        ]
      });

    expect(res.status).toBe(200);
    expect(res.body.acknowledgedCount).toBe(1);

    const fleetRes = await dispatcher.get('/v1/fleet/locations/latest');
    expect(fleetRes.status).toBe(200);
    const drv1 = fleetRes.body.fleet.find((f: any) => f.driverId === 'DRV-101');
    expect(drv1).toBeDefined();
    expect(drv1.latitude).toBe(-37.815);
    expect(drv1.speedKmh).toBe(59);
  });

  test('10. Driver reports incident and dispatcher resolves it', async () => {
    const incRes = await request(app)
      .post('/v1/driver/incidents')
      .set('Authorization', `Bearer ${driverToken}`)
      .send({
        driverId: 'DRV-101',
        vehicleId: 'VH-101',
        jobId: 'job-101',
        category: 'DELAY',
        description: 'Heavy congestion on M5 East due to earlier accident.',
        severity: 'MEDIUM',
        latitude: -33.94,
        longitude: 151.12
      });

    expect(incRes.status).toBe(201);
    const incId = incRes.body.incident.id;

    const resolveRes = await dispatcher
      .post(`/v1/incidents/${incId}/resolve`)
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({ opsNotes: 'Traffic cleared, delivery window adjusted.' });

    expect(resolveRes.status).toBe(200);
    expect(resolveRes.body.incident.status).toBe('RESOLVED');
    expect(resolveRes.body.incident.opsNotes).toContain('Traffic cleared');
  });

  test('11. Driver reports pre-start defect and vehicle status updates to DEFECT', async () => {
    const res = await request(app)
      .post('/v1/driver/vehicle/VH-101/defect')
      .set('Authorization', `Bearer ${driverToken}`)
      .send({
        driverId: 'DRV-101',
        shiftId: 'shift-101-today',
        defectDescription: 'Windscreen wiper blade split on passenger side.',
        severity: 'MEDIUM'
      });

    expect(res.status).toBe(201);
    const vehicle = db.vehicles.get('VH-101');
    expect(vehicle?.status).toBe('DEFECT');
    expect(vehicle?.activeDefectCount).toBeGreaterThan(0);
  });

  test('12. Dispatcher sends message to driver', async () => {
    const res = await dispatcher
      .post('/v1/messages/send')
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({
        driverId: 'DRV-101',
        jobId: 'job-101',
        category: 'URGENT',
        content: 'Please confirm dock entry code before proceeding.',
        isUrgent: true
      });

    expect(res.status).toBe(201);
    expect(res.body.message.content).toContain('dock entry code');
    expect(res.body.message.isUrgent).toBe(true);
  });

  test('13. Audit logs record critical actions', async () => {
    const res = await dispatcher.get('/v1/audit/logs');

    expect(res.status).toBe(200);
    expect(res.body.logs.length).toBeGreaterThan(0);
  });

  test('14. Idempotency middleware deduplicates repeated requests with identical key', async () => {
    const idempotencyKey = 'unique-key-abc-123';
    const payload = {
      driverId: 'DRV-101',
      vehicleId: 'VH-101',
      jobId: 'job-101',
      category: 'OTHER',
      description: 'Test duplicate submission',
      severity: 'LOW'
    };

    const res1 = await request(app)
      .post('/v1/driver/incidents')
      .set('Authorization', `Bearer ${driverToken}`)
      .set('X-Idempotency-Key', idempotencyKey)
      .send(payload);

    const res2 = await request(app)
      .post('/v1/driver/incidents')
      .set('Authorization', `Bearer ${driverToken}`)
      .set('X-Idempotency-Key', idempotencyKey)
      .send(payload);

    expect(res1.status).toBe(201);
    expect(res2.status).toBe(201);
    expect(res2.body.incident.id).toBe(res1.body.incident.id);
  });

  test('15. Unauthenticated business access is rejected', async () => {
    expect((await request(app).get('/v1/jobs')).status).toBe(401);
    expect((await request(app).post('/v1/driver/locations').send({ points: [] })).status).toBe(401);
    expect((await request(app).get(`/v1/evidence/${podEvidenceIds[0]}`)).status).toBe(401);
  });

  test('16. Driver IDOR is rejected for another drivers job and telemetry', async () => {
    const login = await request(app).post('/v1/auth/driver/login')
      .send({ driverId: 'DRV-102', pin: '8842' });
    const otherToken = login.body.token;
    expect((await request(app).post('/v1/driver/jobs/job-101/status')
      .set('Authorization', `Bearer ${otherToken}`).send({ toStatus: 'ACCEPTED' })).status).toBe(403);
    expect((await request(app).post('/v1/driver/locations')
      .set('Authorization', `Bearer ${otherToken}`)
      .send({ points: [{ driverId: 'DRV-101', latitude: -37, longitude: 144 }] })).status).toBe(403);
  });

  test('17. Refresh rotation revokes the old refresh token', async () => {
    const first = await request(app).post('/v1/auth/driver/refresh').send({ refreshToken: driverRefreshToken });
    expect(first.status).toBe(200);
    const replay = await request(app).post('/v1/auth/driver/refresh').send({ refreshToken: driverRefreshToken });
    expect(replay.status).toBe(401);
  });

  test('18. Credential hashes are never returned', async () => {
    const profile = await request(app).get('/v1/driver/profile')
      .set('Authorization', `Bearer ${driverToken}`);
    expect(profile.body.driver.pinHash).toBeUndefined();
    expect(JSON.stringify(profile.body)).not.toContain('8841');
  });

  test('19. Dispatcher edit accepts expected revision and audits changed field names', async () => {
    const before = db.jobs.get('job-104')!;
    const expectedRevision = before.revision;
    const result = await dispatcher.put('/v1/jobs/job-104')
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({ expectedRevision, priority: 'HIGH', specialInstructions: 'Updated by dispatch' });

    expect(result.status).toBe(200);
    expect(result.body.job.revision).toBe(expectedRevision + 1);
    expect(result.body.job.priority).toBe('HIGH');
    expect(result.body.job.specialInstructions).toBe('Updated by dispatch');
    const audit = (await db.list<AuditLogEntry>('auditLogs')).find(entry => entry.action === 'UPDATE_JOB' && entry.entityId === 'job-104');
    expect(audit?.notes).toContain('priority, specialInstructions');
  });

  test('20. Stale job revision is rejected with a structured conflict', async () => {
    const result = await dispatcher.put('/v1/jobs/job-104')
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({ expectedRevision: 1, specialInstructions: 'stale update' });
    expect(result.status).toBe(409);
    expect(result.body.code).toBe('JOB_REVISION_CONFLICT');
  });

  test('21. Completed or cancelled jobs reject dispatcher edits with terminal-state conflict metadata', async () => {
    const terminal = await db.get<Job>('jobs', 'job-104');
    expect(terminal).toBeDefined();
    const terminalRevision = terminal!.revision;
    await db.put('jobs', terminal!.id, { ...terminal!, status: 'CANCELLED' }, { expectedRevision: terminalRevision });

    const result = await dispatcher.put('/v1/jobs/job-104')
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({ expectedRevision: terminalRevision, specialInstructions: 'Must not save' });

    expect(result.status).toBe(409);
    expect(result.body).toMatchObject({ code: 'JOB_TERMINAL_STATE', currentRevision: terminalRevision });
  });

  test('22. Reusing an idempotency key with a different payload is rejected', async () => {
    const result = await request(app).post('/v1/driver/incidents')
      .set('Authorization', `Bearer ${driverToken}`)
      .set('X-Idempotency-Key', 'unique-key-abc-123')
      .send({ driverId: 'DRV-101', category: 'OTHER', description: 'different request' });
    expect(result.status).toBe(409);
    expect(result.body.code).toBe('IDEMPOTENCY_CONFLICT');
  });

  test('21. Authentication attempts are rate limited without limiting telemetry', async () => {
    let response;
    for (let attempt = 0; attempt < 11; attempt += 1) {
      response = await request(app).post('/v1/auth/driver/login')
        .send({ driverId: 'RATE-LIMIT-SYNTHETIC', pin: 'wrong' });
    }
    expect(response?.status).toBe(429);
  });

  test('22. View-only dispatch users cannot perform dispatcher mutations', async () => {
    const viewer = request.agent(app);
    const login = await viewer.post('/v1/auth/dispatch/login')
      .send({ email: 'viewer@1stclassexpress.com.au', password: 'Dispatch2026!' });

    expect(login.status).toBe(200);
    const mutation = await viewer.post('/v1/jobs/create')
      .set('X-CSRF-Token', login.body.csrfToken)
      .send({});
    expect(mutation.status).toBe(403);
  });

  test('23. Refresh tokens and tokens without an explicit access type cannot access driver routes', async () => {
    const refreshAttempt = await request(app).get('/v1/driver/profile')
      .set('Authorization', `Bearer ${driverRefreshToken}`);
    expect(refreshAttempt.status).toBe(401);

    const missingTypeToken = generateToken({
      id: 'DRV-101', driverId: 'DRV-101', name: 'John Smith', role: 'DRIVER'
    }, '15m');
    const missingTypeAttempt = await request(app).get('/v1/driver/profile')
      .set('Authorization', `Bearer ${missingTypeToken}`);
    expect(missingTypeAttempt.status).toBe(401);
  });

  test('24. Expired access tokens are rejected', async () => {
    const expiredToken = generateToken({
      id: 'DRV-101', driverId: 'DRV-101', name: 'John Smith', role: 'DRIVER', type: 'access'
    }, '-1s');
    const res = await request(app).get('/v1/driver/profile')
      .set('Authorization', `Bearer ${expiredToken}`);

    expect(res.status).toBe(401);
  });

  test('25. Disabled drivers lose access even with a previously valid JWT', async () => {
    const driver = db.drivers.get('DRV-101');
    expect(driver).toBeDefined();
    const wasActive = driver!.active;
    driver!.active = false;
    try {
      const res = await request(app).get('/v1/driver/profile')
        .set('Authorization', `Bearer ${driverToken}`);
      expect(res.status).toBe(401);
    } finally {
      driver!.active = wasActive;
    }
  });

  test('26. Evidence upload validates content, MIME type, emptiness, and size', async () => {
    const endpoint = () => request(app).post('/v1/driver/evidence')
      .set('Authorization', `Bearer ${driverToken}`)
      .set('X-Idempotency-Key', `upload-validation-${Math.random()}`)
      .field('metadata', JSON.stringify({ jobId: 'job-101', driverId: 'DRV-101', type: 'DELIVERY_PHOTO' }));

    expect((await endpoint().attach('file', Buffer.from('MZ executable'), {
      filename: 'malware.jpg', contentType: 'image/jpeg'
    })).status).toBe(415);
    expect((await endpoint().attach('file', Buffer.from([0xff, 0xd8, 0xff, 0x00]), {
      filename: 'image.jpg', contentType: 'application/octet-stream'
    })).status).toBe(415);
    expect((await endpoint().attach('file', Buffer.alloc(0), {
      filename: 'empty.png', contentType: 'image/png'
    })).status).toBe(415);
    expect((await endpoint().attach('file', Buffer.alloc(10 * 1024 * 1024 + 1, 1), {
      filename: 'large.jpg', contentType: 'image/jpeg'
    })).status).toBe(413);
    expect((await endpoint().attach('file', Buffer.from([0xff, 0xd8, 0xff, 0xd9]), {
      filename: 'valid.jpg', contentType: 'image/jpeg'
    })).status).toBe(201);
    expect((await endpoint().attach('file', Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), {
      filename: 'valid.png', contentType: 'image/png'
    })).status).toBe(201);
  });

  test('27. Dispatcher edits reject malformed values without changing the job', async () => {
    const before = db.jobs.get('job-103')!;
    const response = await dispatcher.put('/v1/jobs/job-103')
      .set('X-CSRF-Token', dispatcherCsrf)
      .send({ expectedRevision: before.revision, priority: 'INVALID', itemCount: 0 });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('JOB_EDIT_INVALID');
    expect(db.jobs.get('job-103')?.revision).toBe(before.revision);
  });

  test('28. Driver shift changes are persisted after mutation', async () => {
    const response = await request(app).post('/v1/driver/shifts/shift-101-today/events')
      .set('Authorization', `Bearer ${driverToken}`)
      .set('X-Idempotency-Key', 'shift-end-persisted')
      .send({ driverId: 'DRV-101', eventType: 'SHIFT_ENDED' });

    expect(response.status).toBe(200);
    expect(db.drivers.get('DRV-101')?.shiftStatus).toBe('OFF_DUTY');
    expect(db.drivers.get('DRV-101')?.currentShiftId).toBeNull();
  });
});
