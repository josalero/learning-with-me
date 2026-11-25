import { ne, sql } from 'drizzle-orm';
import { getDb } from '../db/client.js';
import { projects } from '../db/schema.js';
import { fetchUsdCrcRate, type FxSnapshot } from './fxRate.js';

export async function syncAllProjectsFxRate(force = false): Promise<FxSnapshot & { projectsUpdated: number }> {
  const snap = await fetchUsdCrcRate(force);
  const db = getDb();
  const updated = await db.update(projects).set({
    payload: sql`jsonb_set(${projects.payload}, '{usdCrcRate}', to_jsonb(${snap.rate}::double precision))`,
    usdCrcRate: snap.rate,
    updatedAt: new Date(),
  }).where(ne(projects.usdCrcRate, snap.rate)).returning({ id: projects.id });
  return { ...snap, projectsUpdated: updated.length };
}
