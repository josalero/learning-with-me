import Fastify from 'fastify';
import cookie from '@fastify/cookie';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { registerReferenceRoutes } from './routes/references.js';
import { registerApiRoutes } from './routes/api.js';
import { syncAllProjectsFxRate } from './services/fxProjectSync.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function resolveProjectRoot(): string {
  const candidates = [
    path.join(__dirname, '../../..'),
    path.join(__dirname, '../..'),
    process.cwd(),
  ];
  for (const dir of candidates) {
    if (existsSync(path.join(dir, 'package.json'))) return dir;
  }
  return process.cwd();
}

async function main(): Promise<void> {
  const app = Fastify({ logger: true });
  await app.register(cookie, {
    secret: process.env.SESSION_SECRET ?? 'dev-only-change-me',
  });
  await app.register(multipart);
  const root = resolveProjectRoot();
  await registerApiRoutes(app);
  if (process.env.DATABASE_URL) {
    try {
      const fx = await syncAllProjectsFxRate();
      app.log.info(
        { rate: fx.rate, provider: fx.provider, fallback: fx.fallback, updated: fx.projectsUpdated },
        'FX rate synced',
      );
    } catch (err) {
      app.log.warn({ err }, 'FX sync skipped at startup');
    }
  }
  registerReferenceRoutes(app, path.join(root, 'public/assets'));
  const clientDist = path.join(root, 'dist/client');
  const publicDir = path.join(root, 'public');
  if (existsSync(publicDir)) {
    await app.register(fastifyStatic, { root: publicDir, prefix: '/public/', decorateReply: false });
  }
  if (existsSync(clientDist)) {
    await app.register(fastifyStatic, {
      root: clientDist,
      prefix: '/',
      decorateReply: true,
    });
    app.setNotFoundHandler((request, reply) => {
      if (request.url.startsWith('/api')) {
        return reply.code(404).send({ error: 'Not found' });
      }
      return reply.sendFile('index.html');
    });
  } else {
    app.log.warn('Client dist not found — API only until npm run build');
  }

  const port = Number(process.env.PORT ?? process.env.LOCAL_APP_PORT ?? 8080);
  const host = process.env.HOST ?? '0.0.0.0';
  await app.listen({ port, host });
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});