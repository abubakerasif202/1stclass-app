import assert from 'node:assert/strict';
import test from 'node:test';
const { createJobEditDraft, buildJobEditPayload, persistJobEdit, submitJobEdit } = await import('../.test-dist/editJob.js');

const job = {
  id: 'job-42',
  reference: '1CE-042',
  status: 'ASSIGNED',
  priority: 'HIGH',
  assignedDriverId: 'DRV-1',
  assignedVehicleId: 'VH-1',
  pickup: { companyName: 'North Depot', address: '12 Depot Road', suburb: 'Derrimut VIC', contactName: 'Taylor', contactPhone: '0400 111 222', lat: -37.8, lng: 144.9 },
  delivery: { companyName: 'South Receiver', address: '8 Delivery Way', suburb: 'Dandenong VIC', contactName: 'Morgan', contactPhone: '0400 333 444', lat: -38.0, lng: 145.2 },
  pickupWindowStart: '09:15',
  pickupWindowEnd: '10:45',
  deliveryWindowStart: '13:30',
  deliveryWindowEnd: '15:00',
  freightDescription: 'Six pallets of chilled freight',
  itemCount: 6,
  specialInstructions: 'Use loading dock 3',
  dangerousGoods: true,
  revision: 7,
  serverUpdatedAt: 1,
  timeline: [],
  pod: null,
  createdAt: 1
};

test('edit form opens with current job values rather than fabricated defaults', () => {
  const draft = createJobEditDraft(job);

  assert.equal(draft.pickup.companyName, 'North Depot');
  assert.equal(draft.pickupWindowStart, '09:15');
  assert.equal(draft.freightDescription, 'Six pallets of chilled freight');
  assert.equal(draft.dangerousGoods, true);
  assert.notEqual(draft.pickupWindowStart, '08:00');
});

test('invalid edit input is blocked before any mutation callback', async () => {
  const draft = createJobEditDraft(job);
  draft.delivery.address = '   ';
  let calls = 0;

  const result = await submitJobEdit(draft, job.revision, async () => { calls += 1; });

  assert.deepEqual(result, { kind: 'validation', message: 'Delivery company, address, and suburb are required.' });
  assert.equal(calls, 0);
});

test('successful edit sends only supported fields with the current expected revision and preserves coordinates', async () => {
  const draft = createJobEditDraft(job);
  draft.specialInstructions = 'Call ahead on arrival';
  let submitted;

  const result = await submitJobEdit(draft, job.revision, async payload => { submitted = payload; });

  assert.equal(result.kind, 'success');
  assert.equal(submitted.expectedRevision, 7);
  assert.equal(submitted.specialInstructions, 'Call ahead on arrival');
  assert.equal(submitted.pickup.lat, -37.8);
  assert.equal(submitted.delivery.lng, 145.2);
  assert.deepEqual(Object.keys(submitted).sort(), [
    'dangerousGoods', 'delivery', 'deliveryWindowEnd', 'deliveryWindowStart', 'expectedRevision', 'freightDescription', 'itemCount', 'pickup', 'pickupWindowEnd', 'pickupWindowStart', 'priority', 'specialInstructions'
  ]);
});

test('a successful edit refreshes dispatcher data only after its update completes', async () => {
  const events = [];
  const payload = buildJobEditPayload(createJobEditDraft(job), job.revision);

  const result = await persistJobEdit(
    payload,
    async () => { events.push('update'); },
    async () => { events.push('refresh'); }
  );

  assert.deepEqual(events, ['update', 'refresh']);
  assert.deepEqual(result, { kind: 'refreshed' });
});

test('a refresh failure is reported separately after a committed edit and never turns it into a retryable save failure', async () => {
  const events = [];
  const payload = buildJobEditPayload(createJobEditDraft(job), job.revision);

  const result = await persistJobEdit(
    payload,
    async () => { events.push('update'); },
    async () => { events.push('refresh'); throw new Error('Fleet refresh unavailable'); }
  );

  assert.deepEqual(events, ['update', 'refresh']);
  assert.deepEqual(result, { kind: 'refresh-failed', message: 'Fleet refresh unavailable' });
});

test('revision conflict exposes a refresh-required result without a silent retry', async () => {
  const result = await submitJobEdit(createJobEditDraft(job), job.revision, async () => {
    const error = Object.assign(new Error('Job was updated by another session'), { code: 'JOB_REVISION_CONFLICT', currentRevision: 8 });
    throw error;
  });

  assert.deepEqual(result, {
    kind: 'conflict',
    message: 'This job was updated by another dispatcher. No changes were saved. Refresh the record before trying again.',
    currentRevision: 8
  });
});

test('network failures keep the edit open with a recoverable error', async () => {
  let calls = 0;
  const result = await submitJobEdit(createJobEditDraft(job), job.revision, async () => {
    calls += 1;
    throw new Error('Network unavailable');
  });

  assert.deepEqual(result, { kind: 'network', message: 'Network unavailable' });
  assert.equal(calls, 1);
});
