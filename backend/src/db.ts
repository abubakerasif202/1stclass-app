import {
  AppConfig,
  AuditLogEntry,
  DispatchUser,
  Driver,
  Incident,
  Job,
  LocationTelemetry,
  OperationMessage,
  Vehicle,
  VehicleDefect
} from './types';
import { DeviceRegistration, EvidenceMetadata, IdempotencyRecord, RefreshSession } from './types';
import { hashCredential } from './security/credentials';
import { currentRequestId } from './requestContext';
import { developmentDispatchPassword, developmentDriverPin } from './testing/developmentCredentials';
import { createStateRepository } from './persistence/createStateRepository';
import { PersistedTransportState, StateRepository } from './persistence/StateRepository';
import { EntityWriteOptions, IdempotencyEntry, TransportEntityType } from './persistence/StateRepository';
import { PostgresStateRepository } from './persistence/PostgresStateRepository';

export class TransportDatabase {
  public jobs: Map<string, Job> = new Map();
  public drivers: Map<string, Driver> = new Map();
  public vehicles: Map<string, Vehicle> = new Map();
  public latestLocations: Map<string, LocationTelemetry> = new Map();
  public locationHistory: LocationTelemetry[] = [];
  public incidents: Map<string, Incident> = new Map();
  public vehicleDefects: Map<string, VehicleDefect> = new Map();
  public messages: OperationMessage[] = [];
  public auditLogs: AuditLogEntry[] = [];
  public dispatchUsers: Map<string, DispatchUser> = new Map();
  public idempotencyKeys: Map<string, IdempotencyRecord> = new Map();
  public refreshSessions: Map<string, RefreshSession> = new Map();
  public deviceRegistrations: Map<string, DeviceRegistration> = new Map();
  public evidenceMetadata: Map<string, EvidenceMetadata> = new Map();
  public appConfig: AppConfig = {
    minSupportedAppVersion: '1.0.0',
    latestAppVersion: '1.0.0',
    supportPhoneNumber: '1300 000 178',
    supportEmail: 'dispatch@1stclassexpress.com.au',
    features: {
      liveTracking: true,
      barcodeScanner: true,
      geofencing: true,
      messaging: true,
      offlineSync: true,
      delayPrompts: true
    }
  };

  private repository: StateRepository | null = null;
  private postgresRepository: PostgresStateRepository | null = null;
  private saveQueue: Promise<void> = Promise.resolve();

  public async initialize(repository: StateRepository = createStateRepository()): Promise<void> {
    this.repository = repository;
    this.postgresRepository = repository instanceof PostgresStateRepository ? repository : null;
    if (this.postgresRepository) {
      if (!await this.postgresRepository.health()) throw new Error('PostgreSQL is unavailable');
      return;
    }
    const state = await repository.load();
    if (state) this.restore(state);
  }

  public async persist(): Promise<void> {
    if (!this.repository) return;
    if (this.postgresRepository) return;
    const snapshot = this.snapshot();
    this.saveQueue = this.saveQueue.then(() => this.repository!.save(snapshot));
    await this.saveQueue;
  }

  public async health(): Promise<boolean> {
    return this.repository ? this.repository.health() : false;
  }

  public async close(): Promise<void> {
    await this.saveQueue;
    await this.repository?.close();
  }

  public get usingPostgres(): boolean { return this.postgresRepository !== null; }

  public async transaction<T>(work: () => Promise<T>): Promise<T> {
    return this.postgresRepository ? this.postgresRepository.transaction(work) : work();
  }

  public async get<T>(type: TransportEntityType, id: string): Promise<T | undefined> {
    if (this.postgresRepository) return this.postgresRepository.getEntity<T>(type, id);
    if (type === 'appConfig') return id === 'singleton' ? this.appConfig as T : undefined;
    const value = this.memoryEntries(type).find(([entityId]) => entityId === id)?.[1];
    return value === undefined ? undefined : structuredClone(value) as T;
  }

  public async list<T>(type: TransportEntityType): Promise<T[]> {
    if (this.postgresRepository) return this.postgresRepository.listEntities<T>(type);
    if (type === 'appConfig') return [this.appConfig as T];
    return this.memoryEntries(type).map(([, value]) => structuredClone(value) as T);
  }

