import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  MessageSquare,
  Package,
  Radio,
  Truck
} from 'lucide-react';
import React from 'react';

export interface ActivityItem {
  id: string;
  type: string;
  title: string;
  description: string;
  timestamp: number;
}

interface ActivityFeedProps {
  items: ActivityItem[];
}

export const ActivityFeed: React.FC<ActivityFeedProps> = ({ items }) => {
  const getIcon = (type: string) => {
    switch (type) {
      case 'POD_COMPLETED':
      case 'COMPLETED':
        return <CheckCircle2 size={16} color="var(--status-green)" />;
      case 'INCIDENT':
        return <AlertTriangle size={16} color="var(--status-red)" />;
      case 'DEFECT':
        return <Truck size={16} color="var(--status-amber)" />;
      case 'MESSAGE':
        return <MessageSquare size={16} color="var(--status-blue)" />;
      case 'LOCATION':
        return <Radio size={16} color="var(--text-gold)" />;
      default:
        return <Package size={16} color="var(--text-secondary)" />;
    }
  };

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div className="ops-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: '16px',
        borderBottom: '1px solid var(--border-subtle)',
        paddingBottom: '12px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Clock size={16} color="var(--gold-primary)" />
          <span style={{ fontWeight: 700, fontSize: '14px', letterSpacing: '0.5px' }}>
            LIVE OPERATIONS FEED
          </span>
        </div>
        <span className="badge badge-gold" style={{ fontSize: '10px' }}>
          REALTIME
        </span>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {items.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '24px 0' }}>
            No live events recorded yet.
          </div>
        ) : (
          items.map(item => (
            <div
              key={item.id}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: '12px',
                padding: '10px 12px',
                background: 'var(--bg-surface)',
                borderRadius: '6px',
                border: '1px solid var(--border-subtle)'
              }}
            >
              <div style={{ marginTop: '2px' }}>{getIcon(item.type)}</div>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: '13px', fontWeight: 600 }}>{item.title}</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                    {formatTime(item.timestamp)}
                  </span>
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {item.description}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
