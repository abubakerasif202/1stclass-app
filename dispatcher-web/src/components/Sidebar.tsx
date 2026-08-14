import {
  AlertTriangle,
  CheckCircle2,
  FileText,
  History,
  LayoutDashboard,
  MapPin,
  MessageSquare,
  Package,
  Settings,
  Truck,
  Users
} from 'lucide-react';
import React from 'react';

export type TabKey =
  | 'dashboard'
  | 'live-map'
  | 'jobs'
  | 'drivers'
  | 'vehicles'
  | 'incidents'
  | 'pod'
  | 'messages'
  | 'audit'
  | 'settings';

interface SidebarProps {
  activeTab: TabKey;
  onSelectTab: (tab: TabKey) => void;
  openIncidentsCount: number;
  activeDefectsCount: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeTab,
  onSelectTab,
  openIncidentsCount,
  activeDefectsCount
}) => {
  const items: { key: TabKey; label: string; icon: React.ReactNode; badgeCount?: number }[] = [
    { key: 'dashboard', label: 'Operations Dashboard', icon: <LayoutDashboard size={18} /> },
    { key: 'live-map', label: 'Live Fleet Map', icon: <MapPin size={18} /> },
    { key: 'jobs', label: 'Manifest & Jobs', icon: <Package size={18} /> },
    { key: 'drivers', label: 'Driver Operations', icon: <Users size={18} /> },
    { key: 'vehicles', label: 'Fleet & Vehicles', icon: <Truck size={18} />, badgeCount: activeDefectsCount },
    { key: 'incidents', label: 'Incidents & Alerts', icon: <AlertTriangle size={18} />, badgeCount: openIncidentsCount },
    { key: 'pod', label: 'POD Review Centre', icon: <CheckCircle2 size={18} /> },
    { key: 'messages', label: 'Operations Messaging', icon: <MessageSquare size={18} /> },
    { key: 'audit', label: 'Audit Trail', icon: <History size={18} /> },
    { key: 'settings', label: 'System & Devices', icon: <Settings size={18} /> }
  ];

  return (
    <aside style={{
      width: '240px',
      background: 'var(--bg-surface)',
      borderRight: '1px solid var(--border-subtle)',
      display: 'flex',
      flexDirection: 'column',
      padding: '16px 8px'
    }}>
      <div style={{
        fontSize: '11px',
        fontWeight: 700,
        color: 'var(--text-muted)',
        letterSpacing: '1px',
        padding: '0 12px 12px 12px'
      }}>
        NAVIGATION
      </div>

      <nav style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1 }}>
        {items.map(item => {
          const isActive = activeTab === item.key;
          return (
            <button
              key={item.key}
              onClick={() => onSelectTab(item.key)}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 12px',
                borderRadius: '6px',
                border: 'none',
                background: isActive ? 'var(--bg-card-hover)' : 'transparent',
                color: isActive ? 'var(--gold-primary)' : 'var(--text-secondary)',
                fontWeight: isActive ? 600 : 500,
                fontSize: '13px',
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'all 0.15s',
                borderLeft: isActive ? '3px solid var(--gold-primary)' : '3px solid transparent'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                {item.icon}
                <span>{item.label}</span>
              </div>
              {item.badgeCount !== undefined && item.badgeCount > 0 && (
                <span className="badge badge-red" style={{ fontSize: '10px', padding: '1px 6px' }}>
                  {item.badgeCount}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      <div style={{
        padding: '12px',
        background: 'var(--bg-main)',
        borderRadius: '8px',
        border: '1px solid var(--border-subtle)',
        marginTop: 'auto'
      }}>
        <div style={{ fontSize: '11px', color: 'var(--text-gold)', fontWeight: 600 }}>
          1ST CLASS EXPRESS TMS
        </div>
        <div style={{ fontSize: '10px', color: 'var(--text-muted)', marginTop: '2px' }}>
          Pilot Build v1.0.0 (Australia)
        </div>
      </div>
    </aside>
  );
};
