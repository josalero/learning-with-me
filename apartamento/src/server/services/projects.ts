import { and, eq } from 'drizzle-orm';
import { repairLegacyQuoteLinks } from '../../domain/demo-quotes.js';
import type { FurnishingProject } from '../../shared/types.js';
import { getDb } from '../db/client.js';
import { projects } from '../db/schema.js';

type Db = ReturnType<typeof getDb>;
type ProjectConnection = Pick<Db, 'select' | 'update'>;

export function projectFromRow(row: typeof projects.$inferSelect): FurnishingProject {
  const project = row.payload as FurnishingProject;
  return {
    ...project,
    items: repairLegacyQuoteLinks(project.items),
    id: row.id,
    workspaceId: row.workspaceId,
    name: row.name,
    address: row.address,
    region: row.region as FurnishingProject['region'],
    usdCrcRate: row.usdCrcRate,
  };
}

export async function loadOwnedProject(db: ProjectConnection, workspaceId: string, projectId: string, lock = false) {
  const query = db.select().from(projects)
    .where(and(eq(projects.id, projectId), eq(projects.workspaceId, workspaceId))).limit(1);
  const [row] = await (lock ? query.for('update') : query);
  return row ? projectFromRow(row) : null;
}

export async function saveProjectPayload(db: ProjectConnection, project: FurnishingProject) {
  await db.update(projects).set({ payload: project, usdCrcRate: project.usdCrcRate, updatedAt: new Date() })
    .where(and(eq(projects.id, project.id), eq(projects.workspaceId, project.workspaceId)));
}
