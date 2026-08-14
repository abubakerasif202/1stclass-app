import {
  AlertTriangle,
  Bell,
  CheckCircle2,
  Clock,
  MessageSquare,
  Radio,
  Send,
  Shield,
  Truck,
  User
} from 'lucide-react';
import React, { useState } from 'react';
import { Driver, OperationMessage } from '../types';

interface MessagingViewProps {
  messages: OperationMessage[];
  drivers: Driver[];
  onSendMessage: (payload: { driverId?: string; category: string; content: string; isUrgent?: boolean }) => Promise<void>;
}

export const MessagingView: React.FC<MessagingViewProps> = ({
  messages,
  drivers,
  onSendMessage
}) => {
  const [selectedDriverId, setSelectedDriverId] = useState<string>('');
  const [category, setCategory] = useState<string>('DISPATCH');
  const [content, setContent] = useState('');
  const [isUrgent, setIsUrgent] = useState(false);
  const [isSending, setIsSending] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;

    try {
      setIsSending(true);
      await onSendMessage({
        driverId: selectedDriverId || undefined,
        category: isUrgent ? 'URGENT' : category,
        content: content.trim(),
        isUrgent
      });
      setContent('');
      setIsUrgent(false);
    } finally {
      setIsSending(false);
    }
  };

  const getCategoryBadge = (cat: string, urgent: boolean) => {
    if (urgent || cat === 'URGENT') return <span className="badge badge-red">URGENT ALERT</span>;
    if (cat === 'JOB_UPDATE') return <span className="badge badge-amber">JOB UPDATE</span>;
    if (cat === 'DRIVER_NOTICE') return <span className="badge badge-blue">DRIVER NOTICE</span>;
    return <span className="badge badge-gold">DISPATCH</span>;
  };

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '20px', height: 'calc(100vh - 120px)' }}>
      {/* Messages Feed */}
      <div className="ops-card" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px',
          borderBottom: '1px solid var(--border-subtle)',
          paddingBottom: '12px'
        }}>
          <div>
            <h2 style={{ fontSize: '18px', fontWeight: 800 }}>OPERATIONS MESSAGING LOG</h2>
            <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
              Direct dispatcher-to-driver communications and urgent notices.
            </p>
          </div>
          <span className="badge badge-gold">{messages.length} Messages Logged</span>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {messages.map(msg => {
            const recipientDriver = msg.driverId ? drivers.find(d => d.id === msg.driverId) : null;

            return (
              <div
                key={msg.id}
                style={{
                  padding: '14px',
                  background: msg.isUrgent ? 'rgba(239, 68, 68, 0.05)' : 'var(--bg-surface)',
                  border: msg.isUrgent ? '1px solid rgba(239, 68, 68, 0.3)' : '1px solid var(--border-subtle)',
                  borderRadius: '8px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {getCategoryBadge(msg.category, msg.isUrgent)}
                    <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)' }}>
                      To: {recipientDriver?.name || (msg.driverId ? msg.driverId : 'ALL FLEET DRIVERS (BROADCAST)')}
                    </span>
                  </div>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                    {new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>

                <div style={{ fontSize: '13px', color: 'var(--text-primary)', marginTop: '4px', lineHeight: 1.4 }}>
                  {msg.content}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>
                  <span>Sent by: {msg.senderName}</span>
                  {msg.readAt ? (
                    <span style={{ color: 'var(--status-green)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <CheckCircle2 size={12} /> Read by driver
                    </span>
                  ) : (
                    <span style={{ color: 'var(--text-muted)' }}>Delivered to work phone</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Compose & Send Box */}
      <div className="ops-card" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '16px', color: 'var(--text-gold)' }}>
          COMPOSE DISPATCH MESSAGE
        </h3>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px', flex: 1 }}>
          <div>
            <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              Target Driver
            </label>
            <select
              className="input-field"
              value={selectedDriverId}
              onChange={(e) => setSelectedDriverId(e.target.value)}
            >
              <option value="">-- Broadcast to All Drivers --</option>
              {drivers.map(d => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.shiftStatus}) {d.currentVehicleId ? `[${d.currentVehicleId}]` : ''}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              Message Category
            </label>
            <select
              className="input-field"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="DISPATCH">General Dispatch Instruction</option>
              <option value="JOB_UPDATE">Job / Delivery Window Update</option>
              <option value="DRIVER_NOTICE">Operational Bulletin / Weather</option>
              <option value="URGENT">Urgent Safety / Operations Alert</option>
            </select>
          </div>

          <div>
            <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
              Message Content *
            </label>
            <textarea
              className="input-field"
              rows={5}
              placeholder="Type operational instruction or notice..."
              value={content}
              onChange={(e) => setContent(e.target.value)}
              required
            />
          </div>

          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', cursor: 'pointer', color: isUrgent ? 'var(--status-red)' : 'var(--text-secondary)' }}>
            <input
              type="checkbox"
              checked={isUrgent}
              onChange={(e) => setIsUrgent(e.target.checked)}
            />
            <strong style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <AlertTriangle size={14} /> Send as High-Priority Urgent Alert
            </strong>
          </label>

          <div style={{ marginTop: 'auto' }}>
            <button
              type="submit"
              className={isUrgent ? 'btn-danger' : 'btn-primary'}
              style={{ width: '100%', justifyContent: 'center' }}
              disabled={isSending || !content.trim()}
            >
              <Send size={16} /> {isSending ? 'Sending Push...' : 'Send Message & Notify'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
