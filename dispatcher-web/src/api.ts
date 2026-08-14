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
import { JobEditPayload } from './editJob';

const envBase = (import.meta as any).env?.VITE_API_BASE_URL;
const BASE_URL = typeof envBase === 'string' ? envBase.replace(/\/$/, '') : '';
const API_BASE = BASE_URL ? `${BASE_URL}/v1` : '/v1';
let csrfToken: string | null = null;

export class ApiError extends Error {
  readonly code?: string;
  readonly currentRevision?: number;

  constructor(message: string, options: { code?: string; currentRevision?: number } = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = options.code;
    this.currentRevision = options.currentRevision;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {})
  };

  const method = (options.method || 'GET').toUpperCase();
  if (csrfToken && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers['X-CSRF-Token'] = csrfToken;
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && !headers['X-Idempotency-Key']) {
    headers['X-Idempotency-Key'] = crypto.randomUUID();
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: 'include'
  });

  if (!response.ok) {
    const errBody = await response.json().catch(() => ({ error: response.statusText }));
    if (response.status === 401) window.dispatchEvent(new Event('tms:session-expired'));
    throw new ApiError(errBody.error || `HTTP ${response.status}: ${response.statusText}`, {
      code: errBody.code,
      currentRevision: errBody.currentRevision
    });
  }

  return response.json();
}

