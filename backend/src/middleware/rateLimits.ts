import { NextFunction, Request, Response } from 'express';

const buckets = new Map<string, { count: number; resetAt: number }>();
const MAX_BUCKETS = 10_000;

function sensitiveLimit(max: number) {
  return (req: Request, res: Response, next: NextFunction) => {
    const now = Date.now();
    if (buckets.size >= MAX_BUCKETS) {
      for (const [candidate, value] of buckets) {
        if (value.resetAt <= now) buckets.delete(candidate);
      }
      if (buckets.size >= MAX_BUCKETS) {
        res.status(503).json({ error: 'Authentication service is busy', code: 'AUTH_LIMIT_CAPACITY' });
        return;
      }
    }
    const key = `${req.ip}:${req.path}`;
    const current = buckets.get(key);
    const bucket = !current || current.resetAt <= now ? { count: 0, resetAt: now + 15 * 60000 } : current;
    bucket.count += 1;
    buckets.set(key, bucket);
    res.setHeader('RateLimit-Limit', String(max));
    res.setHeader('RateLimit-Remaining', String(Math.max(0, max - bucket.count)));
    res.setHeader('RateLimit-Reset', String(Math.ceil(bucket.resetAt / 1000)));
    if (bucket.count > max) {
      res.setHeader('Retry-After', String(Math.ceil((bucket.resetAt - now) / 1000)));
      res.status(429).json({ error: 'Too many authentication attempts', code: 'AUTH_RATE_LIMITED' });
      return;
    }
    next();
  };
}

export const loginRateLimit = sensitiveLimit(10);
export const refreshRateLimit = sensitiveLimit(30);
