import { Readable } from 'node:stream';
import { S3EvidenceStorage } from '../storage/S3EvidenceStorage';

const enabled = process.env.TEST_S3_ENDPOINT && process.env.OBJECT_STORAGE_ACCESS_KEY_ID &&
  process.env.OBJECT_STORAGE_SECRET_ACCESS_KEY;
const describeS3 = enabled ? describe : describe.skip;

async function readAll(stream: Readable): Promise<Buffer> {
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  return Buffer.concat(chunks);
}

describeS3('S3-compatible evidence storage integration', () => {
  beforeAll(() => {
    process.env.OBJECT_STORAGE_ENDPOINT = process.env.TEST_S3_ENDPOINT;
    process.env.OBJECT_STORAGE_REGION = 'us-east-1';
  });

  test('JPEG and PNG objects survive adapter restart', async () => {
    const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);
    const png = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
    const first = new S3EvidenceStorage('tms-evidence', 'integration/');
    await first.put('synthetic.jpg', 'image/jpeg', jpeg);
    await first.put('signature.png', 'image/png', png);
    expect(await first.health()).toBe(true);

    const restarted = new S3EvidenceStorage('tms-evidence', 'integration/');
    const storedJpeg = await restarted.get('synthetic.jpg');
    const storedPng = await restarted.get('signature.png');
    expect(await readAll(storedJpeg.body)).toEqual(jpeg);
    expect(await readAll(storedPng.body)).toEqual(png);
  });
});
