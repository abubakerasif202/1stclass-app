import { UserCheck, X } from 'lucide-react';
import React, { useState } from 'react';
import { Driver, Job, Vehicle } from '../types';

interface ReassignJobModalProps {
  job: Job;
  drivers: Driver[];
  vehicles: Vehicle[];
  onClose: () => void;
  onSubmit: (jobId: string, newDriverId: string, newVehicleId: string, reason: string) => Promise<void>;
}

export const ReassignJobModal: React.FC<ReassignJobModalProps> = ({
  job,
  drivers,
  vehicles,
  onClose,
  onSubmit
}) => {
  const [newDriverId, setNewDriverId] = useState(job.assignedDriverId || '');
  const [newVehicleId, setNewVehicleId] = useState(job.assignedVehicleId || '');
  const [reason, setReason] = useState('Operational adjustment & fleet optimization');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDriverId) return;

    try {
      setIsSubmitting(true);
      await onSubmit(job.id, newDriverId, newVehicleId, reason);
      onClose();
    } catch (err) {
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '520px' }}>
        <form onSubmit={handleSubmit}>
          <div className="modal-header">
            <h2 style={{ fontSize: '16px', fontWeight: 800, color: 'var(--text-gold)' }}>
              REASSIGN JOB {job.reference}
            </h2>
            <button
              type="button"
              onClick={onClose}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
            >
              <X size={20} />
            </button>
          </div>

          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
              Current Driver: <strong>{drivers.find(d => d.id === job.assignedDriverId)?.name || 'Unassigned'}</strong>
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                New Allocated Driver *
              </label>
              <select
                className="input-field"
                value={newDriverId}
                onChange={(e) => {
                  setNewDriverId(e.target.value);
                  const d = drivers.find(drv => drv.id === e.target.value);
                  if (d?.currentVehicleId) setNewVehicleId(d.currentVehicleId);
                }}
                required
              >
                <option value="">-- Select Driver --</option>
                {drivers.map(d => (
                  <option key={d.id} value={d.id}>
                    {d.name} ({d.shiftStatus}) {d.currentVehicleId ? `[${d.currentVehicleId}]` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Allocated Vehicle
              </label>
              <select
                className="input-field"
                value={newVehicleId}
                onChange={(e) => setNewVehicleId(e.target.value)}
              >
                <option value="">-- Select Vehicle --</option>
                {vehicles.map(v => (
                  <option key={v.id} value={v.id}>
                    {v.rego} — {v.makeModel}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Reason for Reassignment
              </label>
              <textarea
                className="input-field"
                rows={2}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={isSubmitting || !newDriverId}>
              <UserCheck size={16} /> {isSubmitting ? 'Updating...' : 'Confirm Reassignment'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