  public async put<T extends object>(
    type: TransportEntityType, id: string, value: T, options: EntityWriteOptions = {}
  ): Promise<boolean> {
    if (this.postgresRepository) return this.postgresRepository.putEntity(type, id, value, options);
    if (options.expectedRevision !== undefined) {
      const current = await this.get<{ revision?: number }>(type, id);
      if (current?.revision !== options.expectedRevision) return false;
    }
    this.setMemoryEntity(type, id, value as Record<string, unknown>);
    return true;
  }

  public async remove(type: TransportEntityType, id: string): Promise<void> {
    if (this.postgresRepository) return this.postgresRepository.deleteEntity(type, id);
    this.deleteMemoryEntity(type, id);
  }

  public async readIdempotency(scope: string): Promise<IdempotencyEntry | undefined> {
    if (this.postgresRepository) return this.postgresRepository.readIdempotency(scope);
    const record = this.idempotencyKeys.get(scope);
    if (!record || record.expiresAt <= Date.now()) {
      this.idempotencyKeys.delete(scope);
      return undefined;
    }
    return record;
  }

  public async reserveIdempotency(scope: string, fingerprint: string, expiresAt: number): Promise<boolean> {
    if (this.postgresRepository) return this.postgresRepository.reserveIdempotency(scope, fingerprint, expiresAt);
    if (this.idempotencyKeys.has(scope)) return false;
    this.idempotencyKeys.set(scope, {
      scope, requestFingerprint: fingerprint, statusCode: 0, response: null, createdAt: Date.now(), expiresAt
    });
    return true;
  }

  public async completeIdempotency(scope: string, statusCode: number, response: unknown): Promise<void> {
    if (this.postgresRepository) return this.postgresRepository.completeIdempotency(scope, statusCode, response);
    const record = this.idempotencyKeys.get(scope);
    if (record) { record.statusCode = statusCode; record.response = response; }
  }

  public async pruneTelemetry(retentionDays: number, maximumPoints: number): Promise<void> {
    if (this.postgresRepository) return this.postgresRepository.pruneTelemetry(retentionDays, maximumPoints);
    if (retentionDays > 0) {
      const cutoff = Date.now() - retentionDays * 86400000;
      this.locationHistory = this.locationHistory.filter(point => point.recordedAt >= cutoff);
    }
    if (maximumPoints > 0 && this.locationHistory.length > maximumPoints) {
      this.locationHistory = this.locationHistory.slice(-maximumPoints);
    }
  }

  private memoryEntries(type: Exclude<TransportEntityType, 'appConfig'>): Array<[string, unknown]> {
    switch (type) {
      case 'jobs': return Array.from(this.jobs.entries());
      case 'drivers': return Array.from(this.drivers.entries());
      case 'vehicles': return Array.from(this.vehicles.entries());
      case 'latestLocations': return Array.from(this.latestLocations.entries());
      case 'telemetry': return this.locationHistory.map((value, index) => [`${value.driverId}:${value.recordedAt}:${index}`, value]);
      case 'incidents': return Array.from(this.incidents.entries());
      case 'vehicleDefects': return Array.from(this.vehicleDefects.entries());
      case 'messages': return this.messages.map(value => [value.id, value]);
      case 'auditLogs': return this.auditLogs.map(value => [value.id, value]);
      case 'dispatchUsers': return Array.from(this.dispatchUsers.entries());
      case 'refreshSessions': return Array.from(this.refreshSessions.entries());
      case 'deviceRegistrations': return Array.from(this.deviceRegistrations.entries());
      case 'evidenceMetadata': return Array.from(this.evidenceMetadata.entries());
    }
  }

