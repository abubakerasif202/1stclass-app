import { randomUUID } from 'node:crypto';
import { db } from '../db';
import { hashCredential } from '../security/credentials';

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function main() {
  await db.initialize();
  const mode = required('PROVISION_TYPE');
  if (mode === 'dispatch') {
    const email = required('PROVISION_EMAIL').toLowerCase();
    if ((await db.list<any>('dispatchUsers')).some(user => user.email.toLowerCase() === email)) {
      throw new Error('Dispatch account already exists');
    }
    const id = randomUUID();
    const role = process.env.PROVISION_ROLE || 'ADMIN';
    if (!['ADMIN', 'DISPATCHER', 'OPERATIONS', 'VIEW_ONLY'].includes(role)) {
      throw new Error('PROVISION_ROLE is invalid');
    }
    await db.put('dispatchUsers', id, {
      id,
      email,
      name: required('PROVISION_NAME'),
      role: role as 'ADMIN' | 'DISPATCHER' | 'OPERATIONS' | 'VIEW_ONLY',
      passwordHash: hashCredential(required('PROVISION_PASSWORD')),
      active: true
    });
  } else if (mode === 'driver') {
    const driverId = required('PROVISION_DRIVER_ID');
    if (await db.get('drivers', driverId)) throw new Error('Driver account already exists');
    await db.put('drivers', driverId, {
      id: driverId,
      name: required('PROVISION_NAME'),
      phone: required('PROVISION_PHONE'),
      pinHash: hashCredential(required('PROVISION_PIN')),
      active: true,
      licenseNumber: required('PROVISION_LICENSE_NUMBER'),
      shiftStatus: 'OFF_DUTY', currentVehicleId: null, currentShiftId: null,
      activeJobId: null, appVersion: '', pushToken: null, lastSeen: 0
    });
  } else {
    throw new Error('PROVISION_TYPE must be dispatch or driver');
  }
  await db.persist();
  await db.close();
  console.log(`Provisioned ${mode} account`);
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : 'Provisioning failed');
  process.exitCode = 1;
});
