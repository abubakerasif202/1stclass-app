import React, { useEffect, useState } from 'react';
import { api } from './api';
import { ActivityFeed, ActivityItem } from './components/ActivityFeed';
import { AuditLogView } from './components/AuditLogView';
import { CancelJobModal } from './components/CancelJobModal';
import { CreateJobModal } from './components/CreateJobModal';
import { DriverSidePanel } from './components/DriverSidePanel';
import { EditJobModal } from './components/EditJobModal';
import { DriversView } from './components/DriversView';
import { IncidentsView } from './components/IncidentsView';
import { JobBoard } from './components/JobBoard';
import { JobDetailModal } from './components/JobDetailModal';
import { KpiCards } from './components/KpiCards';
import { LiveFleetMap } from './components/LiveFleetMap';
import { MessagingView } from './components/MessagingView';
import { Navbar } from './components/Navbar';
import { ReassignJobModal } from './components/ReassignJobModal';
import { SettingsView } from './components/SettingsView';
import { Sidebar, TabKey } from './components/Sidebar';
import { VehiclesView } from './components/VehiclesView';
import { JobEditPayload, persistJobEdit } from './editJob';
import {
  AppConfig,
  AuditLogEntry,
  DispatchUser,
  Driver,
  FleetLocationItem,
  Incident,
  Job,
  OperationMessage,
  Vehicle
} from './types';