  private setMemoryEntity(type: TransportEntityType, id: string, value: Record<string, unknown>): void {
    if (type === 'appConfig') { this.appConfig = value as unknown as AppConfig; return; }
    const replaceArray = <T extends { id: string }>(items: T[], next: T) => {
      const index = items.findIndex(item => item.id === id);
      if (index === -1) items.push(next); else items[index] = next;
    };
    switch (type) {
      case 'jobs': this.jobs.set(id, value as unknown as Job); break;
      case 'drivers': this.drivers.set(id, value as unknown as Driver); break;
      case 'vehicles': this.vehicles.set(id, value as unknown as Vehicle); break;
      case 'latestLocations': this.latestLocations.set(id, value as unknown as LocationTelemetry); break;
      case 'telemetry': this.locationHistory.push(value as unknown as LocationTelemetry); break;
      case 'incidents': this.incidents.set(id, value as unknown as Incident); break;
      case 'vehicleDefects': this.vehicleDefects.set(id, value as unknown as VehicleDefect); break;
      case 'messages': replaceArray(this.messages, value as unknown as OperationMessage); break;
      case 'auditLogs': replaceArray(this.auditLogs, value as unknown as AuditLogEntry); break;
      case 'dispatchUsers': this.dispatchUsers.set(id, value as unknown as DispatchUser); break;
      case 'refreshSessions': this.refreshSessions.set(id, value as unknown as RefreshSession); break;
      case 'deviceRegistrations': this.deviceRegistrations.set(id, value as unknown as DeviceRegistration); break;
      case 'evidenceMetadata': this.evidenceMetadata.set(id, value as unknown as EvidenceMetadata); break;
    }
  }

  private deleteMemoryEntity(type: TransportEntityType, id: string): void {
    if (type === 'appConfig') return;
    switch (type) {
      case 'jobs': this.jobs.delete(id); break;
      case 'drivers': this.drivers.delete(id); break;
      case 'vehicles': this.vehicles.delete(id); break;
      case 'latestLocations': this.latestLocations.delete(id); break;
      case 'telemetry': this.locationHistory = this.locationHistory.filter((_, index) => `${this.locationHistory[index].driverId}:${this.locationHistory[index].recordedAt}:${index}` !== id); break;
      case 'incidents': this.incidents.delete(id); break;
      case 'vehicleDefects': this.vehicleDefects.delete(id); break;
      case 'messages': this.messages = this.messages.filter(value => value.id !== id); break;
      case 'auditLogs': this.auditLogs = this.auditLogs.filter(value => value.id !== id); break;
      case 'dispatchUsers': this.dispatchUsers.delete(id); break;
      case 'refreshSessions': this.refreshSessions.delete(id); break;
      case 'deviceRegistrations': this.deviceRegistrations.delete(id); break;
      case 'evidenceMetadata': this.evidenceMetadata.delete(id); break;
    }
  }

  private snapshot(): PersistedTransportState {
    const fromMap = (map: Map<string, unknown>) => Object.fromEntries(map.entries());
    const fromArray = (values: any[]) => Object.fromEntries(values.map(value => [value.id, value]));
    return {
      jobs: fromMap(this.jobs as Map<string, unknown>),
      drivers: fromMap(this.drivers as Map<string, unknown>),
      vehicles: fromMap(this.vehicles as Map<string, unknown>),
      latestLocations: fromMap(this.latestLocations as Map<string, unknown>),
      telemetry: Object.fromEntries(this.locationHistory.map((value, index) => [
        `${value.driverId}:${value.recordedAt}:${index}`, value
      ])),
      incidents: fromMap(this.incidents as Map<string, unknown>),
      vehicleDefects: fromMap(this.vehicleDefects as Map<string, unknown>),
      messages: fromArray(this.messages),
      auditLogs: fromArray(this.auditLogs),
      dispatchUsers: fromMap(this.dispatchUsers as Map<string, unknown>),
      idempotency: fromMap(this.idempotencyKeys as Map<string, unknown>),
      refreshSessions: fromMap(this.refreshSessions as Map<string, unknown>),
      deviceRegistrations: fromMap(this.deviceRegistrations as Map<string, unknown>),
      evidenceMetadata: fromMap(this.evidenceMetadata as Map<string, unknown>),
      appConfig: this.appConfig
    };
  }

