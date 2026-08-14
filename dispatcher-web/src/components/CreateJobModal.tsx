import { AlertTriangle, Plus, X } from 'lucide-react';
import React, { useState } from 'react';
import { Driver, Job, Vehicle } from '../types';

interface CreateJobModalProps {
  drivers: Driver[];
  vehicles: Vehicle[];
  onClose: () => void;
  onSubmit: (payload: Partial<Job>) => Promise<void>;
}

export const CreateJobModal: React.FC<CreateJobModalProps> = ({
  drivers,
  vehicles,
  onClose,
  onSubmit
}) => {
  const [reference, setReference] = useState('');
  const [priority, setPriority] = useState<'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'>('NORMAL');
  const [assignedDriverId, setAssignedDriverId] = useState('');
  const [assignedVehicleId, setAssignedVehicleId] = useState('');

  // Pickup
  const [pickupCompany, setPickupCompany] = useState('');
  const [pickupAddress, setPickupAddress] = useState('');
  const [pickupSuburb, setPickupSuburb] = useState('');
  const [pickupContact, setPickupContact] = useState('');
  const [pickupPhone, setPickupPhone] = useState('');
  const [pickupWindowStart, setPickupWindowStart] = useState('08:00');
  const [pickupWindowEnd, setPickupWindowEnd] = useState('10:00');

  // Delivery
  const [deliveryCompany, setDeliveryCompany] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [deliverySuburb, setDeliverySuburb] = useState('');
  const [deliveryContact, setDeliveryContact] = useState('');
  const [deliveryPhone, setDeliveryPhone] = useState('');
  const [deliveryWindowStart, setDeliveryWindowStart] = useState('12:00');
  const [deliveryWindowEnd, setDeliveryWindowEnd] = useState('14:00');

  // Freight
  const [freightDescription, setFreightDescription] = useState('');
  const [itemCount, setItemCount] = useState('1');
  const [specialInstructions, setSpecialInstructions] = useState('');
  const [dangerousGoods, setDangerousGoods] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reference || !pickupCompany || !pickupAddress || !deliveryCompany || !deliveryAddress || !freightDescription) {
      setError('Please fill in all mandatory job and location fields.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      await onSubmit({
        reference: reference.toUpperCase(),
        priority,
        assignedDriverId: assignedDriverId || null,
        assignedVehicleId: assignedVehicleId || null,
        pickup: {
          companyName: pickupCompany,
          address: pickupAddress,
          suburb: pickupSuburb,
          contactName: pickupContact,
          contactPhone: pickupPhone,
          lat: null,
          lng: null
        },
        delivery: {
          companyName: deliveryCompany,
          address: deliveryAddress,
          suburb: deliverySuburb,
          contactName: deliveryContact,
          contactPhone: deliveryPhone,
          lat: null,
          lng: null
        },
        pickupWindowStart,
        pickupWindowEnd,
        deliveryWindowStart,
        deliveryWindowEnd,
        freightDescription,
        itemCount: parseInt(itemCount, 10) || 1,
        specialInstructions,
        dangerousGoods
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to create job');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '720px' }}>
        <form onSubmit={handleSubmit}>
          <div className="modal-header">
            <h2 style={{ fontSize: '18px', fontWeight: 800, color: 'var(--text-gold)' }}>
              CREATE NEW EXPRESS JOB
            </h2>
            <button
              type="button"
              onClick={onClose}
              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
            >
              <X size={20} />
            </button>
          </div>

          <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {error && (
              <div style={{ padding: '10px 14px', background: 'var(--status-red-bg)', color: 'var(--status-red)', borderRadius: '6px', fontSize: '13px' }}>
                {error}
              </div>
            )}

            {/* General Job Settings */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  Job Reference *
                </label>
                <input
                  type="text"
                  className="input-field"
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                  required
                />
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  Priority Level
                </label>
                <select
                  className="input-field"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as any)}
                >
                  <option value="NORMAL">NORMAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT EXPRESS</option>
                  <option value="LOW">LOW</option>
                </select>
              </div>
            </div>

            {/* Fleet Allocation */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  Assign Driver
                </label>
                <select
                  className="input-field"
                  value={assignedDriverId}
                  onChange={(e) => {
                    setAssignedDriverId(e.target.value);
                    const d = drivers.find(drv => drv.id === e.target.value);
                    if (d?.currentVehicleId) setAssignedVehicleId(d.currentVehicleId);
                  }}
                >
                  <option value="">-- Unassigned (Pool) --</option>
                  {drivers.map(d => (
                    <option key={d.id} value={d.id}>
                      {d.name} ({d.shiftStatus}) {d.currentVehicleId ? `[${d.currentVehicleId}]` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  Assign Vehicle
                </label>
                <select
                  className="input-field"
                  value={assignedVehicleId}
                  onChange={(e) => setAssignedVehicleId(e.target.value)}
                >
                  <option value="">-- Auto Match / Unassigned --</option>
                  {vehicles.map(v => (
                    <option key={v.id} value={v.id}>
                      {v.rego} — {v.makeModel} ({v.status})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Pickup Details */}
            <div className="ops-card" style={{ padding: '12px' }}>
              <strong style={{ fontSize: '13px', color: 'var(--gold-primary)', display: 'block', marginBottom: '8px' }}>
                1. PICKUP POINT
              </strong>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '8px' }}>
                <input
                  type="text"
                  placeholder="Pickup Company *"
                  className="input-field"
                  value={pickupCompany}
                  onChange={(e) => setPickupCompany(e.target.value)}
                  required
                />
                <input
                  type="text"
                  placeholder="Street Address *"
                  className="input-field"
                  value={pickupAddress}
                  onChange={(e) => setPickupAddress(e.target.value)}
                  required
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px' }}>
                <input
                  type="text"
                  placeholder="Suburb & State"
                  className="input-field"
                  value={pickupSuburb}
                  onChange={(e) => setPickupSuburb(e.target.value)}
                />
                <input
                  type="text"
                  placeholder="Window Start"
                  className="input-field"
                  value={pickupWindowStart}
                  onChange={(e) => setPickupWindowStart(e.target.value)}
                />
                <input
                  type="text"
                  placeholder="Window End"
                  className="input-field"
                  value={pickupWindowEnd}
                  onChange={(e) => setPickupWindowEnd(e.target.value)}
                />
              </div>
            </div>

            {/* Delivery Details */}
            <div className="ops-card" style={{ padding: '12px' }}>
              <strong style={{ fontSize: '13px', color: 'var(--status-green)', display: 'block', marginBottom: '8px' }}>
                2. DELIVERY DESTINATION
              </strong>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '8px' }}>
                <input
                  type="text"
                  placeholder="Delivery Company *"
                  className="input-field"
                  value={deliveryCompany}
                  onChange={(e) => setDeliveryCompany(e.target.value)}
                  required
                />
                <input
                  type="text"
                  placeholder="Street Address *"
                  className="input-field"
                  value={deliveryAddress}
                  onChange={(e) => setDeliveryAddress(e.target.value)}
                  required
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px' }}>
                <input
                  type="text"
                  placeholder="Suburb & State"
                  className="input-field"
                  value={deliverySuburb}
                  onChange={(e) => setDeliverySuburb(e.target.value)}
                />
                <input
                  type="text"
                  placeholder="Window Start"
                  className="input-field"
                  value={deliveryWindowStart}
                  onChange={(e) => setDeliveryWindowStart(e.target.value)}
                />
                <input
                  type="text"
                  placeholder="Window End"
                  className="input-field"
                  value={deliveryWindowEnd}
                  onChange={(e) => setDeliveryWindowEnd(e.target.value)}
                />
              </div>
            </div>

            {/* Freight Manifest */}
            <div className="ops-card" style={{ padding: '12px' }}>
              <strong style={{ fontSize: '13px', color: 'var(--text-gold)', display: 'block', marginBottom: '8px' }}>
                3. FREIGHT & SPECIAL INSTRUCTIONS
              </strong>
              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '8px', marginBottom: '8px' }}>
                <input
                  type="text"
                  placeholder="Freight Description (e.g. 4 Pallets Automotive Spares) *"
                  className="input-field"
                  value={freightDescription}
                  onChange={(e) => setFreightDescription(e.target.value)}
                  required
                />
                <input
                  type="number"
                  placeholder="Item Count"
                  className="input-field"
                  value={itemCount}
                  onChange={(e) => setItemCount(e.target.value)}
                />
              </div>
              <textarea
                placeholder="Special delivery instructions, gate access codes, tail-lift requirements..."
                className="input-field"
                rows={2}
                value={specialInstructions}
                onChange={(e) => setSpecialInstructions(e.target.value)}
              />
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '8px', fontSize: '13px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={dangerousGoods}
                  onChange={(e) => setDangerousGoods(e.target.checked)}
                />
                <span>Dangerous Goods / Hazmat documentation required</span>
              </label>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" onClick={onClose} className="btn-secondary" disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={isSubmitting}>
              <Plus size={16} /> {isSubmitting ? 'Dispatching...' : 'Create & Dispatch Job'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
