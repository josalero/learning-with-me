import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { eq } from 'drizzle-orm';
import { getDb } from '../db/client.js';
import { sessions, users } from '../db/schema.js';

const SESSION_COOKIE = 'natarundi_sid';
const SESSION_DAYS = 14;

function hashPassword(password: string): string {
  const salt = randomBytes(16).toString('hex');
  const derived = scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${derived}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const [salt, hash] = stored.split(':');
  if (!salt || !hash) return false;
  const derived = scryptSync(password, salt, 64).toString('hex');
  try {
    return timingSafeEqual(Buffer.from(hash, 'hex'), Buffer.from(derived, 'hex'));
  } catch {
    return false;
  }
}

export { hashPassword };

export type SessionUser = {
  id: string;
  email: string;
  workspaceId: string;
  isDemo: boolean;
};

export async function createSession(userId: string): Promise<string> {
  const sessionId = randomBytes(24).toString('hex');
  const expires = new Date(Date.now() + SESSION_DAYS * 86_400_000);
  const db = getDb();
  await db.insert(sessions).values({ id: sessionId, userId, expiresAt: expires });
  return sessionId;
}

export async function resolveSession(sessionId: string | undefined): Promise<SessionUser | null> {
  if (!sessionId) return null;
  const db = getDb();
  const rows = await db
    .select({
      id: users.id,
      email: users.email,
      workspaceId: users.workspaceId,
      isDemo: users.isDemo,
      expiresAt: sessions.expiresAt,
    })
    .from(sessions)
    .innerJoin(users, eq(sessions.userId, users.id))
    .where(eq(sessions.id, sessionId))
    .limit(1);
  const row = rows[0];
  if (!row) return null;
  if (row.expiresAt.getTime() < Date.now()) return null;
  return { id: row.id, email: row.email, workspaceId: row.workspaceId, isDemo: row.isDemo };
}

export function setSessionCookie(reply: FastifyReply, sessionId: string): void {
  reply.setCookie(SESSION_COOKIE, sessionId, {
    path: '/',
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    maxAge: SESSION_DAYS * 86_400,
  });
}

export function clearSessionCookie(reply: FastifyReply): void {
  reply.clearCookie(SESSION_COOKIE, { path: '/' });
}

export async function requireUser(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<SessionUser | null> {
  const sid = request.cookies[SESSION_COOKIE];
  const user = await resolveSession(sid);
  if (!user) {
    reply.code(401).send({ error: 'Unauthorized' });
    return null;
  }
  return user;
}