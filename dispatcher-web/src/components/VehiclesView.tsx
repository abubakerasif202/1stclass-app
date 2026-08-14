import {
  AlertOctagon,
  CheckCircle2,
  Clock,
  Shield,
  Truck,
  User,
  Wrench
} from 'lucide-react';
import React, { useState } from 'react';
import { Vehicle } from '../types';

interface VehiclesViewProps {
  vehicles: Vehicle[];
  onUpdateDefectStatus: (vehicleId: string, defectId: string, status: string) => Promise<void>;
}

export const VehiclesView: React.FC<VehiclesViewProps> = ({
  vehicles,
  onUpdateDefectStatus
}) => {
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');

  const filtered = vehicles.filter(v => {
    if (selectedStatus === 'ALL') return true;
    return v.status === selectedStatus;
  });

  const getStatusBadge = (st: string) => {
    switch (st) {
      case 'AVAILABLE':
        return <span className="badge badge-green">AVAILABLE</span>;
      case 'ON_JOB':
        return <span className="badge badge-blue">ON ACTIVE JOB</span>;
      case 'DEFECT':
        return <span className="badge badge-red">DEFECT REPORTED</span>;
      default:
        return <span className="badge badge-neutral">{st}</span>;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '0.5px' }}>
            FLEET & VEHICLE PRE-START DEFECTS
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Monitor prime movers, rigid trucks, pre-start checklists and active mechanical defects.
          </p>
        </div>

        {/* Filter Tabs */}
        <div style={{ display: 'flex', gap: '6px' }}>
          {['ALL', 'AVAILABLE', 'ON_JOB', 'DEFECT', 'OFFLINE'].map(st => (
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

      {/* Vehicle Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '16px' }}>
        {filtered.map(v => (
          <div
            key={v.id}
            className="ops-card"
            style={{
              border: v.status === 'DEFECT' ? '1px solid rgba(239, 68, 68, 0.4)' : '1px solid var(--border-subtle)',
              background: v.status === 'DEFECT' ? 'rgba(239, 68, 68, 0.03)' : 'var(--bg-card)'
            }}
          >
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Truck size={20} color="var(--gold-primary)" />
                <div>
                  <div style={{ fontWeight: 800, fontSize: '16px', color: 'var(--text-gold)', fontFamily: 'var(--font-mono)' }}>
                    {v.rego}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                    {v.type}
                  </div>
                </div>
              </div>
              {getStatusBadge(v.status)}
            </div>

            {/* Make / Model & Details */}
            <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '8px' }}>
              {v.makeModel}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)', borderTop: '1px solid var(--border-subtle)', paddingTop: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <User size={13} /> Driver:
                </span>
                <strong style={{ color: 'var(--text-primary)' }}>{v.driverName || 'Unassigned'}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Odometer:</span>
                <strong style={{ fontFamily: 'var(--font-mono)' }}>{v.odometer.toLocaleString()} km</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Clock size={13} /> Pre-Start Check:
                </span>
                <span>{v.lastPreStartAt ? new Date(v.lastPreStartAt).toLocaleDateString() : 'Pending Check'}</span>
              </div>
            </div>

            {/* Defects Section if any */}
            {v.activeDefectCount > 0 && (
              <div style={{ marginTop: '12px', padding: '10px', background: 'var(--bg-main)', borderRadius: '6px', border: '1px solid rgba(239, 68, 68, 0.3)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--status-red)', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <AlertOctagon size={14} /> Active Pre-Start Defect
                  </span>
                  <span className="badge badge-red">{v.activeDefectCount} Defect</span>
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                  Tyre tread damage observed on rear dual outer tyre during pre-start check.
                </div>
                <div style={{ marginTop: '8px', display: 'flex', justifyContent: 'flex-end' }}>
                  <button
                    onClick={() => onUpdateDefectStatus(v.id, 'def-101', 'RESOLVED')}
                    className="btn-primary"
                    style={{ fontSize: '11px', padding: '4px 8px' }}
                  >
                    <CheckCircle2 size={12} /> Mark Defect Resolved
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
