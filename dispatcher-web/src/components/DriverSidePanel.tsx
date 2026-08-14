import {
  AlertTriangle,
  Battery,
  Clock,
  ExternalLink,
  MapPin,
  MessageSquare,
  Navigation,
  Package,
  Phone,
  Radio,
  Truck,
  User,
  X,
  Zap
} from 'lucide-react';
import React from 'react';
import { Driver, FleetLocationItem, Job, Vehicle } from '../types';

interface DriverSidePanelProps {
  driverId: string;
  fleetItem?: FleetLocationItem;
  driver?: Driver;
  vehicle?: Vehicle;
  activeJob?: Job;
  onClose: () => void;
  onViewJob: (jobId: string) => void;
  onSendMessage: (driverId: string) => void;
}

export const DriverSidePanel: React.FC<DriverSidePanelProps> = ({
  driverId,
  fleetItem,
  driver,
  vehicle,
  activeJob,
  onClose,
  onViewJob,
  onSendMessage
}) => {
  const driverName = driver?.name || fleetItem?.driverName || driverId;
  const vehicleRego = vehicle?.rego || fleetItem?.vehicleRego || 'Unassigned';
  const phone = driver?.phone || fleetItem?.driverPhone || '';

  return (
    <div style={{
      width: '360px',
      background: 'var(--bg-surface)',
      borderLeft: '1px solid var(--border-subtle)',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      zIndex: 50,
      boxShadow: '-10px 0 30px rgba(0, 0, 0, 0.5)'
    }}>
      {/* Header */}
      <div style={{
        padding: '16px 20px',
        borderBottom: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        background: 'var(--bg-card)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            background: 'var(--gold-primary)',
            color: '#0c0e12',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 800
          }}>
            {driverName.substring(0, 2).toUpperCase()}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '15px' }}>{driverName}</div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
              {driverId}
            </div>
          </div>
        </div>

        <button
          onClick={onClose}
          style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
        >
          <X size={20} />
        </button>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* Duty & Telemetry Status Card */}
        <div className="ops-card" style={{ padding: '14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
            <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)' }}>SHIFT STATUS</span>
            <span className={`badge ${driver?.shiftStatus === 'ON_DUTY' ? 'badge-green' : driver?.shiftStatus === 'ON_BREAK' ? 'badge-amber' : 'badge-neutral'}`}>
              {driver?.shiftStatus || fleetItem?.shiftStatus || 'OFF_DUTY'}
            </span>
          </div>

          {fleetItem && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12px', color: 'var(--text-secondary)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Radio size={14} color="var(--status-green)" /> GPS Telemetry:
                </span>
                <strong style={{ color: fleetItem.isStale ? 'var(--status-amber)' : 'var(--status-green)' }}>
                  {fleetItem.isStale ? `Stale (${fleetItem.ageSeconds}s ago)` : `Live (${fleetItem.ageSeconds}s ago)`}
                </strong>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Navigation size={14} color="var(--gold-primary)" /> Speed:
                </span>
                <strong>{fleetItem.speedKmh} km/h</strong>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Battery size={14} color="var(--text-muted)" /> Phone Battery:
                </span>
                <strong>{fleetItem.batteryLevel !== null ? `${fleetItem.batteryLevel}%` : 'N/A'}</strong>
              </div>
            </div>
          )}
        </div>

        {/* Vehicle Information */}
        <div className="ops-card" style={{ padding: '14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Truck size={16} color="var(--gold-primary)" />
              <span style={{ fontWeight: 700, fontSize: '13px' }}>ASSIGNED VEHICLE</span>
            </div>
            <span className="badge badge-gold">{vehicleRego}</span>
          </div>
          <div style={{ fontSize: '13px', color: 'var(--text-primary)', fontWeight: 500 }}>
            {vehicle?.makeModel || fleetItem?.vehicleModel || 'No vehicle allocated'}
          </div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Odometer: {vehicle?.odometer ? `${vehicle.odometer.toLocaleString()} km` : 'N/A'}
          </div>
        </div>

        {/* Active Job Card */}
        <div className="ops-card" style={{ padding: '14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Package size={16} color="var(--status-blue)" />
              <span style={{ fontWeight: 700, fontSize: '13px' }}>ACTIVE ASSIGNMENT</span>
            </div>
            {activeJob && (
              <span className="badge badge-blue">
                {activeJob.status}
              </span>
            )}
          </div>

          {activeJob ? (
            <div>
              <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-gold)', fontFamily: 'var(--font-mono)' }}>
                {activeJob.reference}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                {activeJob.pickup.suburb} → {activeJob.delivery.suburb}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                {activeJob.freightDescription} ({activeJob.itemCount} items)
              </div>
              <div style={{ marginTop: '12px' }}>
                <button
                  onClick={() => onViewJob(activeJob.id)}
                  className="btn-secondary"
                  style={{ width: '100%', justifyContent: 'center', padding: '6px' }}
                >
                  <ExternalLink size={14} /> Open Full Job Record
                </button>
              </div>
            </div>
          ) : (
            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
              No job currently in progress. Driver is available for assignment.
            </div>
          )}
        </div>

        {/* Contact & Dispatch Quick Actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: 'auto' }}>
          <button
            onClick={() => onSendMessage(driverId)}
            className="btn-primary"
            style={{ width: '100%', justifyContent: 'center' }}
          >
            <MessageSquare size={16} /> Send Dispatch Message
          </button>

          {phone && (
            <a
              href={`tel:${phone.replace(/\s+/g, '')}`}
              className="btn-secondary"
              style={{ width: '100%', justifyContent: 'center', textDecoration: 'none' }}
            >
              <Phone size={16} /> Call Driver ({phone})
            </a>
          )}
        </div>
      </div>
    </div>
  );
};
