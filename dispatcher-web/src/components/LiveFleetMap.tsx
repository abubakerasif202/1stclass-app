import {
  Battery,
  Compass,
  Filter,
  Layers,
  MapPin,
  Maximize2,
  Navigation,
  Radio,
  Search,
  Truck,
  Zap
} from 'lucide-react';
import React, { useState } from 'react';
import { FleetLocationItem } from '../types';

interface LiveFleetMapProps {
  fleet: FleetLocationItem[];
  selectedDriverId: string | null;
  onSelectDriver: (driverId: string | null) => void;
}

type ViewRegion = 'ALL' | 'SYDNEY' | 'MELBOURNE' | 'BRISBANE' | 'ADELAIDE' | 'PERTH';

export const LiveFleetMap: React.FC<LiveFleetMapProps> = ({
  fleet,
  selectedDriverId,
  onSelectDriver
}) => {
  const [region, setRegion] = useState<ViewRegion>('ALL');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Regions bounding boxes in Australia [minLat, maxLat, minLng, maxLng]
  const viewBounds: Record<ViewRegion, { minLat: number; maxLat: number; minLng: number; maxLng: number; label: string }> = {
    ALL: { minLat: -43.5, maxLat: -11.0, minLng: 113.0, maxLng: 154.0, label: 'National Australia' },
    SYDNEY: { minLat: -34.3, maxLat: -33.4, minLng: 150.5, maxLng: 151.6, label: 'Sydney & NSW' },
    MELBOURNE: { minLat: -38.3, maxLat: -37.4, minLng: 144.4, maxLng: 145.5, label: 'Melbourne & VIC' },
    BRISBANE: { minLat: -28.2, maxLat: -26.8, minLng: 152.4, maxLng: 153.6, label: 'Brisbane & SE QLD' },
    ADELAIDE: { minLat: -35.3, maxLat: -34.4, minLng: 138.2, maxLng: 139.0, label: 'Adelaide & SA' },
    PERTH: { minLat: -32.4, maxLat: -31.5, minLng: 115.4, maxLng: 116.3, label: 'Perth & WA' }
  };

  const currentBounds = viewBounds[region];

  // Map lat/lng into SVG 0-100% viewport
  const project = (lat: number, lng: number) => {
    const x = ((lng - currentBounds.minLng) / (currentBounds.maxLng - currentBounds.minLng)) * 100;
    const y = ((currentBounds.maxLat - lat) / (currentBounds.maxLat - currentBounds.minLat)) * 100;
    return { x: Math.max(5, Math.min(95, x)), y: Math.max(5, Math.min(95, y)) };
  };

  const filteredFleet = fleet.filter(item => {
    if (filterStatus !== 'ALL') {
      if (filterStatus === 'MOVING' && item.speedKmh <= 5) return false;
      if (filterStatus === 'STATIONARY' && item.speedKmh > 5) return false;
      if (filterStatus === 'ACTIVE_JOB' && !item.jobId) return false;
      if (filterStatus === 'STALE' && !item.isStale) return false;
    }
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().trim();
      return (
        item.driverName.toLowerCase().includes(q) ||
        item.vehicleRego.toLowerCase().includes(q) ||
        item.jobReference.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const getStatusColor = (item: FleetLocationItem) => {
    if (item.isStale) return 'var(--status-amber)';
    if (item.movementStatus === 'MOVING') return 'var(--status-green)';
    if (item.movementStatus === 'AT_PICKUP' || item.movementStatus === 'AT_DELIVERY') return 'var(--gold-primary)';
    if (item.shiftStatus === 'ON_BREAK') return 'var(--status-blue)';
    if (item.shiftStatus === 'OFF_DUTY') return 'var(--text-muted)';
    return 'var(--status-blue)';
  };

  return (
    <div className="ops-card" style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: '16px', position: 'relative' }}>
      {/* Map Header Controls */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        marginBottom: '16px',
        borderBottom: '1px solid var(--border-subtle)',
        paddingBottom: '12px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <MapPin size={18} color="var(--gold-primary)" />
          <span style={{ fontWeight: 700, fontSize: '15px' }}>
            NATIONAL FLEET LIVE GPS MAP
          </span>
          <span className="badge badge-gold" style={{ fontSize: '11px' }}>
            {filteredFleet.length} Vehicles Tracked
          </span>
        </div>

        {/* Region & Filter Buttons */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          {/* Search Box */}
          <div style={{ position: 'relative', width: '180px' }}>
            <Search size={14} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--text-muted)' }} />
            <input
              type="text"
              placeholder="Driver / Rego / Job..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input-field"
              style={{ paddingLeft: '30px', paddingRight: '8px', height: '34px', fontSize: '12px' }}
            />
          </div>

          {/* Region Selector */}
          <div style={{ display: 'flex', background: 'var(--bg-surface)', borderRadius: '6px', border: '1px solid var(--border-subtle)', overflow: 'hidden' }}>
            {(['ALL', 'SYDNEY', 'MELBOURNE', 'BRISBANE', 'ADELAIDE', 'PERTH'] as ViewRegion[]).map(r => (
              <button
                key={r}
                onClick={() => setRegion(r)}
                style={{
                  padding: '6px 10px',
                  background: region === r ? 'var(--gold-primary)' : 'transparent',
                  color: region === r ? '#0c0e12' : 'var(--text-secondary)',
                  border: 'none',
                  fontSize: '11px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                {r}
              </button>
            ))}
          </div>

          {/* Status Filter */}
          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            style={{
              background: 'var(--bg-input)',
              color: 'var(--text-primary)',
              border: '1px solid var(--border-subtle)',
              borderRadius: '6px',
              padding: '6px 10px',
              fontSize: '12px',
              fontWeight: 500,
              cursor: 'pointer'
            }}
          >
            <option value="ALL">Status: All</option>
            <option value="MOVING">Status: In Transit / Moving</option>
            <option value="STATIONARY">Status: Stationary</option>
            <option value="ACTIVE_JOB">Status: On Active Job</option>
            <option value="STALE">Status: GPS Stale (&gt;2m)</option>
          </select>
        </div>
      </div>

      {/* SVG Interactive Map Canvas */}
      <div style={{
        flex: 1,
        background: '#090b0e',
        borderRadius: '8px',
        border: '1px solid var(--border-subtle)',
        position: 'relative',
        overflow: 'hidden',
        minHeight: '450px'
      }}>
        {/* Radar / Grid Background Overlay */}
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundImage: 'radial-gradient(circle, rgba(212, 175, 55, 0.05) 1px, transparent 1px)',
          backgroundSize: '30px 30px',
          opacity: 0.6
        }} />

        {/* Australia Continental Outline Representation */}
        <svg
          style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}
          viewBox="0 0 1000 600"
          preserveAspectRatio="none"
        >
          {/* Subtle Interstate Route Lines */}
          <path
            d="M 680,480 L 800,380 L 840,240"
            fill="none"
            stroke="rgba(212, 175, 55, 0.15)"
            strokeWidth="1.5"
            strokeDasharray="4 4"
          />
          <path
            d="M 680,480 L 520,440 L 160,340"
            fill="none"
            stroke="rgba(212, 175, 55, 0.15)"
            strokeWidth="1.5"
            strokeDasharray="4 4"
          />
        </svg>

        {/* Major Hub Cities */}
        {[
          { name: 'SYDNEY', lat: -33.86, lng: 151.20 },
          { name: 'MELBOURNE', lat: -37.81, lng: 144.96 },
          { name: 'BRISBANE', lat: -27.47, lng: 153.02 },
          { name: 'ADELAIDE', lat: -34.92, lng: 138.60 },
          { name: 'PERTH', lat: -31.95, lng: 115.86 }
        ].map(city => {
          const pos = project(city.lat, city.lng);
          return (
            <div
              key={city.name}
              style={{
                position: 'absolute',
                left: `${pos.x}%`,
                top: `${pos.y}%`,
                transform: 'translate(-50%, -50%)',
                pointerEvents: 'none',
                opacity: 0.4
              }}
            >
              <div style={{ width: '6px', height: '6px', background: 'var(--text-muted)', borderRadius: '50%' }} />
              <div style={{ fontSize: '9px', fontWeight: 700, color: 'var(--text-muted)', marginTop: '2px', letterSpacing: '0.5px' }}>
                {city.name}
              </div>
            </div>
          );
        })}

        {/* Real-time Fleet Vehicles Pins */}
        {filteredFleet.map(item => {
          const pos = project(item.latitude, item.longitude);
          const isSelected = selectedDriverId === item.driverId;
          const markerColor = getStatusColor(item);

          return (
            <div
              key={item.driverId}
              onClick={() => onSelectDriver(isSelected ? null : item.driverId)}
              style={{
                position: 'absolute',
                left: `${pos.x}%`,
                top: `${pos.y}%`,
                transform: 'translate(-50%, -50%)',
                cursor: 'pointer',
                zIndex: isSelected ? 20 : 10,
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)'
              }}
            >
              {/* Pulse Ring if Selected or Moving */}
              {(isSelected || item.movementStatus === 'MOVING') && (
                <div style={{
                  position: 'absolute',
                  top: '-8px',
                  left: '-8px',
                  right: '-8px',
                  bottom: '-8px',
                  borderRadius: '50%',
                  border: `2px solid ${markerColor}`,
                  animation: 'pulse 2s infinite',
                  opacity: 0.7
                }} />
              )}

              {/* Marker Pin Head */}
              <div style={{
                background: isSelected ? 'var(--gold-primary)' : 'var(--bg-surface)',
                color: isSelected ? '#0c0e12' : markerColor,
                border: `2px solid ${markerColor}`,
                borderRadius: '8px',
                padding: '4px 8px',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.6)',
                fontWeight: 700,
                fontSize: '11px',
                whiteSpace: 'nowrap'
              }}>
                <Truck size={14} />
                <span>{item.vehicleRego}</span>
                <span style={{
                  fontSize: '9px',
                  padding: '1px 4px',
                  borderRadius: '3px',
                  background: isSelected ? 'rgba(0,0,0,0.2)' : 'rgba(255,255,255,0.1)'
                }}>
                  {item.speedKmh} km/h
                </span>
              </div>

              {/* Driver Tooltip Preview */}
              <div style={{
                fontSize: '10px',
                color: 'var(--text-secondary)',
                fontWeight: 600,
                textAlign: 'center',
                marginTop: '2px',
                textShadow: '0 1px 3px black'
              }}>
                {item.driverName}
              </div>
            </div>
          );
        })}
      </div>

      {/* Map Legend Footer */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginTop: '12px',
        paddingTop: '8px',
        borderTop: '1px solid var(--border-subtle)',
        fontSize: '11px',
        color: 'var(--text-muted)',
        flexWrap: 'wrap',
        gap: '8px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--status-green)' }} />
            In Transit / Moving
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--gold-primary)' }} />
            At Pickup / Delivery
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--status-blue)' }} />
            Stationary / Standby
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--status-amber)' }} />
            Stale Telemetry (&gt;2m)
          </span>
        </div>

        <div>
          Active Map Bounding: <strong style={{ color: 'var(--text-primary)' }}>{currentBounds.label}</strong>
        </div>
      </div>
    </div>
  );
};
