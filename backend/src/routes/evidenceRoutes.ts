import { createHash, randomUUID } from 'node:crypto';
import { Router } from 'express';
import multer from 'multer';
import { db } from '../db';
import { AuthenticatedRequest, authenticate, idempotencyMiddleware, requireDriver } from '../middleware/auth';
import { createEvidenceStorage } from '../storage/createEvidenceStorage';

const router = Router();
const storage = createEvidenceStorage();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024, files: 1, fields: 8 }
});

function detectedImageType(buffer: Buffer): 'image/jpeg' | 'image/png' | null {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'image/jpeg';
  }
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
    return 'image/png';
  }
  return null;
}

router.post(
  '/driver/evidence',
  authenticate,
  requireDriver,
  upload.single('file'),
  idempotencyMiddleware,
  async (req: AuthenticatedRequest, res, next) => {
    try {
      if (!req.file) {
        res.status(400).json({ error: 'JPEG or PNG evidence file is required' });
        return;
      }
      const contentType = detectedImageType(req.file.buffer);
      if (!contentType || !['image/jpeg', 'image/png'].includes(req.file.mimetype)) {
        res.status(415).json({ error: 'Only valid JPEG and PNG evidence is accepted' });
        return;
      }
      let metadata: any;
      try { metadata = JSON.parse(req.body.metadata || '{}'); }
      catch { res.status(400).json({ error: 'Evidence metadata must be valid JSON' }); return; }

      const driverId = req.user!.driverId!;
      if (metadata.driverId && metadata.driverId !== driverId) {
        res.status(403).json({ error: 'Forbidden: Cross-driver evidence denied' });
        return;
      }
      if (metadata.jobId) {
        const job = await db.get<any>('jobs', metadata.jobId);
        if (!job || job.assignedDriverId !== driverId) {
          res.status(403).json({ error: 'Forbidden: Evidence job is not assigned to this driver' });
          return;
        }
      }

      const evidenceId = metadata.evidenceId || randomUUID();
      const extension = contentType === 'image/png' ? 'png' : 'jpg';
      const storageKey = `${driverId}/${evidenceId}.${extension}`;
      const object = await storage.put(storageKey, contentType, req.file.buffer);
      await db.put('evidenceMetadata', evidenceId, {
        evidenceId,
        jobId: metadata.jobId || null,
        driverId,
        type: metadata.type || 'OTHER',
        contentType,
        sizeBytes: object.sizeBytes,
        sha256: createHash('sha256').update(req.file.buffer).digest('hex'),
        storageKey,
        createdAt: Date.now()
      });
      res.status(201).json({ success: true, evidenceId, receivedAt: Date.now(), sizeBytes: object.sizeBytes });
    } catch (error) { next(error); }
  }
);

router.get('/evidence/:id', authenticate, async (req: AuthenticatedRequest, res, next) => {
  try {
    const metadata = await db.get<any>('evidenceMetadata', req.params.id);
    if (!metadata) { res.status(404).json({ error: 'Evidence not found' }); return; }
    if (req.user?.role === 'DRIVER' && metadata.driverId !== req.user.driverId) {
      res.status(403).json({ error: 'Forbidden: Evidence belongs to another driver' });
      return;
    }
    const object = await storage.get(metadata.storageKey);
    res.setHeader('Content-Type', metadata.contentType);
    res.setHeader('Content-Disposition', `inline; filename="${metadata.evidenceId}"`);
    res.setHeader('Cache-Control', 'private, no-store');
    object.body.on('error', next).pipe(res);
  } catch (error) { next(error); }
});

export async function evidenceStorageHealth() { return storage.health(); }
export default router;