export const api = {
  evidenceUrl(id: string): string { return `${API_BASE}/evidence/${encodeURIComponent(id)}`; },
  // Auth
  async login(email: string, password: string): Promise<{ csrfToken: string; user: DispatchUser }> {
    const res = await request<{ csrfToken: string; user: DispatchUser }>('/auth/dispatch/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
    csrfToken = res.csrfToken;
    return res;
  },

  async getMe(): Promise<{ user: DispatchUser; csrfToken: string }> {
    const res = await request<{ user: DispatchUser; csrfToken: string }>('/auth/dispatch/me');
    csrfToken = res.csrfToken;
    return res;
  },

  async logout(): Promise<void> {
    await request('/auth/dispatch/logout', { method: 'POST' });
    csrfToken = null;
  },

  // Jobs
  async getJobs(params: { status?: string; search?: string } = {}): Promise<{ jobs: Job[]; total: number }> {
    const query = new URLSearchParams();
    if (params.status && params.status !== 'ALL') query.append('status', params.status);
    if (params.search) query.append('search', params.search);
    const queryString = query.toString() ? `?${query.toString()}` : '';
    return request<{ jobs: Job[]; total: number }>(`/jobs${queryString}`);
  },

  async getJob(id: string): Promise<{ job: Job }> {
    return request<{ job: Job }>(`/jobs/${id}`);
  },

  async createJob(payload: Partial<Job>): Promise<{ success: boolean; job: Job }> {
    return request<{ success: boolean; job: Job }>('/jobs/create', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async updateJob(id: string, payload: JobEditPayload): Promise<{ success: boolean; job: Job }> {
    return request<{ success: boolean; job: Job }>(`/jobs/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  async reassignJob(id: string, newDriverId: string, newVehicleId: string, reason: string): Promise<{ success: boolean; job: Job }> {
    return request<{ success: boolean; job: Job }>(`/jobs/${id}/reassign`, {
      method: 'POST',
      body: JSON.stringify({ newDriverId, newVehicleId, reason })
    });
  },

  async cancelJob(id: string, reason: string): Promise<{ success: boolean; job: Job }> {
    return request<{ success: boolean; job: Job }>(`/jobs/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
  },

  // Fleet Telemetry & Locations
  async getFleetLocations(): Promise<{ fleet: FleetLocationItem[]; count: number }> {
    return request<{ fleet: FleetLocationItem[]; count: number }>('/fleet/locations/latest');
  },

  // Drivers
  async getDrivers(status?: string): Promise<{ drivers: Driver[]; count: number }> {
    const query = status && status !== 'ALL' ? `?shiftStatus=${status}` : '';
    return request<{ drivers: Driver[]; count: number }>(`/drivers${query}`);
  },

  // Vehicles
  async getVehicles(status?: string): Promise<{ vehicles: Vehicle[]; count: number }> {
    const query = status && status !== 'ALL' ? `?status=${status}` : '';
    return request<{ vehicles: Vehicle[]; count: number }>(`/vehicles${query}`);
  },

  async updateDefectStatus(vehicleId: string, defectId: string, status: string): Promise<{ success: boolean }> {
    return request<{ success: boolean }>(`/vehicles/${vehicleId}/defects/${defectId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
  },

  // Incidents
  async getIncidents(): Promise<{ incidents: Incident[]; count: number }> {
    return request<{ incidents: Incident[]; count: number }>('/incidents');
  },

  async acknowledgeIncident(id: string): Promise<{ success: boolean; incident: Incident }> {
    return request<{ success: boolean; incident: Incident }>(`/incidents/${id}/acknowledge`, {
      method: 'POST'
    });
  },

  async resolveIncident(id: string, opsNotes: string): Promise<{ success: boolean; incident: Incident }> {
    return request<{ success: boolean; incident: Incident }>(`/incidents/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ opsNotes })
    });
  },

  // Messages
  async getMessages(driverId?: string): Promise<{ messages: OperationMessage[]; count: number }> {
    const query = driverId ? `?driverId=${driverId}` : '';
    return request<{ messages: OperationMessage[]; count: number }>(`/messages${query}`);
  },

  async sendMessage(payload: { driverId?: string; jobId?: string; category: string; content: string; isUrgent?: boolean }): Promise<{ success: boolean; message: OperationMessage }> {
    return request<{ success: boolean; message: OperationMessage }>('/messages/send', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  // Audit Logs
  async getAuditLogs(): Promise<{ logs: AuditLogEntry[]; total: number }> {
    return request<{ logs: AuditLogEntry[]; total: number }>('/audit/logs?limit=50');
  },

  // App Config
  async getAppConfig(): Promise<AppConfig> {
    return request<AppConfig>('/app/config');
  },

  async updateAppConfig(config: Partial<AppConfig>): Promise<{ success: boolean; config: AppConfig }> {
    return request<{ success: boolean; config: AppConfig }>('/app/config', {
      method: 'PUT',
      body: JSON.stringify(config)
    });
  },

  // Realtime SSE Connection
  connectSse(
    onEvent: (eventType: string, data: any) => void,
    onStatusChange: (status: 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED') => void
  ): () => void {
    let eventSource: EventSource | null = null;
    let reconnectTimeout: any = null;

    const connect = () => {
      onStatusChange('RECONNECTING');
      eventSource = new EventSource(`${API_BASE}/events/stream`, { withCredentials: true });

      eventSource.onopen = () => {
        onStatusChange('CONNECTED');
      };

      const eventTypes = [
        'connected',
        'job.created',
        'job.updated',
        'job.status_changed',
        'job.reassigned',
        'job.cancelled',
        'pod.completed',
        'driver.location_updated',
        'driver.shift_event',
        'incident.created',
        'incident.updated',
        'vehicle.defect_reported',
        'message.created',
        'config.updated'
      ];

      for (const et of eventTypes) {
        eventSource.addEventListener(et, (event: MessageEvent) => {
          try {
            const parsed = JSON.parse(event.data);
            onEvent(et, parsed);
          } catch (e) {
            console.error('Failed to parse SSE event payload', e);
          }
        });
      }

      eventSource.onerror = () => {
        onStatusChange('DISCONNECTED');
        if (eventSource) {
          eventSource.close();
          eventSource = null;
        }
        reconnectTimeout = setTimeout(connect, 3000);
      };
    };

    connect();

    return () => {
      if (reconnectTimeout) clearTimeout(reconnectTimeout);
      if (eventSource) eventSource.close();
    };
  }
};
