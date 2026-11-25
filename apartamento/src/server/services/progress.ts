import type { FastifyReply, FastifyRequest } from 'fastify';
import { PassThrough } from 'node:stream';
import type { ProgressEvent } from '../../shared/types.js';

export class ProgressError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function respondWithProgress<Activity, Result>(
  request: FastifyRequest,
  reply: FastifyReply,
  run: (publish: (activity: Activity) => void) => Promise<Result>,
) {
  if (!request.headers.accept?.includes('application/x-ndjson')) {
    try {
      return await run(() => {});
    } catch (error) {
      if (error instanceof ProgressError) return reply.code(error.status).send({ error: error.message });
      throw error;
    }
  }

  const stream = new PassThrough();
  const write = (event: ProgressEvent<Activity, Result>) => {
    if (!stream.destroyed) stream.write(`${JSON.stringify(event)}\n`);
  };
  reply.type('application/x-ndjson; charset=utf-8')
    .header('cache-control', 'no-cache, no-transform')
    .header('x-accel-buffering', 'no')
    .send(stream);
  // An open connection lets proxies distinguish a long provider call from a dead request.
  const heartbeat = setInterval(() => { if (!stream.destroyed) stream.write('\n'); }, 12_000);
  stream.once('close', () => clearInterval(heartbeat));
  void run((activity) => write({ type: 'activity', activity }))
    .then((result) => write({ type: 'result', result }))
    .catch((error: unknown) => {
      if (error instanceof ProgressError) write({ type: 'error', status: error.status, message: error.message });
      else {
        request.log.error({ err: error }, 'Operation failed');
        write({ type: 'error' });
      }
    })
    .finally(() => { clearInterval(heartbeat); stream.end(); });
  return reply;
}
