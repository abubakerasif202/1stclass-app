import {
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Filter,
  MapPin,
  MessageSquare,
  Shield,
  Truck,
  User,
  X
} from 'lucide-react';
import React, { useState } from 'react';
import { Incident } from '../types';

interface IncidentsViewProps {
  incidents: Incident[];
  onAcknowledge: (incidentId: string) => Promise<void>;
  onResolve: (incidentId: string, opsNotes: string) => Promise<void>;
}

export const IncidentsView: React.FC<IncidentsViewProps> = ({
  incidents,
  onAcknowledge,
  onResolve
}) => {
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [opsNotes, setOpsNotes] = useState('');
  const [isResolving, setIsResolving] = useState(false);

  const filtered = incidents.filter(i => {
    if (selectedStatus === 'ALL') return true;
    return i.status === selectedStatus;
  });

  const getSeverityBadge = (sev: string) => {
    if (sev === 'CRITICAL' || sev === 'HIGH') return <span className="badge badge-red">{sev}</span>;
    if (sev === 'MEDIUM') return <span className="badge badge-amber">{sev}</span>;
    return <span className="badge badge-neutral">{sev}</span>;
  };

  const getStatusBadge = (st: string) => {
    if (st === 'RESOLVED') return <span className="badge badge-green">RESOLVED</span>;
    if (st === 'ACKNOWLEDGED') return <span className="badge badge-blue">ACKNOWLEDGED</span>;
    return <span className="badge badge-red">OPEN / ACTIVE</span>;
  };

  const handleResolveSubmit = async () => {
    if (!selectedIncident) return;
    try {
      setIsResolving(true);
      await onResolve(selectedIncident.id, opsNotes);
      setSelectedIncident(null);
      setOpsNotes('');
    } finally {
      setIsResolving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '0.5px' }}>
            INCIDENTS & EXCEPTION MANAGEMENT
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time driver breakdown, delay, accident, and freight damage notifications.
          </p>
        </div>

        {/* Status Filter Tabs */}
        <div style={{ display: 'flex', gap: '6px' }}>
          {['ALL', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'].map(st => (
            <button
              key={st}
              onClick={() => setSelectedStatus(st)}
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: 'none',
                background: selectedStatus === st ? 'var(--gold-primary)' : 'var(--bg-card)',
                color: selectedStatus === st ? '#0c0e12' : 'var(--text-secondary)',
                fontWeight: 600,
                fontSize: '12px',
                cursor: 'pointer'
              }}
            >
              {st}
            </button>
          ))}
        </div>
      </div>

      {/* Incidents Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '16px' }}>
        {filtered.length === 0 ? (
          <div className="ops-card" style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
            <CheckCircle2 size={32} color="var(--status-green)" style={{ marginBottom: '8px' }} />
            <div>No open incidents or exceptions currently recorded.</div>
          </div>
        ) : (
          filtered.map(inc => (
            <div
              key={inc.id}
              className="ops-card"
              style={{
                border: inc.status === 'OPEN' ? '1px solid rgba(239, 68, 68, 0.4)' : '1px solid var(--border-subtle)',
                background: inc.status === 'OPEN' ? 'rgba(239, 68, 68, 0.03)' : 'var(--bg-card)'
              }}
            >
              {/* Card Header */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <AlertTriangle size={18} color={inc.status === 'OPEN' ? 'var(--status-red)' : 'var(--text-muted)'} />
                  <strong style={{ fontSize: '14px', color: 'var(--text-primary)' }}>{inc.category}</strong>
                </div>
                <div style={{ display: 'flex', gap: '6px' }}>
                  {getSeverityBadge(inc.severity)}
                  {getStatusBadge(inc.status)}
                </div>
              </div>

              {/* Description */}
              <p style={{ fontSize: '13px', color: 'var(--text-primary)', marginBottom: '12px', lineHeight: 1.4 }}>
                {inc.description}
              </p>

              {/* Meta Info */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)', borderTop: '1px solid var(--border-subtle)', paddingTop: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <User size={13} /> Driver:
                  </span>
                  <strong style={{ color: 'var(--text-primary)' }}>{inc.driverName || inc.driverId}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Truck size={13} /> Vehicle / Job:
                  </span>
                  <span>{inc.vehicleRego || 'N/A'} · {inc.jobReference || 'General Shift'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Clock size={13} /> Reported:
                  </span>
                  <span style={{ fontFamily: 'var(--font-mono)' }}>{new Date(inc.reportedAt).toLocaleString()}</span>
                </div>
                {inc.opsNotes && (
                  <div style={{ marginTop: '6px', padding: '6px 8px', background: 'var(--bg-main)', borderRadius: '4px', fontSize: '11px', color: 'var(--text-gold)' }}>
                    <strong>Ops Notes: </strong>{inc.opsNotes}
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', gap: '8px', marginTop: '14px', borderTop: '1px solid var(--border-subtle)', paddingTop: '10px' }}>
                {inc.status === 'OPEN' && (
                  <button
                    onClick={() => onAcknowledge(inc.id)}
                    className="btn-secondary"
                    style={{ flex: 1, justifyContent: 'center', fontSize: '12px', padding: '6px' }}
                  >
                    Acknowledge
                  </button>
                )}
                {inc.status !== 'RESOLVED' && (
                  <button
                    onClick={() => { setSelectedIncident(inc); setOpsNotes(inc.opsNotes || ''); }}
                    className="btn-primary"
                    style={{ flex: 1, justifyContent: 'center', fontSize: '12px', padding: '6px' }}
                  >
                    Resolve Incident
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Resolve Incident Dialog */}
      {selectedIncident && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: '480px' }}>
            <div className="modal-header">
              <h2 style={{ fontSize: '16px', fontWeight: 800, color: 'var(--status-green)' }}>
                RESOLVE INCIDENT — {selectedIncident.category}
              </h2>
              <button
                onClick={() => setSelectedIncident(null)}
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                Driver: <strong>{selectedIncident.driverName || selectedIncident.driverId}</strong>
              </div>
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                Reported: {selectedIncident.description}
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  Resolution / Operations Action Notes *
                </label>
                <textarea
                  className="input-field"
                  rows={3}
                  placeholder="e.g. Service team arrived, tyre replaced, driver resumed transit."
                  value={opsNotes}
                  onChange={(e) => setOpsNotes(e.target.value)}
                  required
                />
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setSelectedIncident(null)} className="btn-secondary">
                Cancel
              </button>
              <button onClick={handleResolveSubmit} className="btn-primary" disabled={isResolving || !opsNotes.trim()}>
                <CheckCircle2 size={16} /> {isResolving ? 'Resolving...' : 'Confirm Resolution'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
