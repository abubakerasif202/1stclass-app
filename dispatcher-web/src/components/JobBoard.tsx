import {
  AlertTriangle,
  ArrowRight,
  Clock,
  Eye,
  Filter,
  Plus,
  RefreshCw,
  Search,
  Truck,
  UserCheck,
  XCircle
} from 'lucide-react';
import React, { useState } from 'react';
import { Driver, Job, Vehicle } from '../types';

interface JobBoardProps {
  jobs: Job[];
  drivers: Driver[];
  vehicles: Vehicle[];
  onOpenCreateJob: () => void;
  onViewJob: (jobId: string) => void;
  onReassignJob: (job: Job) => void;
  onCancelJob: (job: Job) => void;
}

type JobFilterTab = 'ALL' | 'UNASSIGNED' | 'ACTIVE' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED' | 'URGENT';

export const JobBoard: React.FC<JobBoardProps> = ({
  jobs,
  drivers,
  vehicles,
  onOpenCreateJob,
  onViewJob,
  onReassignJob,
  onCancelJob
}) => {
  const [selectedTab, setSelectedTab] = useState<JobFilterTab>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  const filterTabs: { key: JobFilterTab; label: string }[] = [
    { key: 'ALL', label: `All Jobs (${jobs.length})` },
    { key: 'ACTIVE', label: 'Active / In Progress' },
    { key: 'IN_TRANSIT', label: 'In Transit' },
    { key: 'UNASSIGNED', label: 'Unassigned' },
    { key: 'URGENT', label: 'Urgent Express' },
    { key: 'COMPLETED', label: 'Completed' },
    { key: 'CANCELLED', label: 'Cancelled' }
  ];

  const filteredJobs = jobs.filter(job => {
    // Tab Filter
    if (selectedTab === 'ACTIVE') {
      if (job.status === 'COMPLETED' || job.status === 'CANCELLED') return false;
    } else if (selectedTab === 'IN_TRANSIT') {
      if (job.status !== 'AT_PICKUP' && job.status !== 'PICKED_UP' && job.status !== 'EN_ROUTE_DELIVERY' && job.status !== 'AT_DELIVERY') return false;
    } else if (selectedTab === 'UNASSIGNED') {
      if (job.assignedDriverId) return false;
    } else if (selectedTab === 'URGENT') {
      if (job.priority !== 'URGENT') return false;
    } else if (selectedTab === 'COMPLETED') {
      if (job.status !== 'COMPLETED') return false;
    } else if (selectedTab === 'CANCELLED') {
      if (job.status !== 'CANCELLED') return false;
    }

    // Search Query
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().trim();
      const driverName = job.assignedDriverId ? (drivers.find(d => d.id === job.assignedDriverId)?.name || '') : '';
      return (
        job.reference.toLowerCase().includes(q) ||
        job.pickup.companyName.toLowerCase().includes(q) ||
        job.pickup.suburb.toLowerCase().includes(q) ||
        job.delivery.companyName.toLowerCase().includes(q) ||
        job.delivery.suburb.toLowerCase().includes(q) ||
        job.freightDescription.toLowerCase().includes(q) ||
        driverName.toLowerCase().includes(q)
      );
    }

    return true;
  });

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <span className="badge badge-green">COMPLETED</span>;
      case 'EN_ROUTE_DELIVERY':
      case 'IN_PROGRESS':
        return <span className="badge badge-blue">IN TRANSIT</span>;
      case 'AT_PICKUP':
      case 'AT_DELIVERY':
        return <span className="badge badge-gold">{status.replace('_', ' ')}</span>;
      case 'CANCELLED':
        return <span className="badge badge-red">CANCELLED</span>;
      case 'ASSIGNED':
      case 'ACCEPTED':
        return <span className="badge badge-amber">{status}</span>;
      default:
        return <span className="badge badge-neutral">{status}</span>;
    }
  };

  const getPriorityBadge = (priority: string) => {
    if (priority === 'URGENT') return <span className="badge badge-red">URGENT</span>;
    if (priority === 'HIGH') return <span className="badge badge-amber">HIGH</span>;
    return <span className="badge badge-neutral">{priority}</span>;
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Top Header & Actions */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '0.5px' }}>MANIFEST & JOB BOARD</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time transport operations, live dispatch allocations, and POD tracking.
          </p>
        </div>

        <button onClick={onOpenCreateJob} className="btn-primary">
          <Plus size={16} /> Create Express Job
        </button>
      </div>

      {/* Filter Tabs & Search Bar */}
      <div className="ops-card" style={{ padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
          {/* Tabs */}
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
            {filterTabs.map(t => (
              <button
                key={t.key}
                onClick={() => setSelectedTab(t.key)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  background: selectedTab === t.key ? 'var(--gold-primary)' : 'var(--bg-surface)',
                  color: selectedTab === t.key ? '#0c0e12' : 'var(--text-secondary)',
                  fontWeight: 600,
                  fontSize: '12px',
                  cursor: 'pointer'
                }}
              >
                {t.label}
              </button>
            ))}
          </div>

          {/* Search Box */}
          <div style={{ position: 'relative', width: '280px' }}>
            <Search size={14} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--text-muted)' }} />
            <input
              type="text"
              placeholder="Search reference, company, suburb..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input-field"
              style={{ paddingLeft: '32px', height: '34px', fontSize: '12px' }}
            />
          </div>
        </div>
      </div>

      {/* Jobs Table */}
      <div className="ops-table-container">
        <table className="ops-table">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Pickup Location</th>
              <th>Delivery Destination</th>
              <th>Freight Manifest</th>
              <th>Assigned Fleet</th>
              <th>Rev</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredJobs.length === 0 ? (
              <tr>
                <td colSpan={9} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                  No transport jobs found matching the selected filter criteria.
                </td>
              </tr>
            ) : (
              filteredJobs.map(job => {
                const driver = drivers.find(d => d.id === job.assignedDriverId);
                const vehicle = vehicles.find(v => v.id === job.assignedVehicleId);

                return (
                  <tr key={job.id}>
                    {/* Reference */}
                    <td>
                      <div style={{ fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--text-gold)' }}>
                        {job.reference}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {new Date(job.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </td>

                    {/* Priority */}
                    <td>{getPriorityBadge(job.priority)}</td>

                    {/* Status */}
                    <td>{getStatusBadge(job.status)}</td>

                    {/* Pickup */}
                    <td>
                      <div style={{ fontWeight: 600, fontSize: '13px' }}>{job.pickup.suburb}</div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{job.pickup.companyName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--gold-dark)', marginTop: '2px' }}>
                        Window: {job.pickupWindowStart} - {job.pickupWindowEnd}
                      </div>
                    </td>

                    {/* Delivery */}
                    <td>
                      <div style={{ fontWeight: 600, fontSize: '13px' }}>{job.delivery.suburb}</div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{job.delivery.companyName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--gold-dark)', marginTop: '2px' }}>
                        Window: {job.deliveryWindowStart} - {job.deliveryWindowEnd}
                      </div>
                    </td>

                    {/* Freight */}
                    <td>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{job.freightDescription}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{job.itemCount} items / pallets</div>
                    </td>

                    {/* Assigned Fleet */}
                    <td>
                      {driver ? (
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '13px' }}>{driver.name}</div>
                          <div style={{ fontSize: '11px', color: 'var(--text-gold)' }}>
                            {vehicle?.rego || 'No vehicle'}
                          </div>
                        </div>
                      ) : (
                        <span className="badge badge-neutral">UNASSIGNED</span>
                      )}
                    </td>

                    {/* Revision */}
                    <td>
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-muted)' }}>
                        r{job.revision}
                      </span>
                    </td>

                    {/* Actions */}
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '6px' }}>
                        <button
                          onClick={() => onViewJob(job.id)}
                          className="btn-secondary"
                          style={{ padding: '4px 8px', fontSize: '12px' }}
                          title="View Full Record"
                        >
                          <Eye size={14} /> View
                        </button>
                        {job.status !== 'COMPLETED' && job.status !== 'CANCELLED' && (
                          <>
                            <button
                              onClick={() => onReassignJob(job)}
                              className="btn-secondary"
                              style={{ padding: '4px 8px', fontSize: '12px' }}
                              title="Reassign Driver"
                            >
                              <UserCheck size={14} />
                            </button>
                            <button
                              onClick={() => onCancelJob(job)}
                              className="btn-danger"
                              style={{ padding: '4px 8px', fontSize: '12px' }}
                              title="Cancel Job"
                            >
                              <XCircle size={14} />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
