import {
  AlertTriangle,
  Calendar,
  CheckCircle2,
  Clock,
  ExternalLink,
  FileText,
  MapPin,
  Package,
  Phone,
  Shield,
  Truck,
  User,
  X
} from 'lucide-react';
import React, { useState } from 'react';
import { api } from '../api';
import { Driver, Job, Vehicle } from '../types';

interface JobDetailModalProps {
  job: Job;
  driver?: Driver;
  vehicle?: Vehicle;
  onClose: () => void;
  onOpenEdit: () => void;
  onOpenReassign: () => void;
  onOpenCancel: () => void;
}

export const JobDetailModal: React.FC<JobDetailModalProps> = ({
  job,
  driver,
  vehicle,
  onClose,
  onOpenEdit,
  onOpenReassign,
  onOpenCancel
}) => {
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'TIMELINE' | 'POD'>('OVERVIEW');

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '780px' }}>
        {/* Header */}
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h2 style={{ fontSize: '18px', fontWeight: 800, fontFamily: 'var(--font-mono)', color: 'var(--text-gold)' }}>
                  {job.reference}
                </h2>
                <span className={`badge ${job.priority === 'URGENT' ? 'badge-red' : 'badge-neutral'}`}>
                  {job.priority}
                </span>
                <span className="badge badge-blue">{job.status}</span>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                  Rev {job.revision}
                </span>
              </div>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                {job.pickup.suburb} → {job.delivery.suburb}
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Tab Navigation */}
        <div style={{ display: 'flex', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-main)', padding: '0 24px' }}>
          {[
            { key: 'OVERVIEW', label: 'Job Overview & Manifest' },
            { key: 'TIMELINE', label: `Timeline & Audit (${job.timeline.length})` },
            { key: 'POD', label: job.pod ? 'Proof of Delivery (Complete)' : 'Proof of Delivery (Pending)' }
          ].map(t => (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key as any)}
              style={{
                padding: '12px 16px',
                border: 'none',
                background: 'transparent',
                color: activeTab === t.key ? 'var(--gold-primary)' : 'var(--text-secondary)',
                fontWeight: activeTab === t.key ? 700 : 500,
                fontSize: '13px',
                borderBottom: activeTab === t.key ? '2px solid var(--gold-primary)' : '2px solid transparent',
                cursor: 'pointer'
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Body */}
        <div className="modal-body">
          {activeTab === 'OVERVIEW' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Pickup & Delivery Grid */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                {/* Pickup Box */}
                <div className="ops-card" style={{ padding: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px', color: 'var(--gold-primary)' }}>
                    <MapPin size={16} />
                    <strong style={{ fontSize: '13px' }}>PICKUP POINT</strong>
                  </div>
                  <div style={{ fontWeight: 700, fontSize: '14px' }}>{job.pickup.companyName}</div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>{job.pickup.address}</div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>{job.pickup.suburb}</div>
                  <div style={{ fontSize: '12px', color: 'var(--gold-dark)', marginTop: '6px' }}>
                    Window: {job.pickupWindowStart} – {job.pickupWindowEnd}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                    Contact: {job.pickup.contactName} ({job.pickup.contactPhone})
                  </div>
                </div>

                {/* Delivery Box */}
                <div className="ops-card" style={{ padding: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px', color: 'var(--status-green)' }}>
                    <MapPin size={16} />
                    <strong style={{ fontSize: '13px' }}>DELIVERY DESTINATION</strong>
                  </div>
                  <div style={{ fontWeight: 700, fontSize: '14px' }}>{job.delivery.companyName}</div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>{job.delivery.address}</div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>{job.delivery.suburb}</div>
                  <div style={{ fontSize: '12px', color: 'var(--gold-dark)', marginTop: '6px' }}>
                    Window: {job.deliveryWindowStart} – {job.deliveryWindowEnd}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                    Contact: {job.delivery.contactName} ({job.delivery.contactPhone})
                  </div>
                </div>
              </div>

              {/* Freight & Special Instructions */}
              <div className="ops-card" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                  <Package size={16} color="var(--gold-primary)" />
                  <strong style={{ fontSize: '13px' }}>FREIGHT MANIFEST & REQUIREMENTS</strong>
                </div>
                <div style={{ fontSize: '14px', fontWeight: 600 }}>{job.freightDescription}</div>
                <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  Quantity: {job.itemCount} items / pallets
                </div>
                {job.dangerousGoods && (
                  <div className="badge badge-red" style={{ marginTop: '6px' }}>
                    DANGEROUS GOODS / HAZMAT REGULATED
                  </div>
                )}
                {job.specialInstructions && (
                  <div style={{
                    marginTop: '10px',
                    padding: '10px',
                    background: 'var(--bg-main)',
                    borderRadius: '6px',
                    border: '1px solid var(--border-subtle)',
                    fontSize: '12px',
                    color: 'var(--text-secondary)'
                  }}>
                    <strong style={{ color: 'var(--text-gold)' }}>Special Instructions: </strong>
                    {job.specialInstructions}
                  </div>
                )}
              </div>

              {/* Assigned Fleet */}
              <div className="ops-card" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Truck size={18} color="var(--gold-primary)" />
                    <div>
                      <strong style={{ fontSize: '13px' }}>ASSIGNED DRIVER & VEHICLE</strong>
                      <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                        {driver ? `${driver.name} (${driver.phone})` : 'No driver allocated'}
                      </div>
                    </div>
                  </div>
                  {vehicle && (
                    <span className="badge badge-gold">{vehicle.rego} — {vehicle.makeModel}</span>
                  )}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'TIMELINE' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {job.timeline.map((event, idx) => (
                <div
                  key={event.id || idx}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '12px 16px',
                    background: 'var(--bg-main)',
                    borderRadius: '8px',
                    border: '1px solid var(--border-subtle)'
                  }}
                >
                  <div style={{
                    width: '28px',
                    height: '28px',
                    borderRadius: '50%',
                    background: 'var(--bg-surface)',
                    border: '1px solid var(--border-strong)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--gold-primary)'
                  }}>
                    <Clock size={14} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span className="badge badge-gold" style={{ fontSize: '11px' }}>{event.type}</span>
                      <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        {new Date(event.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: 500, marginTop: '4px' }}>
                      {event.description}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                      Actor: {event.actor} {event.lat && event.lng ? `· GPS: (${event.lat.toFixed(4)}, ${event.lng.toFixed(4)})` : ''}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === 'POD' && (
            <div>
              {job.pod ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <div className="ops-card" style={{ padding: '16px', border: '1px solid rgba(16, 185, 129, 0.4)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                      <CheckCircle2 size={20} color="var(--status-green)" />
                      <strong style={{ fontSize: '15px' }}>PROOF OF DELIVERY (COMPLETE & VERIFIED)</strong>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '13px' }}>
                      <div>
                        <span style={{ color: 'var(--text-muted)' }}>Signatory / Recipient:</span>
                        <div style={{ fontWeight: 700 }}>{job.pod.recipientName}</div>
                      </div>
                      <div>
                        <span style={{ color: 'var(--text-muted)' }}>Delivered Timestamp:</span>
                        <div style={{ fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                          {new Date(job.pod.completedAt).toLocaleString()}
                        </div>
                      </div>
                    </div>

                    {job.pod.driverNotes && (
                      <div style={{ marginTop: '12px', padding: '8px 12px', background: 'var(--bg-main)', borderRadius: '6px', fontSize: '12px' }}>
                        <span style={{ color: 'var(--text-muted)' }}>Driver Delivery Notes: </span>
                        {job.pod.driverNotes}
                      </div>
                    )}
                  </div>

                  {/* Signature Viewer */}
                  {job.pod.signatureEvidenceId && (
                    <div className="ops-card" style={{ padding: '16px' }}>
                      <strong style={{ fontSize: '13px', display: 'block', marginBottom: '8px' }}>
                        RECIPIENT SIGNATURE
                      </strong>
                      <div style={{
                        background: '#ffffff',
                        padding: '16px',
                        borderRadius: '6px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        maxHeight: '140px'
                      }}>
                        <img
                          src={api.evidenceUrl(job.pod.signatureEvidenceId)}
                          alt="Recipient Signature"
                          style={{ maxHeight: '100px', maxWidth: '100%', objectFit: 'contain' }}
                        />
                      </div>
                    </div>
                  )}

                  {/* Delivery Photos */}
                  {job.pod.photoEvidenceIds && job.pod.photoEvidenceIds.length > 0 && (
                    <div className="ops-card" style={{ padding: '16px' }}>
                      <strong style={{ fontSize: '13px', display: 'block', marginBottom: '8px' }}>
                        DELIVERY EVIDENCE PHOTOGRAPHS ({job.pod.photoEvidenceIds.length})
                      </strong>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '8px' }}>
                        {job.pod.photoEvidenceIds.map((evidenceId, idx) => (
                          <img
                            key={idx}
                            src={api.evidenceUrl(evidenceId)}
                            alt={`POD Photo ${idx + 1}`}
                            style={{ width: '100%', height: '100px', objectFit: 'cover', borderRadius: '6px' }}
                          />
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '36px 0', color: 'var(--text-muted)' }}>
                  <Package size={36} style={{ marginBottom: '12px', opacity: 0.5 }} />
                  <div>Proof of Delivery evidence has not been submitted yet.</div>
                  <div style={{ fontSize: '12px', marginTop: '4px' }}>
                    POD evidence is captured automatically on the driver work phone upon delivery.
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="modal-footer">
          {job.status !== 'COMPLETED' && job.status !== 'CANCELLED' && (
            <>
              <button onClick={onOpenCancel} className="btn-danger">
                Cancel Job
              </button>
              <button onClick={onOpenReassign} className="btn-secondary">
                Reassign Fleet
              </button>
              <button onClick={onOpenEdit} className="btn-secondary">
                Edit Job Details
              </button>
            </>
          )}
          <button onClick={onClose} className="btn-primary">
            Close Record
          </button>
        </div>
      </div>
    </div>
  );
};
