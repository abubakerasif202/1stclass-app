import { createApp } from './app';
import { db } from './db';

const PORT = process.env.PORT || 8080;
async function main() {
  await db.initialize();
  const app = createApp();
  const server = app.listen(PORT, () => console.log(`Transport API listening on port ${PORT}`));
  const shutdown = () => server.close(async () => {
    await db.close();
    process.exit(0);
  });
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : 'Transport API startup failed');
  process.exit(1);
});
