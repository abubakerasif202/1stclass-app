import assert from 'node:assert/strict';
import test from 'node:test';

const calls = [];
const sessionEvents = [];
let responder = () => Response.json({ success: true });

globalThis.window = {
  dispatchEvent(event) {
    sessionEvents.push(event.type);
  }
};

globalThis.fetch = async (url, options = {}) => {
  const call = { url: String(url), options };
  calls.push(call);
  return responder(call);
};

const { api, ApiError } = await import('../.test-dist/api.js');

function reset(responseFactory) {
  calls.length = 0;
  sessionEvents.length = 0;
  responder = responseFactory;
}

function jsonBody(call) {
  return JSON.parse(call.options.body);
}

test('dispatcher login, session restoration, and logout use credentialed cookies and rotate CSRF state', async () => {
  reset(call => {
    if (call.url.endsWith('/auth/dispatch/login')) {
      return Response.json({ csrfToken: 'login-csrf', user: { id: 'ops-1', role: 'DISPATCHER' } });
    }
    if (call.url.endsWith('/auth/dispatch/me')) {
      return Response.json({ csrfToken: 'restored-csrf', user: { id: 'ops-1', role: 'DISPATCHER' } });
    }
    return Response.json({ success: true });
  });

  await api.login('ops@test.invalid', 'synthetic-password');
  await api.getMe();
  await api.logout();
  await api.createJob({ reference: 'POST-LOGOUT' });

  assert.deepEqual(jsonBody(calls[0]), { email: 'ops@test.invalid', password: 'synthetic-password' });
  assert.equal(calls[0].options.credentials, 'include');
  assert.equal(calls[1].options.headers['X-CSRF-Token'], undefined);
  assert.equal(calls[2].options.headers['X-CSRF-Token'], 'restored-csrf');
  assert.equal(calls[3].options.headers['X-CSRF-Token'], undefined);
  assert.match(calls[0].options.headers['X-Idempotency-Key'], /^[0-9a-f-]{36}$/);
  assert.match(calls[3].options.headers['X-Idempotency-Key'], /^[0-9a-f-]{36}$/);
});

test('job list and detail preserve filters, identifiers, and proof-of-delivery payloads', async () => {
  reset(call => {
    if (call.url.includes('/jobs?')) return Response.json({ jobs: [{ id: 'job-1' }], total: 1 });
    return Response.json({
      job: {
        id: 'job/a b',
        status: 'COMPLETED',
        pod: { recipientName: 'Sam Receiver', signatureEvidenceId: 'sig-1', photoEvidenceIds: ['photo-1'], status: 'COMPLETE' }
      }
    });
  });

  const list = await api.getJobs({ status: 'ASSIGNED', search: 'Acme & Sons' });
  const detail = await api.getJob('job/a b');

  assert.equal(list.total, 1);
  assert.equal(calls[0].url, '/v1/jobs?status=ASSIGNED&search=Acme+%26+Sons');
  assert.equal(calls[1].url, '/v1/jobs/job/a b');
  assert.equal(detail.job.pod.recipientName, 'Sam Receiver');
  assert.deepEqual(detail.job.pod.photoEvidenceIds, ['photo-1']);
});

test('create and dispatch mutations send complete payloads with CSRF and idempotency protection', async () => {
  reset(call => {
    if (call.url.endsWith('/auth/dispatch/me')) return Response.json({ csrfToken: 'mutation-csrf', user: { id: 'ops-1', role: 'DISPATCHER' } });
    return Response.json({ success: true, job: { id: 'job-1' } });
  });
  await api.getMe();
  await api.createJob({ reference: 'NEW-42', assignedDriverId: 'driver-1', assignedVehicleId: 'vehicle-1' });
  await api.updateJob('job-1', { expectedRevision: 4, specialInstructions: 'Keep refrigerated' });
  await api.reassignJob('job-1', 'driver-2', 'vehicle-2', 'Relief shift');
  await api.cancelJob('job-1', 'Customer cancelled');

  assert.deepEqual(jsonBody(calls[1]), { reference: 'NEW-42', assignedDriverId: 'driver-1', assignedVehicleId: 'vehicle-1' });
  assert.deepEqual(jsonBody(calls[2]), { expectedRevision: 4, specialInstructions: 'Keep refrigerated' });
  assert.deepEqual(jsonBody(calls[3]), { newDriverId: 'driver-2', newVehicleId: 'vehicle-2', reason: 'Relief shift' });
  assert.deepEqual(jsonBody(calls[4]), { reason: 'Customer cancelled' });
  for (const call of calls.slice(1)) {
    assert.equal(call.options.headers['X-CSRF-Token'], 'mutation-csrf');
    assert.match(call.options.headers['X-Idempotency-Key'], /^[0-9a-f-]{36}$/);
  }
});

