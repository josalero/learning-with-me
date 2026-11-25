import { eq } from 'drizzle-orm';
import type { FurnishingProject } from '../../shared/types.js';
import { getDb } from '../db/client.js';
import { projects } from '../db/schema.js';
import { fetchUsdCrcRate, type FxSnapshot } from './fxRate.js';

function projectFromRow(payload: unknown, usdCrcRate: number): FurnishingProject {
  const p = payload as FurnishingProject;
  return { ...p, usdCrcRate };
}

export async function syncAllProjectsFxRate(force = false): Promise<FxSnapshot & { projectsUpdated: number }> {
  const snap = await fetchUsdCrcRate(force);
  const db = getDb();
  const rows = await db.select().from(projects);
  let projectsUpdated = 0;
  for (const row of rows) {
    const project = projectFromRow(row.payload, row.usdCrcRate);
    if (project.usdCrcRate === snap.rate && row.usdCrcRate === snap.rate) continue;
    const next = { ...project, usdCrcRate: snap.rate };
    await db
      .update(projects)
      .set({ payload: next, usdCrcRate: snap.rate, updatedAt: new Date() })
      .where(eq(projects.id, row.id));
    projectsUpdated += 1;
  }
  return { ...snap, projectsUpdated };
}