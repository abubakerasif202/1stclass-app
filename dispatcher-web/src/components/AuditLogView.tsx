import { Clock, Filter, History, Shield, User } from 'lucide-react';
import React, { useState } from 'react';
import { AuditLogEntry } from '../types';

interface AuditLogViewProps {
  logs: AuditLogEntry[];
}

export const AuditLogView: React.FC<AuditLogViewProps> = ({ logs }) => {
  const [selectedEntity, setSelectedEntity] = useState<string>('ALL');

  const filtered = logs.filter(l => {
    if (selectedEntity === 'ALL') return true;
    return l.entityType === selectedEntity;
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '0.5px' }}>
            OPERATIONAL AUDIT TRAIL
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Immutable record of all job creations, fleet allocations, status changes, cancellations, and config updates.
          </p>
        </div>

        {/* Filter Tabs */}
        <div style={{ display: 'flex', gap: '6px' }}>
          {['ALL', 'JOB', 'DRIVER', 'VEHICLE', 'INCIDENT', 'MESSAGE', 'CONFIG'].map(ent => (
            <button
              key={ent}
              onClick={() => setSelectedEntity(ent)}
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: 'none',
                background: selectedEntity === ent ? 'var(--gold-primary)' : 'var(--bg-card)',
                color: selectedEntity === ent ? '#0c0e12' : 'var(--text-secondary)',
                fontWeight: 600,
                fontSize: '12px',
                cursor: 'pointer'
              }}
            >
              {ent}
            </button>
          ))}
        </div>
      </div>

      {/* Audit Log Table */}
      <div className="ops-table-container">
        <table className="ops-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Actor</th>
              <th>Role</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Entity ID</th>
              <th>Notes / Change Summary</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(entry => (
              <tr key={entry.id}>
                <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-muted)' }}>
                  {new Date(entry.timestamp).toLocaleString()}
                </td>
                <td>
                  <strong style={{ fontSize: '13px', color: 'var(--text-primary)' }}>{entry.userName}</strong>
                </td>
                <td>
                  <span className="badge badge-gold" style={{ fontSize: '10px' }}>
                    {entry.userRole}
                  </span>
                </td>
                <td>
                  <span style={{ fontWeight: 700, fontSize: '12px', color: 'var(--text-gold)', fontFamily: 'var(--font-mono)' }}>
                    {entry.action}
                  </span>
                </td>
                <td>
                  <span className="badge badge-neutral" style={{ fontSize: '10px' }}>
                    {entry.entityType}
                  </span>
                </td>
                <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-secondary)' }}>
                  {entry.entityId}
                </td>
                <td style={{ fontSize: '12px', color: 'var(--text-secondary)', maxWidth: '300px' }}>
                  {entry.notes || (entry.newState ? JSON.stringify(entry.newState) : '—')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
