import { AsyncLocalStorage } from 'node:async_hooks';

const requestContext = new AsyncLocalStorage<{ requestId: string }>();

export function withRequestId<T>(requestId: string, callback: () => T): T {
  return requestContext.run({ requestId }, callback);
}

export function currentRequestId(): string | undefined {
  return requestContext.getStore()?.requestId;
}
