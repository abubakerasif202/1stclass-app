import { Job, LocationCoordinates, Priority } from './types';

export interface JobEditDraft {
  priority: Priority;
  pickup: LocationCoordinates;
  delivery: LocationCoordinates;
  pickupWindowStart: string;
  pickupWindowEnd: string;
  deliveryWindowStart: string;
  deliveryWindowEnd: string;
  freightDescription: string;
  itemCount: string;
  specialInstructions: string;
  dangerousGoods: boolean;
}

export interface JobEditPayload {
  expectedRevision: number;
  priority: Priority;
  pickup: LocationCoordinates;
  delivery: LocationCoordinates;
  pickupWindowStart: string;
  pickupWindowEnd: string;
  deliveryWindowStart: string;
  deliveryWindowEnd: string;
  freightDescription: string;
  itemCount: number;
  specialInstructions: string;
  dangerousGoods: boolean;
}

export type JobEditSubmitResult =
  | { kind: 'success'; payload: JobEditPayload }
  | { kind: 'validation'; message: string }
  | { kind: 'conflict'; message: string; currentRevision?: number }
  | { kind: 'network'; message: string };

export type JobEditRefreshResult =
  | { kind: 'refreshed' }
  | { kind: 'refresh-failed'; message: string };

export function createJobEditDraft(job: Job): JobEditDraft {
  return {
    priority: job.priority,
    pickup: { ...job.pickup },
    delivery: { ...job.delivery },
    pickupWindowStart: job.pickupWindowStart,
    pickupWindowEnd: job.pickupWindowEnd,
    deliveryWindowStart: job.deliveryWindowStart,
    deliveryWindowEnd: job.deliveryWindowEnd,
    freightDescription: job.freightDescription,
    itemCount: String(job.itemCount),
    specialInstructions: job.specialInstructions,
    dangerousGoods: job.dangerousGoods
  };
}

export function validateJobEditDraft(draft: JobEditDraft): string | null {
  if (!draft.pickup.companyName.trim() || !draft.pickup.address.trim() || !draft.pickup.suburb.trim()) {
    return 'Pickup company, address, and suburb are required.';
  }
  if (!draft.delivery.companyName.trim() || !draft.delivery.address.trim() || !draft.delivery.suburb.trim()) {
    return 'Delivery company, address, and suburb are required.';
  }
  if (!draft.pickupWindowStart || !draft.pickupWindowEnd || !draft.deliveryWindowStart || !draft.deliveryWindowEnd) {
    return 'Pickup and delivery windows are required.';
  }
  if (!draft.freightDescription.trim()) return 'Freight description is required.';
  if (!Number.isInteger(Number(draft.itemCount)) || Number(draft.itemCount) < 1) {
    return 'Item count must be a whole number of at least 1.';
  }
  return null;
}

export function buildJobEditPayload(draft: JobEditDraft, expectedRevision: number): JobEditPayload {
  const validationError = validateJobEditDraft(draft);
  if (validationError) throw new Error(validationError);

  return {
    expectedRevision,
    priority: draft.priority,
    pickup: { ...draft.pickup },
    delivery: { ...draft.delivery },
    pickupWindowStart: draft.pickupWindowStart,
    pickupWindowEnd: draft.pickupWindowEnd,
    deliveryWindowStart: draft.deliveryWindowStart,
    deliveryWindowEnd: draft.deliveryWindowEnd,
    freightDescription: draft.freightDescription.trim(),
    itemCount: Number(draft.itemCount),
    specialInstructions: draft.specialInstructions,
    dangerousGoods: draft.dangerousGoods
  };
}

export async function submitJobEdit(
  draft: JobEditDraft,
  expectedRevision: number,
  submit: (payload: JobEditPayload) => Promise<void>
): Promise<JobEditSubmitResult> {
  const validationError = validateJobEditDraft(draft);
  if (validationError) return { kind: 'validation', message: validationError };

  const payload = buildJobEditPayload(draft, expectedRevision);
  try {
    await submit(payload);
    return { kind: 'success', payload };
  } catch (error) {
    const apiError = error as Error & { code?: string; currentRevision?: number };
    if (apiError.code === 'JOB_REVISION_CONFLICT') {
      return {
        kind: 'conflict',
        message: 'This job was updated by another dispatcher. No changes were saved. Refresh the record before trying again.',
        currentRevision: apiError.currentRevision
      };
    }
    return { kind: 'network', message: apiError.message || 'Unable to update the job. Please try again.' };
  }
}

export async function persistJobEdit(
  payload: JobEditPayload,
  update: (payload: JobEditPayload) => Promise<void>,
  refresh: () => Promise<void>
): Promise<JobEditRefreshResult> {
  await update(payload);
  try {
    await refresh();
    return { kind: 'refreshed' };
  } catch (error) {
    return {
      kind: 'refresh-failed',
      message: error instanceof Error ? error.message : 'Job was saved, but the latest dispatcher data could not be refreshed.'
    };
  }
}
