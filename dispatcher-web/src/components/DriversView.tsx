import {
  Clock,
  MapPin,
  MessageSquare,
  Phone,
  Radio,
  Shield,
  Smartphone,
  Truck,
  User,
  UserCheck
} from 'lucide-react';
import React, { useState } from 'react';
import { Driver } from '../types';

interface DriversViewProps {
  drivers: Driver[];
  onSendMessage: (driverId: string) => void;
}

export const DriversView: React.FC<DriversViewProps> = ({
  drivers,
  onSendMessage
}) => {
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');

  const filtered = drivers.filter(d => {
    if (selectedStatus === 'ALL') return true;
    return d.shiftStatus === selectedStatus;
  });

  const getShiftBadge = (st: string) => {
    switch (st) {
      case 'ON_DUTY':
        return <span className="badge badge-green">ON DUTY</span>;
      case 'ON_BREAK':
        return <span className="badge badge-amber">ON BREAK</span>;
      case 'OFF_DUTY':
        return <span className="badge badge-neutral">OFF DUTY</span>;
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
            DRIVER OPERATIONS & SHIFT ROSTER
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time driver shift status, vehicle allocations, active jobs and app connectivity.
          </p>
        </div>

        {/* Shift Filter Tabs */}
        <div style={{ display: 'flex', gap: '6px' }}>
          {['ALL', 'ON_DUTY', 'ON_BREAK', 'OFF_DUTY'].map(st => (
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
              {st.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {/* Driver List Table */}
      <div className="ops-table-container">
        <table className="ops-table">
          <thead>
            <tr>
              <th>Driver</th>
              <th>Driver ID</th>
              <th>Duty Status</th>
              <th>Allocated Vehicle</th>
              <th>Active Assignment</th>
              <th>Driver Phone</th>
              <th>App Connectivity</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(driver => (
              <tr key={driver.id}>
                <td>
                  <div style={{ fontWeight: 700, fontSize: '14px' }}>{driver.name}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Lic: {driver.licenseNumber}</div>
                </td>
                <td>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-gold)' }}>
                    {driver.id}
                  </span>
                </td>
                <td>{getShiftBadge(driver.shiftStatus)}</td>
                <td>
                  {driver.currentVehicleId ? (
                    <div>
                      <strong style={{ color: 'var(--text-gold)' }}>{driver.vehicleRego || driver.currentVehicleId}</strong>
                    </div>
                  ) : (
                    <span style={{ color: 'var(--text-muted)' }}>No vehicle assigned</span>
                  )}
                </td>
                <td>
                  {driver.activeJobId ? (
                    <div>
                      <strong style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                        {driver.activeJobReference || driver.activeJobId}
                      </strong>
                    </div>
                  ) : (
                    <span className="badge badge-neutral">AVAILABLE</span>
                  )}
                </td>
                <td>
                  <a href={`tel:${driver.phone.replace(/\s+/g, '')}`} style={{ color: 'var(--text-secondary)', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Phone size={13} /> {driver.phone}
                  </a>
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Smartphone size={13} color="var(--text-muted)" />
                    <span style={{ fontSize: '12px' }}>v{driver.appVersion}</span>
                    <span className={`badge ${driver.pushToken ? 'badge-green' : 'badge-neutral'}`} style={{ fontSize: '9px', padding: '1px 4px' }}>
                      {driver.pushToken ? 'FCM READY' : 'NO PUSH'}
                    </span>
                  </div>
                </td>
                <td style={{ textAlign: 'right' }}>
                  <button
                    onClick={() => onSendMessage(driver.id)}
                    className="btn-secondary"
                    style={{ padding: '4px 10px', fontSize: '12px' }}
                  >
                    <MessageSquare size={14} /> Message
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