  private restore(state: PersistedTransportState): void {
    const toMap = <T>(values: Record<string, unknown>) =>
      new Map(Object.entries(values) as Array<[string, T]>);
    this.jobs = toMap<Job>(state.jobs);
    this.drivers = toMap<Driver>(state.drivers);
    this.vehicles = toMap<Vehicle>(state.vehicles);
    this.latestLocations = toMap<LocationTelemetry>(state.latestLocations);
    this.locationHistory = Object.values(state.telemetry) as LocationTelemetry[];
    this.incidents = toMap<Incident>(state.incidents);
    this.vehicleDefects = toMap<VehicleDefect>(state.vehicleDefects);
    this.messages = Object.values(state.messages) as OperationMessage[];
    this.auditLogs = (Object.values(state.auditLogs) as AuditLogEntry[])
      .sort((a, b) => b.timestamp - a.timestamp);
    this.dispatchUsers = toMap<DispatchUser>(state.dispatchUsers);
    this.idempotencyKeys = toMap<IdempotencyRecord>(state.idempotency);
    this.refreshSessions = toMap<RefreshSession>(state.refreshSessions || {});
    this.deviceRegistrations = toMap<DeviceRegistration>(state.deviceRegistrations || {});
    this.evidenceMetadata = toMap<EvidenceMetadata>(state.evidenceMetadata || {});
    if (state.appConfig) this.appConfig = state.appConfig as AppConfig;
  }

