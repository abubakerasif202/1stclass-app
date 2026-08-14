export type DriverShiftStatus = 'ON_DUTY' | 'OFF_DUTY' | 'ON_BREAK';

export type JobStatus =
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'AT_PICKUP'
  | 'PICKED_UP'
  | 'EN_ROUTE_DELIVERY'
  | 'AT_DELIVERY'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'ISSUE';

export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type UserRole = 'ADMIN' | 'DISPATCHER' | 'OPERATIONS' | 'VIEW_ONLY';

export interface LocationCoordinates {
  address: string;
  suburb: string;
  lat: number | null;
  lng: number | null;
  companyName: string;
  contactName: string;
  contactPhone: string;
}

export interface TimelineEvent {
  id: string;
  type: string;
  description: string;
  timestamp: number;
  actor: string;
  lat?: number;
  lng?: number;
}

export interface PodRecord {
  recipientName: string;
  signatureEvidenceId: string;
  photoEvidenceIds: string[];
  driverNotes: string;
  completedAt: number;
  latitude?: number;
  longitude?: number;
  status: 'COMPLETE' | 'PENDING';
}

export interface Job {
  id: string;
  reference: string;
  status: JobStatus;
  priority: Priority;
  assignedDriverId: string | null;
  assignedVehicleId: string | null;
  pickup: LocationCoordinates;
  delivery: LocationCoordinates;
  pickupWindowStart: string;
  pickupWindowEnd: string;
  deliveryWindowStart: string;
  deliveryWindowEnd: string;
  freightDescription: string;
  itemCount: number;
  specialInstructions: string;
  dangerousGoods: boolean;
  revision: number;
  serverUpdatedAt: number;
  timeline: TimelineEvent[];
  pod: PodRecord | null;
  createdAt: number;
  cancellationReason?: string;
  cancelledAt?: number;
  cancelledBy?: string;
}

export interface Driver {
  id: string;
  name: string;
  phone: string;
  licenseNumber: string;
  shiftStatus: DriverShiftStatus;
  currentVehicleId: string | null;
  currentShiftId: string | null;
  activeJobId: string | null;
  appVersion: string;
  pushToken: string | null;
  lastSeen: number;
  vehicleRego?: string;
  activeJobReference?: string;
}

export interface Vehicle {
  id: string;
  rego: string;
  makeModel: string;
  type: string;
  status: 'AVAILABLE' | 'ON_JOB' | 'DEFECT' | 'OFFLINE';
  currentDriverId: string | null;
  driverName?: string;
  odometer: number;
  lastPreStartAt: number | null;
  activeDefectCount: number;
}

export interface FleetLocationItem {
  driverId: string;
  driverName: string;
  driverPhone: string;
  shiftStatus: DriverShiftStatus;
  vehicleId: string;
  vehicleRego: string;
  vehicleModel: string;
  jobId: string | null;
  jobReference: string;
  jobStatus: JobStatus | null;
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  speedKmh: number;
  bearingDegrees: number | null;
  batteryLevel: number | null;
  networkState: string | null;
  recordedAt: number;
  ageSeconds: number;
  isStale: boolean;
  movementStatus: string;
}

export interface Incident {
  id: string;
  driverId: string;
  driverName?: string;
  vehicleId: string | null;
  vehicleRego?: string;
  jobId: string | null;
  jobReference?: string;
  category: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  evidenceIds: string[];
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  latitude: number | null;
  longitude: number | null;
  reportedAt: number;
  acknowledgedAt: number | null;
  acknowledgedBy: string | null;
  resolvedAt: number | null;
  resolvedBy: string | null;
  opsNotes: string;
}

export interface VehicleDefect {
  id: string;
  vehicleId: string;
  driverId: string;
  shiftId: string;
  defectDescription: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  evidenceIds: string[];
  status: 'NEW' | 'ACKNOWLEDGED' | 'UNDER_REVIEW' | 'RESOLVED';
  reportedAt: number;
  resolvedAt: number | null;
  resolvedBy: string | null;
}

export interface OperationMessage {
  id: string;
  senderId: string;
  senderName: string;
  recipientId: string | null;
  driverId: string | null;
  jobId: string | null;
  category: 'DISPATCH' | 'JOB_UPDATE' | 'DRIVER_NOTICE' | 'URGENT';
  content: string;
  sentAt: number;
  readAt: number | null;
  isUrgent: boolean;
}

export interface AuditLogEntry {
  id: string;
  timestamp: number;
  userId: string;
  userName: string;
  userRole: UserRole;
  action: string;
  entityType: string;
  entityId: string;
  previousState?: any;
  newState?: any;
  notes?: string;
}

export interface AppConfig {
  minSupportedAppVersion: string;
  latestAppVersion: string;
  supportPhoneNumber: string;
  supportEmail: string;
  features: {
    liveTracking: boolean;
    barcodeScanner: boolean;
    geofencing: boolean;
    messaging: boolean;
    offlineSync: boolean;
    delayPrompts: boolean;
  };
}

export interface DispatchUser {
  id: string;
  email: string;
  name: string;
  role: UserRole;
}
