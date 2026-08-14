import { App, cert, getApps, initializeApp } from 'firebase-admin/app';
import { getMessaging, MulticastMessage } from 'firebase-admin/messaging';
import { db } from '../db';

export type PushType =
  | 'NEW_JOB' | 'JOB_UPDATED' | 'JOB_CANCELLED'
  | 'DISPATCH_MESSAGE' | 'URGENT_NOTICE' | 'VEHICLE_NOTICE';

const INVALID_TOKEN_CODES = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered'
]);

export class PushNotificationService {
  private readonly projectId = process.env.FCM_PROJECT_ID;
  private readonly clientEmail = process.env.FCM_CLIENT_EMAIL;
  private readonly privateKey = process.env.FCM_PRIVATE_KEY?.replace(/\\n/g, '\n');
  private app: App | null = null;

  constructor() {
    if (!this.configured && (process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging')) {
      throw new Error('FCM project, client email, and private key are required');
    }
  }

  get configured() { return Boolean(this.projectId && this.clientEmail && this.privateKey); }

  private firebaseApp(): App {
    if (!this.configured) throw new Error('FCM is not configured');
    if (this.app) return this.app;
    this.app = getApps().find(candidate => candidate.name === 'tms-push') || initializeApp({
      credential: cert({
        projectId: this.projectId!,
        clientEmail: this.clientEmail!,
        privateKey: this.privateKey!
      }),
      projectId: this.projectId
    }, 'tms-push');
    return this.app;
  }

  async sendToDriver(driverId: string, type: PushType, data: Record<string, string>) {
    if (!this.configured) return { sent: 0, failed: 0, skipped: true };
    const registrations = (await db.list<any>('deviceRegistrations'))
      .filter(device => device.driverId === driverId && device.pushEnabled && device.pushToken);
    if (registrations.length === 0) return { sent: 0, failed: 0, skipped: false };

    let sent = 0;
    let failed = 0;
    let messaging;
    try {
      messaging = getMessaging(this.firebaseApp());
    } catch (error) {
      if (process.env.NODE_ENV !== 'test') {
        console.error(JSON.stringify({ event: 'fcm_initialization_failed', driverId, error: error instanceof Error ? error.name : 'Error' }));
      }
      return { sent: 0, failed: registrations.length, skipped: false };
    }
    for (let offset = 0; offset < registrations.length; offset += 500) {
      const batch = registrations.slice(offset, offset + 500);
      const message: MulticastMessage = {
        tokens: batch.map(registration => registration.pushToken!),
        data: { type, ...data },
        android: { priority: type === 'URGENT_NOTICE' ? 'high' : 'normal' }
      };
      let result;
      try {
        result = await messaging.sendEachForMulticast(message);
      } catch (error) {
        failed += batch.length;
        if (process.env.NODE_ENV !== 'test') {
          console.error(JSON.stringify({ event: 'fcm_batch_failed', driverId, error: error instanceof Error ? error.name : 'Error' }));
        }
        continue;
      }
      sent += result.successCount;
      failed += result.failureCount;
      result.responses.forEach((response, index) => {
        if (response.success || !response.error || !INVALID_TOKEN_CODES.has(response.error.code)) return;
        const registration = batch[index];
        registration.pushEnabled = false;
        registration.pushToken = null;
        registration.updatedAt = Date.now();
      });
    }
    await Promise.all(registrations.map(registration => db.put('deviceRegistrations', registration.deviceId, registration)));
    return { sent, failed, skipped: false };
  }

  async health() { return this.configured; }
}

export const pushNotifications = new PushNotificationService();