  public seed(): void {
    if (process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging') {
      throw new Error('Development fixtures are disabled outside development and tests');
    }
    this.clear();
    // 1. Dispatch Users
    const adminUser: DispatchUser = {
      id: 'usr-admin-1',
      email: 'ops@1stclassexpress.com.au',
      name: 'Operations Manager',
      role: 'ADMIN',
      passwordHash: hashCredential(developmentDispatchPassword()),
      active: true
    };
    const dispatcherUser: DispatchUser = {
      id: 'usr-disp-1',
      email: 'controller@1stclassexpress.com.au',
      name: 'Chief Dispatcher',
      role: 'DISPATCHER',
      passwordHash: hashCredential(developmentDispatchPassword()),
      active: true
    };
    const viewerUser: DispatchUser = {
      id: 'usr-view-1',
      email: 'viewer@1stclassexpress.com.au',
      name: 'Operations Observer',
      role: 'VIEW_ONLY',
      passwordHash: hashCredential(developmentDispatchPassword()),
      active: true
    };
    this.dispatchUsers.set(adminUser.email, adminUser);
    this.dispatchUsers.set(dispatcherUser.email, dispatcherUser);
    this.dispatchUsers.set(viewerUser.email, viewerUser);

    // 2. Drivers
    const driversList: Driver[] = [
      {
        id: 'DRV-101',
        name: 'John Smith',
        phone: '0400 111 222',
        pinHash: hashCredential(developmentDriverPin('DRV-101')),
        active: true,
        licenseNumber: 'VIC-9988771',
        shiftStatus: 'ON_DUTY',
        currentVehicleId: 'VH-101',
        currentShiftId: 'shift-101-today',
        activeJobId: 'job-101',
        appVersion: '1.0.0',
        pushToken: null,
        lastSeen: Date.now() - 15000
      },
      {
        id: 'DRV-102',
        name: 'Michael Evans',
        phone: '0400 222 333',
        pinHash: hashCredential(developmentDriverPin('DRV-102')),
        active: true,
        licenseNumber: 'NSW-4455662',
        shiftStatus: 'ON_DUTY',
        currentVehicleId: 'VH-102',
        currentShiftId: 'shift-102-today',
        activeJobId: 'job-102',
        appVersion: '1.0.0',
        pushToken: null,
        lastSeen: Date.now() - 25000
      },
      {
        id: 'DRV-103',
        name: 'Sarah Connor',
        phone: '0400 333 444',
        pinHash: hashCredential(developmentDriverPin('DRV-103')),
        active: true,
        licenseNumber: 'QLD-1122334',
        shiftStatus: 'ON_DUTY',
        currentVehicleId: 'VH-103',
        currentShiftId: 'shift-103-today',
        activeJobId: 'job-103',
        appVersion: '1.0.0',
        pushToken: null,
        lastSeen: Date.now() - 40000
      },
      {
        id: 'DRV-104',
        name: 'David Miller',
        phone: '0400 444 555',
        pinHash: hashCredential(developmentDriverPin('DRV-104')),
        active: true,
        licenseNumber: 'SA-7788990',
        shiftStatus: 'ON_BREAK',
        currentVehicleId: 'VH-104',
        currentShiftId: 'shift-104-today',
        activeJobId: null,
        appVersion: '1.0.0',
        pushToken: null,
        lastSeen: Date.now() - 120000
      },
      {
        id: 'DRV-105',
        name: 'Marcus Vance',
        phone: '0400 555 666',
        pinHash: hashCredential(developmentDriverPin('DRV-105')),
        active: true,
        licenseNumber: 'WA-3344556',
        shiftStatus: 'OFF_DUTY',
        currentVehicleId: null,
        currentShiftId: null,
        activeJobId: null,
        appVersion: '1.0.0',
        pushToken: null,
        lastSeen: Date.now() - 3600000
      }
    ];
    for (const d of driversList) this.drivers.set(d.id, d);

    // 3. Vehicles
    const vehiclesList: Vehicle[] = [
      {
        id: 'VH-101',
        rego: '1CE-001',
        makeModel: 'Kenworth T610 Prime Mover',
        type: 'Heavy Rigid (26t)',
        status: 'ON_JOB',
        currentDriverId: 'DRV-101',
        odometer: 142580,
        lastPreStartAt: Date.now() - 18000000,
        activeDefectCount: 0
      },
      {
        id: 'VH-102',
        rego: '1CE-002',
        makeModel: 'Volvo FH16 700 Semi-Trailer',
        type: 'Articulated (42t)',
        status: 'ON_JOB',
        currentDriverId: 'DRV-102',
        odometer: 218940,
        lastPreStartAt: Date.now() - 14000000,
        activeDefectCount: 0
      },
      {
        id: 'VH-103',
        rego: '1CE-003',
        makeModel: 'Mercedes-Benz Actros 2653',
        type: 'Heavy Rigid (24t)',
        status: 'ON_JOB',
        currentDriverId: 'DRV-103',
        odometer: 98400,
        lastPreStartAt: Date.now() - 12000000,
        activeDefectCount: 0
      },
      {
        id: 'VH-104',
        rego: '1CE-004',
        makeModel: 'Isuzu FSD 140-260 Curtain Sider',
        type: 'Medium Rigid (14t)',
        status: 'DEFECT',
        currentDriverId: 'DRV-104',
        odometer: 64200,
        lastPreStartAt: Date.now() - 25000000,
        activeDefectCount: 1
      },
      {
        id: 'VH-105',
        rego: '1CE-005',
        makeModel: 'Scania R500 B-Double',
        type: 'B-Double (68t)',
        status: 'AVAILABLE',
        currentDriverId: null,
        odometer: 312000,
        lastPreStartAt: Date.now() - 86400000,
        activeDefectCount: 0
      }
    ];
    for (const v of vehiclesList) this.vehicles.set(v.id, v);

    // 4. Initial Telemetry
    const loc1: LocationTelemetry = {
      driverId: 'DRV-101',
      vehicleId: 'VH-101',
      jobId: 'job-101',
      latitude: -37.8285,
      longitude: 144.7554,
      accuracyMeters: 4.8,
      speedMetersPerSecond: 18.2, // ~65 km/h
      bearingDegrees: 182.0,
      altitudeMeters: 45.0,
      batteryLevel: 88,
      networkState: 'CELLULAR',
      source: 'FUSED_LOCATION',
      recordedAt: Date.now() - 15000,
      receivedAt: Date.now() - 14000
    };
    const loc2: LocationTelemetry = {
      driverId: 'DRV-102',
      vehicleId: 'VH-102',
      jobId: 'job-102',
      latitude: -33.9214,
      longitude: 151.1892,
      accuracyMeters: 5.2,
      speedMetersPerSecond: 22.5, // ~81 km/h
      bearingDegrees: 240.0,
      altitudeMeters: 22.0,
      batteryLevel: 74,
      networkState: 'CELLULAR',
      source: 'FUSED_LOCATION',
      recordedAt: Date.now() - 25000,
      receivedAt: Date.now() - 24000
    };
    const loc3: LocationTelemetry = {
      driverId: 'DRV-103',
      vehicleId: 'VH-103',
      jobId: 'job-103',
      latitude: -27.4215,
      longitude: 153.1582,
      accuracyMeters: 3.9,
      speedMetersPerSecond: 14.0, // ~50 km/h
      bearingDegrees: 15.0,
      altitudeMeters: 10.0,
      batteryLevel: 92,
      networkState: 'CELLULAR',
      source: 'FUSED_LOCATION',
      recordedAt: Date.now() - 40000,
      receivedAt: Date.now() - 38000
    };
    this.latestLocations.set('DRV-101', loc1);
    this.latestLocations.set('DRV-102', loc2);
    this.latestLocations.set('DRV-103', loc3);
    this.locationHistory.push(loc1, loc2, loc3);

    // 5. Initial Jobs
    const jobsList: Job[] = [
      {
        id: 'job-101',
        reference: '1CE-MEL-101',
        status: 'EN_ROUTE_DELIVERY',
        priority: 'URGENT',
        assignedDriverId: 'DRV-101',
        assignedVehicleId: 'VH-101',
        pickup: {
          address: '12 Industrial Ave',
          suburb: 'Truganina VIC',
          lat: -37.83,
          lng: 144.75,
          companyName: 'Truganina DC',
          contactName: 'Mark Stevens',
          contactPhone: '03 9000 1111'
        },
        delivery: {
          address: '88 Port Road',
          suburb: 'West Melbourne VIC',
          lat: -37.81,
          lng: 144.91,
          companyName: 'Express Logistics Hub',
          contactName: 'Receiving Dock A',
          contactPhone: '03 9000 2222'
        },
        pickupWindowStart: '07:30',
        pickupWindowEnd: '09:00',
        deliveryWindowStart: '10:00',
        deliveryWindowEnd: '11:30',
        freightDescription: '4 Pallets Automotive Spares',
        itemCount: 4,
        specialInstructions: 'Reversing camera required. Tail-lift required on arrival.',
        dangerousGoods: false,
        revision: 3,
        serverUpdatedAt: Date.now() - 600000,
        timeline: [
          {
            id: 'tl-1',
            type: 'ASSIGNED',
            description: 'Job assigned to John Smith (VH-101)',
            timestamp: Date.now() - 7200000,
            actor: 'Dispatcher'
          },
          {
            id: 'tl-2',
            type: 'ACCEPTED',
            description: 'Driver accepted job',
            timestamp: Date.now() - 7000000,
            actor: 'John Smith'
          },
          {
            id: 'tl-3',
            type: 'AT_PICKUP',
            description: 'Arrived at Truganina DC',
            timestamp: Date.now() - 5400000,
            actor: 'John Smith',
            lat: -37.83,
            lng: 144.75
          },
          {
            id: 'tl-4',
            type: 'PICKED_UP',
            description: 'Freight loaded and verified (4 items)',
            timestamp: Date.now() - 4800000,
            actor: 'John Smith'
          },
          {
            id: 'tl-5',
            type: 'EN_ROUTE_DELIVERY',
            description: 'Departed pickup en route to West Melbourne',
            timestamp: Date.now() - 4200000,
            actor: 'John Smith'
          }
        ],
        pod: null,
        createdAt: Date.now() - 7500000
      },
      {
        id: 'job-102',
        reference: '1CE-SYD-102',
        status: 'IN_PROGRESS',
        priority: 'NORMAL',
        assignedDriverId: 'DRV-102',
        assignedVehicleId: 'VH-102',
        pickup: {
          address: '45 Foreshore Road',
          suburb: 'Port Botany NSW',
          lat: -33.96,
          lng: 151.21,
          companyName: 'Botany Container Terminal',
          contactName: 'Dave Dockmaster',
          contactPhone: '02 9111 2222'
        },
        delivery: {
          address: '100 Moorebank Ave',
          suburb: 'Moorebank NSW',
          lat: -33.93,
          lng: 150.92,
          companyName: 'Moorebank National DC',
          contactName: 'Inbound Receiving',
          contactPhone: '02 9111 3333'
        },
        pickupWindowStart: '08:00',
        pickupWindowEnd: '10:00',
        deliveryWindowStart: '12:00',
        deliveryWindowEnd: '14:00',
        freightDescription: '2 Containers Electronics Components',
        itemCount: 2,
        specialInstructions: 'MSDS documentation attached. Gate 4 entry only.',
        dangerousGoods: false,
        revision: 2,
        serverUpdatedAt: Date.now() - 3600000,
        timeline: [
          {
            id: 'tl-102-1',
            type: 'ASSIGNED',
            description: 'Job assigned to Michael Evans (VH-102)',
            timestamp: Date.now() - 5000000,
            actor: 'Dispatcher'
          },
          {
            id: 'tl-102-2',
            type: 'ACCEPTED',
            description: 'Driver accepted job',
            timestamp: Date.now() - 4800000,
            actor: 'Michael Evans'
          }
        ],
        pod: null,
        createdAt: Date.now() - 5200000
      },
      {
        id: 'job-103',
        reference: '1CE-BNE-103',
        status: 'AT_PICKUP',
        priority: 'HIGH',
        assignedDriverId: 'DRV-103',
        assignedVehicleId: 'VH-103',
        pickup: {
          address: '7 Whinstanes Street',
          suburb: 'Port of Brisbane QLD',
          lat: -27.42,
          lng: 153.15,
          companyName: 'Brisbane Port Cold Storage',
          contactName: 'Gary Frozen',
          contactPhone: '07 3333 4444'
        },
        delivery: {
          address: '15 Logistics Drive',
          suburb: 'Yatala QLD',
          lat: -27.75,
          lng: 153.22,
          companyName: 'Yatala Regional Cold Hub',
          contactName: 'Dock Manager',
          contactPhone: '07 3333 5555'
        },
        pickupWindowStart: '09:00',
        pickupWindowEnd: '11:00',
        deliveryWindowStart: '13:00',
        deliveryWindowEnd: '15:00',
        freightDescription: '8 Pallets Temperature-Controlled Dairy',
        itemCount: 8,
        specialInstructions: 'Maintain temp between +2C and +4C. Check data logger.',
        dangerousGoods: false,
        revision: 2,
        serverUpdatedAt: Date.now() - 1800000,
        timeline: [
          {
            id: 'tl-103-1',
            type: 'ASSIGNED',
            description: 'Job assigned to Sarah Connor (VH-103)',
            timestamp: Date.now() - 4000000,
            actor: 'Dispatcher'
          },
          {
            id: 'tl-103-2',
            type: 'ACCEPTED',
            description: 'Driver accepted job',
            timestamp: Date.now() - 3800000,
            actor: 'Sarah Connor'
          },
          {
            id: 'tl-103-3',
            type: 'AT_PICKUP',
            description: 'Arrived at Brisbane Port Cold Storage',
            timestamp: Date.now() - 1200000,
            actor: 'Sarah Connor',
            lat: -27.42,
            lng: 153.15
          }
        ],
        pod: null,
        createdAt: Date.now() - 4200000
      },
      {
        id: 'job-104',
        reference: '1CE-ADL-104',
        status: 'ASSIGNED',
        priority: 'NORMAL',
        assignedDriverId: null,
        assignedVehicleId: null,
        pickup: {
          address: '1 James Schofield Drive',
          suburb: 'Adelaide Airport SA',
          lat: -34.94,
          lng: 138.53,
          companyName: 'Qantas Freight Air Cargo',
          contactName: 'Freight Agent',
          contactPhone: '08 8111 2222'
        },
        delivery: {
          address: '50 Wingfield Road',
          suburb: 'Wingfield SA',
          lat: -34.84,
          lng: 138.56,
          companyName: 'Heavy Machinery Spares',
          contactName: 'Receiving Office',
          contactPhone: '08 8111 3333'
        },
        pickupWindowStart: '13:00',
        pickupWindowEnd: '15:00',
        deliveryWindowStart: '16:00',
        deliveryWindowEnd: '18:00',
        freightDescription: '1 Heavy Crate Hydraulic Pumps',
        itemCount: 1,
        specialInstructions: 'Forklift unload only. Weight 850kg.',
        dangerousGoods: false,
        revision: 1,
        serverUpdatedAt: Date.now() - 7200000,
        timeline: [
          {
            id: 'tl-104-1',
            type: 'CREATED',
            description: 'Job created in system',
            timestamp: Date.now() - 7200000,
            actor: 'Dispatcher'
          }
        ],
        pod: null,
        createdAt: Date.now() - 7200000
      }
    ];
    for (const j of jobsList) this.jobs.set(j.id, j);

    // 6. Incidents
    const inc1: Incident = {
      id: 'inc-101',
      driverId: 'DRV-104',
      vehicleId: 'VH-104',
      jobId: null,
      category: 'BREAKDOWN',
      description: 'Rear left tyre puncture on Western Ring Road. Safe in emergency bay.',
      severity: 'HIGH',
      evidenceIds: [],
      status: 'OPEN',
      latitude: -37.76,
      longitude: 144.82,
      reportedAt: Date.now() - 3600000,
      acknowledgedAt: null,
      acknowledgedBy: null,
      resolvedAt: null,
      resolvedBy: null,
      opsNotes: 'Tyre service team dispatched. ETA 25 minutes.'
    };
    this.incidents.set(inc1.id, inc1);

    // 7. Vehicle Defects
    const def1: VehicleDefect = {
      id: 'def-101',
      vehicleId: 'VH-104',
      driverId: 'DRV-104',
      shiftId: 'shift-104-today',
      defectDescription: 'Tyre tread damage observed on rear dual outer tyre during pre-start check.',
      severity: 'HIGH',
      evidenceIds: [],
      status: 'UNDER_REVIEW',
      reportedAt: Date.now() - 25000000,
      resolvedAt: null,
      resolvedBy: null
    };
    this.vehicleDefects.set(def1.id, def1);

    // 8. Messages
    this.messages.push(
      {
        id: 'msg-1',
        senderId: 'usr-disp-1',
        senderName: 'Chief Dispatcher',
        recipientId: 'DRV-101',
        driverId: 'DRV-101',
        jobId: 'job-101',
        category: 'JOB_UPDATE',
        content: 'Dock A at West Melbourne is open until 12:00. Priority dock assigned.',
        sentAt: Date.now() - 3600000,
        readAt: Date.now() - 3500000,
        isUrgent: false
      },
      {
        id: 'msg-2',
        senderId: 'usr-disp-1',
        senderName: 'Chief Dispatcher',
        recipientId: 'DRV-104',
        driverId: 'DRV-104',
        jobId: null,
        category: 'URGENT',
        content: 'Tyre technician en route to your location. Stay inside cabin.',
        sentAt: Date.now() - 1800000,
        readAt: Date.now() - 1700000,
        isUrgent: true
      }
    );

    // 9. Initial Audit Logs
    this.auditLogs.push(
      {
        id: 'aud-1',
        timestamp: Date.now() - 7200000,
        userId: 'usr-disp-1',
        userName: 'Chief Dispatcher',
        userRole: 'DISPATCHER',
        action: 'CREATE_JOB',
        entityType: 'JOB',
        entityId: 'job-101',
        newState: { reference: '1CE-MEL-101', priority: 'URGENT' },
        notes: 'Created urgent Melbourne express job'
      },
      {
        id: 'aud-2',
        timestamp: Date.now() - 7200000,
        userId: 'usr-disp-1',
        userName: 'Chief Dispatcher',
        userRole: 'DISPATCHER',
        action: 'ASSIGN_JOB',
        entityType: 'JOB',
        entityId: 'job-101',
        newState: { assignedDriverId: 'DRV-101', assignedVehicleId: 'VH-101' },
        notes: 'Assigned to John Smith on VH-101'
      }
    );
  }

