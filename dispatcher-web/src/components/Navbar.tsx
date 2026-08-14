import { Activity, Bell, LogOut, Radio, Shield, User } from 'lucide-react';
import React from 'react';
import { DispatchUser } from '../types';

interface NavbarProps {
  user: DispatchUser | null;
  connectionStatus: 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED';
  activeJobsCount: number;
  openIncidentsCount: number;
  onOpenAlerts: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  user,
  connectionStatus,
  activeJobsCount,
  openIncidentsCount,
  onOpenAlerts,
  onLogout
}) => {
  return (
    <header style={{
      height: '64px',
      background: 'var(--bg-surface)',
      borderBottom: '1px solid var(--border-subtle)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 24px',
      zIndex: 100
    }}>
      {/* Brand & Fleet Live Status */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            background: 'var(--gold-primary)',
            color: '#0c0e12',
            fontWeight: 800,
            fontSize: '14px',
            padding: '4px 8px',
            borderRadius: '4px',
            letterSpacing: '1px'
          }}>
            1ST CLASS
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '15px', letterSpacing: '0.5px' }}>
              OPERATIONS CONTROL CENTRE
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
              LIVE NATIONAL FLEET & FREIGHT DISPATCH
            </div>
          </div>
        </div>

        {/* Realtime Connection Indicator */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '4px 12px',
          background: 'var(--bg-card)',
          borderRadius: '20px',
          border: '1px solid var(--border-subtle)'
        }}>
          <Radio size={14} color={connectionStatus === 'CONNECTED' ? 'var(--status-green)' : 'var(--status-amber)'} />
          <span style={{ fontSize: '12px', fontWeight: 600, color: connectionStatus === 'CONNECTED' ? 'var(--status-green)' : 'var(--status-amber)' }}>
            {connectionStatus === 'CONNECTED'
              ? 'TELEMETRY LIVE'
              : connectionStatus === 'RECONNECTING' ? 'RECONNECTING...' : 'DISCONNECTED'}
          </span>
        </div>
      </div>

      {/* Right Controls: Quick Stats, Alerts, Role Selector, User Profile */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        {/* Active Ops Indicators */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div className="badge badge-blue">
            <Activity size={12} style={{ marginRight: '4px' }} />
            {activeJobsCount} Active Jobs
          </div>
          {openIncidentsCount > 0 && (
            <button
              onClick={onOpenAlerts}
              className="badge badge-red"
              style={{ cursor: 'pointer', border: '1px solid rgba(239, 68, 68, 0.4)' }}
            >
              <Bell size={12} style={{ marginRight: '4px' }} />
              {openIncidentsCount} Incidents
            </button>
          )}
        </div>

        {/* Role is server-issued and cannot be changed by the client. */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Shield size={14} color="var(--gold-primary)" />
          <span className="badge badge-gold">ROLE: {user?.role || 'UNKNOWN'}</span>
        </div>
        <button className="btn-secondary" onClick={onLogout} aria-label="Sign out">
          <LogOut size={14} /> Sign out
        </button>

        {/* User Badge */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '6px 12px',
          background: 'var(--bg-card)',
          borderRadius: '6px',
          border: '1px solid var(--border-subtle)'
        }}>
          <User size={14} color="var(--text-secondary)" />
          <span style={{ fontSize: '13px', fontWeight: 500 }}>{user?.name || 'Operations'}</span>
        </div>
      </div>
    </header>
  );
};
