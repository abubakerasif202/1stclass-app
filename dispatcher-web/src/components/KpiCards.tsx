import {
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Clock,
  PackageCheck,
  Truck,
  Users
} from 'lucide-react';
import React from 'react';
import { Driver, Incident, Job, Vehicle } from '../types';

interface KpiCardsProps {
  drivers: Driver[];
  jobs: Job[];
  vehicles: Vehicle[];
  incidents: Incident[];
  onSelectTab: (tab: any) => void;
}

export const KpiCards: React.FC<KpiCardsProps> = ({
  drivers,
  jobs,
  vehicles,
  incidents,
  onSelectTab
}) => {
  const onDutyCount = drivers.filter(d => d.shiftStatus === 'ON_DUTY').length;
  const activeJobs = jobs.filter(j => j.status !== 'COMPLETED' && j.status !== 'CANCELLED');
  const awaitingPickup = jobs.filter(j => j.status === 'ASSIGNED' || j.status === 'ACCEPTED' || j.status === 'IN_PROGRESS');
  const inTransit = jobs.filter(j => j.status === 'AT_PICKUP' || j.status === 'PICKED_UP' || j.status === 'EN_ROUTE_DELIVERY' || j.status === 'AT_DELIVERY');
  const completedToday = jobs.filter(j => j.status === 'COMPLETED').length;
  const openIncidents = incidents.filter(i => i.status !== 'RESOLVED').length;
  const activeDefects = vehicles.reduce((sum, v) => sum + v.activeDefectCount, 0);

  const cards = [
    {
      title: 'DRIVERS ON DUTY',
      value: `${onDutyCount} / ${drivers.length}`,
      sub: `${drivers.filter(d => d.shiftStatus === 'ON_BREAK').length} on break`,
      icon: <Users size={20} color="var(--gold-primary)" />,
      border: 'var(--border-subtle)',
      tab: 'drivers'
    },
    {
      title: 'ACTIVE JOBS',
      value: activeJobs.length,
      sub: `${jobs.filter(j => j.priority === 'URGENT').length} urgent express`,
      icon: <Truck size={20} color="var(--status-blue)" />,
      border: 'var(--border-subtle)',
      tab: 'jobs'
    },
    {
      title: 'AWAITING PICKUP',
      value: awaitingPickup.length,
      sub: 'Allocated to driver',
      icon: <Clock size={20} color="var(--status-amber)" />,
      border: 'var(--border-subtle)',
      tab: 'jobs'
    },
    {
      title: 'IN TRANSIT',
      value: inTransit.length,
      sub: 'Live GPS tracked',
      icon: <PackageCheck size={20} color="var(--status-green)" />,
      border: 'var(--border-subtle)',
      tab: 'jobs'
    },
    {
      title: 'COMPLETED TODAY',
      value: completedToday,
      sub: 'POD evidenced',
      icon: <CheckCircle2 size={20} color="var(--status-green)" />,
      border: 'var(--border-subtle)',
      tab: 'pod'
    },
    {
      title: 'OPEN INCIDENTS',
      value: openIncidents,
      sub: openIncidents > 0 ? 'Requires attention' : 'All clear',
      icon: <AlertTriangle size={20} color={openIncidents > 0 ? 'var(--status-red)' : 'var(--text-muted)'} />,
      border: openIncidents > 0 ? 'rgba(239, 68, 68, 0.4)' : 'var(--border-subtle)',
      isAlert: openIncidents > 0,
      tab: 'incidents'
    },
    {
      title: 'VEHICLE DEFECTS',
      value: activeDefects,
      sub: activeDefects > 0 ? 'Pre-start defects' : 'Fleet operational',
      icon: <AlertOctagon size={20} color={activeDefects > 0 ? 'var(--status-red)' : 'var(--text-muted)'} />,
      border: activeDefects > 0 ? 'rgba(239, 68, 68, 0.4)' : 'var(--border-subtle)',
      isAlert: activeDefects > 0,
      tab: 'vehicles'
    }
  ];

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
      gap: '16px',
      marginBottom: '24px'
    }}>
      {cards.map((c, idx) => (
        <div
          key={idx}
          onClick={() => onSelectTab(c.tab)}
          className="ops-card"
          style={{
            cursor: 'pointer',
            border: `1px solid ${c.border}`,
            background: c.isAlert ? 'rgba(239, 68, 68, 0.05)' : 'var(--bg-card)'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-secondary)', letterSpacing: '0.6px' }}>
              {c.title}
            </span>
            {c.icon}
          </div>
          <div style={{ fontSize: '24px', fontWeight: 800, color: c.isAlert ? 'var(--status-red)' : 'var(--text-primary)' }}>
            {c.value}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {c.sub}
          </div>
        </div>
      ))}
    </div>
  );
};