const LoginGate: React.FC<{ onAuthenticated: (user: DispatchUser) => void }> = ({ onAuthenticated }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try { onAuthenticated((await api.login(email, password)).user); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to sign in'); }
    finally { setSubmitting(false); }
  };
  return (
    <main className="login-page">
      <form className="ops-card login-card" onSubmit={submit}>
        <h1>Dispatcher sign in</h1>
        <label>Email<input className="input-field" type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>Password<input className="input-field" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {error && <div role="alert" className="error-message">{error}</div>}
        <button className="btn-primary" disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </main>
  );
};

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabKey>('dashboard');
  const [connectionStatus, setConnectionStatus] = useState<'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED'>('RECONNECTING');

  // User State
  const [currentUser, setCurrentUser] = useState<DispatchUser | null>(null);
  const [authChecked, setAuthChecked] = useState(false);

  // Fleet & Transport State
  const [jobs, setJobs] = useState<Job[]>([]);
  const [fleet, setFleet] = useState<FleetLocationItem[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [messages, setMessages] = useState<OperationMessage[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [appConfig, setAppConfig] = useState<AppConfig | null>(null);
  const [activityFeed, setActivityFeed] = useState<ActivityItem[]>([]);

  // Selection & Modal States
  const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [isCreateJobOpen, setIsCreateJobOpen] = useState(false);
  const [reassigningJob, setReassigningJob] = useState<Job | null>(null);
  const [cancellingJob, setCancellingJob] = useState<Job | null>(null);
  const [editingJobId, setEditingJobId] = useState<string | null>(null);

  useEffect(() => {
    api.getMe().then(result => setCurrentUser(result.user)).catch(() => setCurrentUser(null)).finally(() => setAuthChecked(true));
    const expired = () => setCurrentUser(null);
    window.addEventListener('tms:session-expired', expired);
    return () => window.removeEventListener('tms:session-expired', expired);
  }, []);

  // Initial Data Fetch
  const refreshAllData = async () => {
    try {
      const [jobsRes, fleetRes, driversRes, vehiclesRes, incidentsRes, messagesRes, auditRes, configRes] =
        await Promise.all([
          api.getJobs(),
          api.getFleetLocations(),
          api.getDrivers(),
          api.getVehicles(),
          api.getIncidents(),
          api.getMessages(),
          api.getAuditLogs(),
          api.getAppConfig()
        ]);

      setJobs(jobsRes.jobs);
      setFleet(fleetRes.fleet);
      setDrivers(driversRes.drivers);
      setVehicles(vehiclesRes.vehicles);
      setIncidents(incidentsRes.incidents);
      setMessages(messagesRes.messages);
      setAuditLogs(auditRes.logs);
      setAppConfig(configRes);
    } catch (err) {
      console.error('Failed to load initial transport state', err);
    }
  };

  useEffect(() => {
    if (!currentUser) return;
    refreshAllData();

    // Connect SSE Stream
    const disconnectSse = api.connectSse((eventType, data) => {
      // Add to live activity feed
      const newActivity: ActivityItem = {
        id: `act-${Date.now()}-${Math.random().toString(36).substring(2, 5)}`,
        type: eventType.includes('pod') ? 'POD_COMPLETED' : eventType.includes('incident') ? 'INCIDENT' : eventType.includes('location') ? 'LOCATION' : 'JOB',
        title: eventType.replace('.', ' ').toUpperCase(),
        description: data.reference ? `Job ${data.reference}: ${data.toStatus || 'updated'}` : data.driverName ? `Driver ${data.driverName} telemetry received` : 'Operations event processed',
        timestamp: Date.now()
      };

      setActivityFeed(prev => [newActivity, ...prev.slice(0, 49)]);

      // Reactively refresh relevant state
      if (eventType.startsWith('job.')) {
        api.getJobs().then(res => setJobs(res.jobs));
      } else if (eventType.startsWith('driver.location')) {
        api.getFleetLocations().then(res => setFleet(res.fleet));
      } else if (eventType.startsWith('incident.')) {
        api.getIncidents().then(res => setIncidents(res.incidents));
      } else if (eventType.startsWith('vehicle.')) {
        api.getVehicles().then(res => setVehicles(res.vehicles));
      } else if (eventType.startsWith('message.')) {
        api.getMessages().then(res => setMessages(res.messages));
      }
      api.getAuditLogs().then(res => setAuditLogs(res.logs));
    }, setConnectionStatus);

    return () => {
      disconnectSse();
    };
  }, [currentUser?.id]);

  // Handlers
  const handleCreateJob = async (payload: Partial<Job>) => {
    await api.createJob(payload);
    await refreshAllData();
  };

  const handleReassignJob = async (jobId: string, newDriverId: string, newVehicleId: string, reason: string) => {
    await api.reassignJob(jobId, newDriverId, newVehicleId, reason);
    await refreshAllData();
  };

  const handleCancelJob = async (jobId: string, reason: string) => {
    await api.cancelJob(jobId, reason);
    await refreshAllData();
  };

  const handleUpdateJob = async (jobId: string, payload: JobEditPayload) => {
    const refreshResult = await persistJobEdit(payload, async update => { await api.updateJob(jobId, update); }, refreshAllData);
    if (refreshResult.kind === 'refresh-failed') {
      console.error('Job was saved, but dispatcher data refresh failed', refreshResult.message);
    }
  };

  const refreshJobs = async () => {
    const response = await api.getJobs();
    setJobs(response.jobs);
  };

  const handleAcknowledgeIncident = async (incidentId: string) => {
    await api.acknowledgeIncident(incidentId);
    await refreshAllData();
  };

  const handleResolveIncident = async (incidentId: string, opsNotes: string) => {
    await api.resolveIncident(incidentId, opsNotes);
    await refreshAllData();
  };

  const handleUpdateDefect = async (vehicleId: string, defectId: string, status: string) => {
    await api.updateDefectStatus(vehicleId, defectId, status);
    await refreshAllData();
  };

  const handleSendMessage = async (payload: any) => {
    await api.sendMessage(payload);
    await refreshAllData();
  };

  const handleSaveConfig = async (configUpdate: Partial<AppConfig>) => {
    await api.updateAppConfig(configUpdate);
    await refreshAllData();
  };

  const activeJob = jobs.find(j => j.id === selectedJobId);
  const editingJob = jobs.find(j => j.id === editingJobId);
  const selectedDriverFleet = fleet.find(f => f.driverId === selectedDriverId);
  const selectedDriver = drivers.find(d => d.id === selectedDriverId);
  const selectedDriverVehicle = vehicles.find(v => v.id === selectedDriver?.currentVehicleId);
  const selectedDriverActiveJob = jobs.find(j => j.id === selectedDriver?.activeJobId);

  const activeJobsCount = jobs.filter(j => j.status !== 'COMPLETED' && j.status !== 'CANCELLED').length;
  const openIncidentsCount = incidents.filter(i => i.status !== 'RESOLVED').length;
  const activeDefectsCount = vehicles.reduce((sum, v) => sum + v.activeDefectCount, 0);

  if (!authChecked) return <main className="login-page">Checking session…</main>;
  if (!currentUser) return <LoginGate onAuthenticated={setCurrentUser} />;

  return (
    <div className="app-container">
      {/* Sidebar Navigation */}
      <Sidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        openIncidentsCount={openIncidentsCount}
        activeDefectsCount={activeDefectsCount}
      />

      {/* Main Content Area */}
      <div className="main-content">
        {/* Top Navbar */}
        <Navbar
          user={currentUser}
          connectionStatus={connectionStatus}
          activeJobsCount={activeJobsCount}
          openIncidentsCount={openIncidentsCount}
          onOpenAlerts={() => setActiveTab('incidents')}
          onLogout={() => void api.logout().finally(() => setCurrentUser(null))}
        />

        {/* Page Content Body */}
        <div className="page-body">
          {/* Main Dashboard View */}
          {activeTab === 'dashboard' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <KpiCards
                drivers={drivers}
                jobs={jobs}
                vehicles={vehicles}
                incidents={incidents}
                onSelectTab={setActiveTab}
              />

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', minHeight: '520px' }}>
                <LiveFleetMap
                  fleet={fleet}
                  selectedDriverId={selectedDriverId}
                  onSelectDriver={setSelectedDriverId}
                />
                <ActivityFeed items={activityFeed} />
              </div>
            </div>
          )}

          {/* Live Fleet Map Dedicated View */}
          {activeTab === 'live-map' && (
            <div style={{ height: 'calc(100vh - 115px)', display: 'flex', gap: '16px' }}>
              <div style={{ flex: 1, height: '100%' }}>
                <LiveFleetMap
                  fleet={fleet}
                  selectedDriverId={selectedDriverId}
                  onSelectDriver={setSelectedDriverId}
                />
              </div>
              {selectedDriverId && (
                <DriverSidePanel
                  driverId={selectedDriverId}
                  fleetItem={selectedDriverFleet}
                  driver={selectedDriver}
                  vehicle={selectedDriverVehicle}
                  activeJob={selectedDriverActiveJob}
                  onClose={() => setSelectedDriverId(null)}
                  onViewJob={(jobId) => setSelectedJobId(jobId)}
                  onSendMessage={(drvId) => { setSelectedDriverId(drvId); setActiveTab('messages'); }}
                />
              )}
            </div>
          )}

          {/* Jobs Board View */}
          {activeTab === 'jobs' && (
            <JobBoard
              jobs={jobs}
              drivers={drivers}
              vehicles={vehicles}
              onOpenCreateJob={() => setIsCreateJobOpen(true)}
              onViewJob={(jobId) => setSelectedJobId(jobId)}
              onReassignJob={(job) => setReassigningJob(job)}
              onCancelJob={(job) => setCancellingJob(job)}
            />
          )}

          {/* Drivers View */}
          {activeTab === 'drivers' && (
            <DriversView
              drivers={drivers}
              onSendMessage={(drvId) => { setSelectedDriverId(drvId); setActiveTab('messages'); }}
            />
          )}

          {/* Vehicles View */}
          {activeTab === 'vehicles' && (
            <VehiclesView
              vehicles={vehicles}
              onUpdateDefectStatus={handleUpdateDefect}
            />
          )}

          {/* Incidents View */}
          {activeTab === 'incidents' && (
            <IncidentsView
              incidents={incidents}
              onAcknowledge={handleAcknowledgeIncident}
              onResolve={handleResolveIncident}
            />
          )}

          {/* POD Review View */}
          {activeTab === 'pod' && (
            <JobBoard
              jobs={jobs.filter(j => j.status === 'COMPLETED' || j.pod !== null)}
              drivers={drivers}
              vehicles={vehicles}
              onOpenCreateJob={() => setIsCreateJobOpen(true)}
              onViewJob={(jobId) => setSelectedJobId(jobId)}
              onReassignJob={(job) => setReassigningJob(job)}
              onCancelJob={(job) => setCancellingJob(job)}
            />
          )}

          {/* Messaging View */}
          {activeTab === 'messages' && (
            <MessagingView
              messages={messages}
              drivers={drivers}
              onSendMessage={handleSendMessage}
            />
          )}

          {/* Audit Log View */}
          {activeTab === 'audit' && (
            <AuditLogView logs={auditLogs} />
          )}

          {/* Settings & Device Registry View */}
          {activeTab === 'settings' && (
            <SettingsView
              config={appConfig}
              drivers={drivers}
              onSaveConfig={handleSaveConfig}
            />
          )}
        </div>
      </div>

      {/* Modals */}
      {isCreateJobOpen && (
        <CreateJobModal
          drivers={drivers}
          vehicles={vehicles}
          onClose={() => setIsCreateJobOpen(false)}
          onSubmit={handleCreateJob}
        />
      )}

      {selectedJobId && activeJob && (
        <JobDetailModal
          job={activeJob}
          driver={drivers.find(d => d.id === activeJob.assignedDriverId)}
          vehicle={vehicles.find(v => v.id === activeJob.assignedVehicleId)}
          onClose={() => setSelectedJobId(null)}
          onOpenEdit={() => { setEditingJobId(activeJob.id); setSelectedJobId(null); }}
          onOpenReassign={() => { setReassigningJob(activeJob); setSelectedJobId(null); }}
          onOpenCancel={() => { setCancellingJob(activeJob); setSelectedJobId(null); }}
        />
      )}

      {reassigningJob && (
        <ReassignJobModal
          job={reassigningJob}
          drivers={drivers}
          vehicles={vehicles}
          onClose={() => setReassigningJob(null)}
          onSubmit={handleReassignJob}
        />
      )}

      {cancellingJob && (
        <CancelJobModal
          job={cancellingJob}
          onClose={() => setCancellingJob(null)}
          onSubmit={handleCancelJob}
        />
      )}

      {editingJob && (
        <EditJobModal
          job={editingJob}
          onClose={() => setEditingJobId(null)}
          onSubmit={handleUpdateJob}
          onRefresh={refreshJobs}
        />
      )}
    </div>
  );
};
