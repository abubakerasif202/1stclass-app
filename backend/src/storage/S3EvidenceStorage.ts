import { createHash, createHmac } from 'node:crypto';
import { Readable } from 'node:stream';
import { EvidenceStorage, StoredEvidenceObject } from './EvidenceStorage';

function hmac(key: Buffer | string, value: string) { return createHmac('sha256', key).update(value).digest(); }
function sha256(value: Buffer | string) { return createHash('sha256').update(value).digest('hex'); }

export class S3EvidenceStorage implements EvidenceStorage {
  readonly kind = 's3' as const;
  private readonly region = process.env.OBJECT_STORAGE_REGION || 'ap-southeast-2';
  private readonly endpoint = process.env.OBJECT_STORAGE_ENDPOINT || `https://s3.${this.region}.amazonaws.com`;

  constructor(private readonly bucket: string, private readonly prefix = 'evidence/') {}

  private async request(method: string, key: string, body: Buffer = Buffer.alloc(0) as Buffer, contentType = ''): Promise<Response> {
    const accessKey = process.env.OBJECT_STORAGE_ACCESS_KEY_ID;
    const secretKey = process.env.OBJECT_STORAGE_SECRET_ACCESS_KEY;
    if (!accessKey || !secretKey) throw new Error('Object storage credentials are not configured');
    const now = new Date();
    const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, '');
    const date = amzDate.slice(0, 8);
    const encodedKey = `${this.prefix}${key}`.split('/').map(encodeURIComponent).join('/');
    const endpoint = new URL(this.endpoint);
    const path = `/${this.bucket}/${encodedKey}`;
    const payloadHash = sha256(body);
    const canonicalHeaders = `host:${endpoint.host}\nx-amz-content-sha256:${payloadHash}\nx-amz-date:${amzDate}\n`;
    const signedHeaders = 'host;x-amz-content-sha256;x-amz-date';
    const canonicalRequest = `${method}\n${path}\n\n${canonicalHeaders}\n${signedHeaders}\n${payloadHash}`;
    const scope = `${date}/${this.region}/s3/aws4_request`;
    const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${sha256(canonicalRequest)}`;
    const signingKey = hmac(hmac(hmac(hmac(`AWS4${secretKey}`, date), this.region), 's3'), 'aws4_request');
    const signature = createHmac('sha256', signingKey).update(stringToSign).digest('hex');
    const response = await fetch(new URL(path, endpoint), {
      method,
      headers: {
        Authorization: `AWS4-HMAC-SHA256 Credential=${accessKey}/${scope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
        'x-amz-content-sha256': payloadHash,
        'x-amz-date': amzDate,
        ...(contentType ? { 'Content-Type': contentType } : {})
      },
      body: method === 'GET' || method === 'HEAD' ? undefined : body
    });
    if (!response.ok) throw new Error(`Object storage ${method} failed with ${response.status}`);
    return response;
  }

  async put(key: string, contentType: StoredEvidenceObject['contentType'], body: Buffer) {
    await this.request('PUT', key, body, contentType);
    return { key, contentType, sizeBytes: body.length };
  }

  async get(key: string) {
    const response = await this.request('GET', key);
    if (!response.body) throw new Error('Evidence body missing');
    return {
      contentType: response.headers.get('content-type') || 'application/octet-stream',
      sizeBytes: Number(response.headers.get('content-length')) || undefined,
      body: Readable.fromWeb(response.body as any)
    };
  }

  async health() {
    // A zero-byte probe verifies authenticated read/write access without relying
    // on a pre-created object. The stable key is private and contains no data.
    const probeKey = '.readiness-probe';
    await this.request('PUT', probeKey);
    await this.request('HEAD', probeKey);
    return true;
  }
}
