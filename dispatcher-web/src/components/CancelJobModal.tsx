import { AlertTriangle, X } from 'lucide-react';
import React, { useState } from 'react';
import { Job } from '../types';

interface CancelJobModalProps {
  job: Job;
  onClose: () => void;
  onSubmit: (jobId: string, reason: string) => Promise<void>;
}

export const CancelJobModal: React.FC<CancelJobModalProps> = ({
  job,
  onClose,
  onSubmit
}) => {
  const [reason, setReason] = useState('Customer cancelled booking / order adjusted');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      await onSubmit(job.id, reason);
      onClose();
    } catch (err) {
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '480px' }}>
        <form onSubmit={handleSubmit}>
          <div className="modal-header">
            <h2 style={{ fontSize: '16px', fontWeight: 800, color: 'var(--status-red)' }}>
              CANCEL JOB {job.reference}
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
            <div style={{
              padding: '12px',
              background: 'var(--status-red-bg)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: '6px',
              fontSize: '13px',
              color: 'var(--status-red)'
            }}>
              <AlertTriangle size={16} style={{ display: 'inline', marginRight: '6px' }} />
              Cancelling will immediately notify the assigned driver work phone and remove the job from their active manifest.
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Cancellation Reason *
              </label>
              <textarea
                className="input-field"
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={onClose} className="btn-secondary">
              Keep Job
            </button>
            <button type="submit" className="btn-danger" disabled={isSubmitting || !reason.trim()}>
              {isSubmitting ? 'Cancelling...' : 'Confirm Job Cancellation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