  public recordAudit(
    userId: string,
    userName: string,
    userRole: any,
    action: string,
    entityType: any,
    entityId: string,
    previousState?: any,
    newState?: any,
    notes?: string
  ): AuditLogEntry {
    const entry: AuditLogEntry = {
      id: `aud-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
      timestamp: Date.now(),
      userId,
      userName,
      userRole,
      action,
      entityType,
      entityId,
      previousState,
      newState,
      notes,
      requestId: currentRequestId()
    };
    this.auditLogs.unshift(entry);
    return entry;
  }

  public async recordAuditAsync(
    userId: string,
    userName: string,
    userRole: any,
    action: string,
    entityType: any,
    entityId: string,
    previousState?: any,
    newState?: any,
    notes?: string
  ): Promise<AuditLogEntry> {
    const entry = this.recordAudit(userId, userName, userRole, action, entityType, entityId, previousState, newState, notes);
    await this.put('auditLogs', entry.id, entry);
    return entry;
  }

  public clear(): void {
    this.jobs.clear();
    this.drivers.clear();
    this.vehicles.clear();
    this.latestLocations.clear();
    this.locationHistory = [];
    this.incidents.clear();
    this.vehicleDefects.clear();
    this.messages = [];
    this.auditLogs = [];
    this.dispatchUsers.clear();
    this.idempotencyKeys.clear();
    this.refreshSessions.clear();
    this.deviceRegistrations.clear();
    this.evidenceMetadata.clear();
  }
}

export const db = new TransportDatabase();
