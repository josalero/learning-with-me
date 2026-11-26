import { randomUUID } from 'node:crypto';
import { and, eq, sql } from 'drizzle-orm';
import { buildNatarundiProject } from '../../domain/natarundi-catalog.js';
import { NATARUNDI_REFERENCES } from '../../domain/natarundi-references.js';
import { applyNatarundiOpeningPrices } from '../../domain/natarundi-opening.js';
import { itemizeCatalogBundles } from '../../domain/itemize-bundle.js';
import { hashPassword } from '../services/auth.js';
import { fetchUsdCrcRate } from '../services/fxRate.js';
import { SEED_STORES } from '../scrapers/stores-seed.js';
import { getDb, getPool } from './client.js';
import { projectReferences, projects, regionalStores, users, workspaces } from './schema.js';

const DEMO_EMAIL = process.env.APP_DEMO_EMAIL ?? 'demo@natarundi.local';

async function main(): Promise<void> {
  if (process.env.APP_SEED_DEMO === '0') {
    console.log('APP_SEED_DEMO=0 — seed skipped');
    return;
  }
  const demoPassword = process.env.APP_DEMO_PASSWORD;
  if (!demoPassword) {
    throw new Error('APP_DEMO_PASSWORD is required for demo seed');
  }
  const db = getDb();
  const wsName = 'Natarundi Workspace';
  const passwordHash = hashPassword(demoPassword);
  const seeded = await db.transaction(async (tx) => {
    await tx.execute(sql`SELECT pg_advisory_xact_lock(30912, 2)`);
    const [existingUser] = await tx.select().from(users).where(eq(users.email, DEMO_EMAIL)).limit(1);
    if (existingUser && !existingUser.isDemo) return false;
    const [existingWs] = await tx.select().from(workspaces).where(eq(workspaces.name, wsName)).limit(1);
    const workspaceId = existingUser?.workspaceId ?? existingWs?.id ?? randomUUID();
    const fx = await fetchUsdCrcRate();
    const project = applyNatarundiOpeningPrices(itemizeCatalogBundles(buildNatarundiProject(workspaceId, randomUUID(), fx.rate)));
    if (!existingWs && !existingUser) {
      await tx.insert(workspaces).values({ id: workspaceId, name: wsName });
    }
    await tx.insert(users).values({ email: DEMO_EMAIL, passwordHash, workspaceId, isDemo: true })
      .onConflictDoUpdate({ target: users.email, set: { passwordHash }, setWhere: eq(users.isDemo, true) });

    const [existingProject] = await tx.select({ id: projects.id }).from(projects)
      .where(and(eq(projects.workspaceId, workspaceId), eq(projects.name, project.name), eq(projects.address, project.address))).limit(1);
    if (existingProject) return { workspaceId };
    await tx.insert(projects).values({
      id: project.id, workspaceId, name: project.name, address: project.address,
      region: project.region, payload: project, usdCrcRate: project.usdCrcRate,
    });
    const createdAt = Date.now();
    await tx.insert(projectReferences).values(NATARUNDI_REFERENCES.map((reference, index) => ({
      ...reference, projectId: project.id, createdAt: new Date(createdAt + index),
    })));
    return { workspaceId };
  });
  if (!seeded) {
    console.log('Demo seed skipped because the configured email belongs to a regular account');
    await getPool().end();
    return;
  }

  for (const store of SEED_STORES) {
    await db
      .insert(regionalStores)
      .values({
        id: store.id,
        workspaceId: seeded.workspaceId,
        name: store.name,
        region: store.region,
        websiteUrl: store.websiteUrl,
        probeKind: store.probeKind,
      })
      .onConflictDoNothing();
  }

  console.log(`Seed OK — demo user ${DEMO_EMAIL}`);
  await getPool().end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
