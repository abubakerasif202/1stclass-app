const testPins: Record<string, string> = {
  'DRV-101': '8841', 'DRV-102': '8842', 'DRV-103': '8843', 'DRV-104': '8844', 'DRV-105': '8845'
};

function assertFixtureEnvironment() {
  if (process.env.NODE_ENV === 'production' || process.env.NODE_ENV === 'staging') {
    throw new Error('Development credentials are unavailable outside development and tests');
  }
}

export function developmentDispatchPassword(): string {
  assertFixtureEnvironment();
  return process.env.DEV_DISPATCH_PASSWORD || 'Dispatch2026!';
}

export function developmentDriverPin(driverId: string): string {
  assertFixtureEnvironment();
  return process.env.DEV_DRIVER_PIN || testPins[driverId] || '0000';
}
