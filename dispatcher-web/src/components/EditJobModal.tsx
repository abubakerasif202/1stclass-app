import { Save, X } from 'lucide-react';
import React, { useEffect, useState } from 'react';
import { createJobEditDraft, JobEditDraft, JobEditPayload, submitJobEdit } from '../editJob';
import { Job } from '../types';

interface EditJobModalProps {
  job: Job;
  onClose: () => void;
  onSubmit: (jobId: string, payload: JobEditPayload) => Promise<void>;
  onRefresh: () => Promise<void>;
}

const updateLocation = (location: JobEditDraft['pickup'], field: keyof JobEditDraft['pickup'], value: string) => ({ ...location, [field]: value });

export const EditJobModal: React.FC<EditJobModalProps> = ({ job, onClose, onSubmit, onRefresh }) => {
  const [draft, setDraft] = useState<JobEditDraft>(() => createJobEditDraft(job));
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [conflict, setConflict] = useState<{ currentRevision?: number } | null>(null);

  useEffect(() => {
    setDraft(createJobEditDraft(job));
    setError(null);
    setConflict(null);
  }, [job]);

  useEffect(() => {
    if (job.status === 'COMPLETED' || job.status === 'CANCELLED') onClose();
  }, [job.status, onClose]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    setConflict(null);
    const result = await submitJobEdit(draft, job.revision, payload => onSubmit(job.id, payload));
    setIsSubmitting(false);

    if (result.kind === 'success') {
      onClose();
      return;
    }
    setError(result.message);
    if (result.kind === 'conflict') setConflict({ currentRevision: result.currentRevision });
  };

  const refresh = async () => {
    setIsRefreshing(true);
    setError(null);
    try {
      await onRefresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to refresh the job record.');
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="edit-job-title">
      <div className="modal-content" style={{ maxWidth: '760px' }}>
        <form onSubmit={save}>
          <div className="modal-header">
            <div>
              <h2 id="edit-job-title" style={{ fontSize: '18px', fontWeight: 800, color: 'var(--text-gold)' }}>EDIT JOB DETAILS</h2>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>{job.reference} · Revision {job.revision}</div>
            </div>
            <button type="button" onClick={onClose} aria-label="Close edit job" style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}><X size={20} /></button>
          </div>

          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {error && <div role="alert" className="error-message">{error}</div>}
            {conflict && (
              <div className="ops-card" style={{ padding: '12px', borderColor: 'var(--status-red)' }}>
                <strong>Revision conflict</strong>
                {conflict.currentRevision !== undefined && <span style={{ marginLeft: '6px' }}>Server revision: {conflict.currentRevision}.</span>}
                <button type="button" onClick={refresh} className="btn-secondary" disabled={isRefreshing} style={{ marginLeft: '12px' }}>
                  {isRefreshing ? 'Refreshing…' : 'Refresh record'}
                </button>
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <label>Priority<select className="input-field" value={draft.priority} onChange={event => setDraft(previous => ({ ...previous, priority: event.target.value as JobEditDraft['priority'] }))}>
                <option value="LOW">LOW</option><option value="NORMAL">NORMAL</option><option value="HIGH">HIGH</option><option value="URGENT">URGENT</option>
              </select></label>
              <label>Item count<input className="input-field" type="number" min="1" step="1" value={draft.itemCount} onChange={event => setDraft(previous => ({ ...previous, itemCount: event.target.value }))} /></label>
            </div>

            <LocationFields title="PICKUP POINT" location={draft.pickup} onChange={(field, value) => setDraft(previous => ({ ...previous, pickup: updateLocation(previous.pickup, field, value) }))} />
            <LocationFields title="DELIVERY DESTINATION" location={draft.delivery} onChange={(field, value) => setDraft(previous => ({ ...previous, delivery: updateLocation(previous.delivery, field, value) }))} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <label>Pickup window start<input className="input-field" value={draft.pickupWindowStart} onChange={event => setDraft(previous => ({ ...previous, pickupWindowStart: event.target.value }))} /></label>
              <label>Pickup window end<input className="input-field" value={draft.pickupWindowEnd} onChange={event => setDraft(previous => ({ ...previous, pickupWindowEnd: event.target.value }))} /></label>
              <label>Delivery window start<input className="input-field" value={draft.deliveryWindowStart} onChange={event => setDraft(previous => ({ ...previous, deliveryWindowStart: event.target.value }))} /></label>
              <label>Delivery window end<input className="input-field" value={draft.deliveryWindowEnd} onChange={event => setDraft(previous => ({ ...previous, deliveryWindowEnd: event.target.value }))} /></label>
            </div>

            <label>Freight description<input className="input-field" value={draft.freightDescription} onChange={event => setDraft(previous => ({ ...previous, freightDescription: event.target.value }))} /></label>
            <label>Special instructions<textarea className="input-field" rows={3} value={draft.specialInstructions} onChange={event => setDraft(previous => ({ ...previous, specialInstructions: event.target.value }))} /></label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}><input type="checkbox" checked={draft.dangerousGoods} onChange={event => setDraft(previous => ({ ...previous, dangerousGoods: event.target.checked }))} /> Dangerous goods / Hazmat documentation required</label>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={onClose} className="btn-secondary" disabled={isSubmitting || isRefreshing}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={isSubmitting || isRefreshing}><Save size={16} /> {isSubmitting ? 'Saving…' : 'Save job details'}</button>
          </div>
        </form>
      </div>
    </div>
  );
};

const LocationFields: React.FC<{
  title: string;
  location: JobEditDraft['pickup'];
  onChange: (field: keyof JobEditDraft['pickup'], value: string) => void;
}> = ({ title, location, onChange }) => (
  <div className="ops-card" style={{ padding: '12px' }}>
    <strong style={{ fontSize: '13px', color: 'var(--gold-primary)', display: 'block', marginBottom: '8px' }}>{title}</strong>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
      <label>Company<input className="input-field" value={location.companyName} onChange={event => onChange('companyName', event.target.value)} /></label>
      <label>Address<input className="input-field" value={location.address} onChange={event => onChange('address', event.target.value)} /></label>
      <label>Suburb<input className="input-field" value={location.suburb} onChange={event => onChange('suburb', event.target.value)} /></label>
      <label>Contact name<input className="input-field" value={location.contactName} onChange={event => onChange('contactName', event.target.value)} /></label>
      <label>Contact phone<input className="input-field" value={location.contactPhone} onChange={event => onChange('contactPhone', event.target.value)} /></label>
    </div>
  </div>
);