test('server validation and revision conflicts remain structured for dispatcher UI handling', async () => {
  reset(call => {
    if (call.url.endsWith('/jobs/create')) {
      return Response.json({ error: 'Pickup address is required', code: 'JOB_VALIDATION_FAILED' }, { status: 400 });
    }
    return Response.json({ error: 'Job was updated by another session', code: 'JOB_REVISION_CONFLICT', currentRevision: 6 }, { status: 409 });
  });

  await assert.rejects(
    () => api.createJob({ reference: 'INVALID' }),
    error => error.message === 'Pickup address is required' && error.code === 'JOB_VALIDATION_FAILED'
  );
  await assert.rejects(
    () => api.updateJob('job-1', { expectedRevision: 5 }),
    error => error instanceof ApiError && error.message === 'Job was updated by another session' && error.code === 'JOB_REVISION_CONFLICT' && error.currentRevision === 6
  );
});

test('incidents, evidence URLs, and unauthorised responses use the dispatcher API contract', async () => {
  reset(call => {
    if (call.url.endsWith('/incidents')) return Response.json({ incidents: [{ id: 'incident-1', status: 'OPEN' }], count: 1 });
    if (call.url.includes('/acknowledge')) return Response.json({ success: true, incident: { id: 'incident-1', status: 'ACKNOWLEDGED' } });
    if (call.url.includes('/resolve')) return Response.json({ success: true, incident: { id: 'incident-1', status: 'RESOLVED', opsNotes: 'Driver safe' } });
    return Response.json({ error: 'Session expired', code: 'AUTH_REQUIRED' }, { status: 401 });
  });

  assert.equal((await api.getIncidents()).count, 1);
  assert.equal((await api.acknowledgeIncident('incident-1')).incident.status, 'ACKNOWLEDGED');
  assert.equal((await api.resolveIncident('incident-1', 'Driver safe')).incident.opsNotes, 'Driver safe');
  assert.equal(api.evidenceUrl('photo / 1'), '/v1/evidence/photo%20%2F%201');
  await assert.rejects(() => api.getDrivers(), error => error.code === 'AUTH_REQUIRED');
  assert.deepEqual(jsonBody(calls[2]), { opsNotes: 'Driver safe' });
  assert.deepEqual(sessionEvents, ['tms:session-expired']);
});

test('realtime connection forwards valid events, tolerates malformed payloads, and cleans up reconnects', () => {
  let source;
  globalThis.EventSource = class {
    listeners = new Map();
    closed = false;
    constructor(url, options) {
      this.url = url;
      this.withCredentials = options.withCredentials;
      source = this;
    }
    addEventListener(type, callback) { this.listeners.set(type, callback); }
    close() { this.closed = true; }
  };

  const statuses = [];
  const events = [];
  const originalConsoleError = console.error;
  console.error = () => {};
  try {
    const close = api.connectSse((type, data) => events.push({ type, data }), status => statuses.push(status));
    source.onopen();
    source.listeners.get('pod.completed')({ data: JSON.stringify({ jobId: 'job-1', podId: 'pod-1' }) });
    source.listeners.get('incident.updated')({ data: '{not json' });
    source.onerror();
    close();

    assert.equal(source.url, '/v1/events/stream');
    assert.equal(source.withCredentials, true);
    assert.deepEqual(statuses, ['RECONNECTING', 'CONNECTED', 'DISCONNECTED']);
    assert.deepEqual(events, [{ type: 'pod.completed', data: { jobId: 'job-1', podId: 'pod-1' } }]);
    assert.equal(source.closed, true);
  } finally {
    console.error = originalConsoleError;
  }
});
