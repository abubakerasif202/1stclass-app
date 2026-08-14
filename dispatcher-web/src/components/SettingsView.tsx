import {
  CheckCircle2,
  Lock,
  Phone,
  Save,
  Settings,
  Shield,
  Smartphone,
  ToggleLeft,
  ToggleRight,
  UserCheck
} from 'lucide-react';
import React, { useState } from 'react';
import { AppConfig, Driver } from '../types';

interface SettingsViewProps {
  config: AppConfig | null;
  drivers: Driver[];
  onSaveConfig: (updated: Partial<AppConfig>) => Promise<void>;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  config,
  drivers,
  onSaveConfig
}) => {
  const [minSupportedAppVersion, setMinSupportedAppVersion] = useState(config?.minSupportedAppVersion || '1.0.0');
  const [latestAppVersion, setLatestAppVersion] = useState(config?.latestAppVersion || '1.0.0');
  const [supportPhone, setSupportPhone] = useState(config?.supportPhoneNumber || '1300 000 178');
  const [supportEmail, setSupportEmail] = useState(config?.supportEmail || 'dispatch@1stclassexpress.com.au');
  const [features, setFeatures] = useState(config?.features || {
    liveTracking: true,
    barcodeScanner: true,
    geofencing: true,
    messaging: true,
    offlineSync: true,
    delayPrompts: true
  });
  const [isSaving, setIsSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsSaving(true);
      await onSaveConfig({
        minSupportedAppVersion,
        latestAppVersion,
        supportPhoneNumber: supportPhone,
        supportEmail,
        features
      });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 3000);
    } finally {
      setIsSaving(false);
    }
  };

  const toggleFeature = (key: keyof typeof features) => {
    setFeatures(prev => ({ ...prev, [key]: !prev[key] }));
  };

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px' }}>
      {/* Remote App Configuration Form */}
      <div className="ops-card">
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px',
          borderBottom: '1px solid var(--border-subtle)',
          paddingBottom: '12px'
        }}>
          <div>
            <h2 style={{ fontSize: '18px', fontWeight: 800 }}>REMOTE DRIVER APP CONFIGURATION</h2>
            <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
              Configure minimum version enforcement and remote operational feature flags.
            </p>
          </div>
          <span className="badge badge-gold">ADMIN LEVEL</span>
        </div>

        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {savedSuccess && (
            <div style={{ padding: '10px 14px', background: 'var(--status-green-bg)', color: 'var(--status-green)', borderRadius: '6px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <CheckCircle2 size={16} /> Remote configuration saved and broadcasted to fleet.
            </div>
          )}

          {/* App Versions */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Minimum Supported Version *
              </label>
              <input
                type="text"
                className="input-field"
                value={minSupportedAppVersion}
                onChange={(e) => setMinSupportedAppVersion(e.target.value)}
                required
              />
              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                Work phones below this will require update before signing in.
              </span>
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Latest Published Version
              </label>
              <input
                type="text"
                className="input-field"
                value={latestAppVersion}
                onChange={(e) => setLatestAppVersion(e.target.value)}
                required
              />
            </div>
          </div>

          {/* Support Contacts */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Operations Support Phone
              </label>
              <input
                type="text"
                className="input-field"
                value={supportPhone}
                onChange={(e) => setSupportPhone(e.target.value)}
              />
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                Operations Support Email
              </label>
              <input
                type="text"
                className="input-field"
                value={supportEmail}
                onChange={(e) => setSupportEmail(e.target.value)}
              />
            </div>
          </div>

          {/* Feature Flags */}
          <div>
            <label style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-gold)', display: 'block', marginBottom: '8px' }}>
              OPERATIONAL FEATURE FLAGS
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              {[
                { key: 'liveTracking', label: 'Live GPS Telemetry' },
                { key: 'barcodeScanner', label: 'Consignment Barcode Scanner' },
                { key: 'geofencing', label: 'Geofence Arrival Prompts' },
                { key: 'messaging', label: 'Driver Operations Messaging' },
                { key: 'offlineSync', label: 'Durable Offline Sync' },
                { key: 'delayPrompts', label: 'Operational Delay Detection' }
              ].map(item => (
                <div
                  key={item.key}
                  onClick={() => toggleFeature(item.key as any)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 12px',
                    background: 'var(--bg-main)',
                    borderRadius: '6px',
                    border: '1px solid var(--border-subtle)',
                    cursor: 'pointer'
                  }}
                >
                  <span style={{ fontSize: '13px', fontWeight: 500 }}>{item.label}</span>
                  <span className={`badge ${features[item.key as keyof typeof features] ? 'badge-green' : 'badge-neutral'}`}>
                    {features[item.key as keyof typeof features] ? 'ENABLED' : 'DISABLED'}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ marginTop: '12px' }}>
            <button type="submit" className="btn-primary" disabled={isSaving}>
              <Save size={16} /> {isSaving ? 'Saving...' : 'Save & Publish Configuration'}
            </button>
          </div>
        </form>
      </div>

      {/* Device Registry Card */}
      <div className="ops-card" style={{ display: 'flex', flexDirection: 'column' }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px',
          borderBottom: '1px solid var(--border-subtle)',
          paddingBottom: '12px'
        }}>
          <div>
            <h2 style={{ fontSize: '18px', fontWeight: 800 }}>REGISTERED WORK PHONES</h2>
            <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
              Hardware device registration and push token connectivity.
            </p>
          </div>
          <span className="badge badge-gold">{drivers.length} Devices</span>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {drivers.map(driver => (
            <div
              key={driver.id}
              style={{
                padding: '12px',
                background: 'var(--bg-surface)',
                borderRadius: '6px',
                border: '1px solid var(--border-subtle)'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <strong style={{ fontSize: '14px' }}>{driver.name}</strong>
                <span className={`badge ${driver.shiftStatus === 'ON_DUTY' ? 'badge-green' : 'badge-neutral'}`} style={{ fontSize: '10px' }}>
                  {driver.shiftStatus}
                </span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                <span>App Version: <strong>v{driver.appVersion}</strong></span>
                <span>Push: <strong style={{ color: driver.pushToken ? 'var(--status-green)' : 'var(--text-muted)' }}>{driver.pushToken ? 'Registered' : 'None'}</strong></span>
              </div>

              <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', fontFamily: 'var(--font-mono)' }}>
                Last Seen: {new Date(driver.lastSeen).toLocaleString()}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
