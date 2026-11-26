import { eq, asc } from 'drizzle-orm';
import { buildAirbnbAssessment } from '../../domain/airbnb-assessment.js';
import type { FurnishingProject } from '../../shared/types.js';
import { getDb } from '../db/client.js';
import { projectReferenceAnalyses, projectReferences } from '../db/schema.js';

export async function loadAirbnbAssessment(db: Pick<ReturnType<typeof getDb>, 'select'>, project: FurnishingProject) {
  const [references, [analysis]] = await Promise.all([
    db.select({ id: projectReferences.id, title: projectReferences.title, assetName: projectReferences.assetName })
      .from(projectReferences).where(eq(projectReferences.projectId, project.id)).orderBy(asc(projectReferences.createdAt)),
    db.select().from(projectReferenceAnalyses).where(eq(projectReferenceAnalyses.projectId, project.id)),
  ]);
  return buildAirbnbAssessment(project, references, analysis?.result.observations);
}
