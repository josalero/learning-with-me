import type { FastifyInstance } from 'fastify';
import { and, asc, desc, eq, sql } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { BUDGET_TIERS, type BudgetQuote, type CatalogItem, type HitlGate, type ProjectSummary, type QuoteActivity, type ReviewActivity, type ReviewHistoryEvent, type ReviewHistoryPage, type ReviewResult, type ReviewSnapshot } from '../../shared/types.js';
import { budgetQuoteJobs, primaryBudgetTier, quotesForTier, selectedBudgetTiers } from '../../shared/budgetProfiles.js';
import { itemizeBundle } from '../../domain/itemize-bundle.js';
import { isTelevisionRequest, namesTelevision } from '../../domain/product-match.js';
import {
  buildFurnishingGraph,
  initialGraphState,
  type GraphStateType,
} from '../agents/furnishingGraph.js';
import { getDb } from '../db/client.js';
import { projectReferences, projects, regionalStores, workflowRuns } from '../db/schema.js';
import { probeStoreHomepage } from '../scrapers/store-probe.js';
import { assertPublicHttpUrl } from '../scrapers/ssrf-guard.js';
import {
  clearSessionCookie,
  createSession,
  requireUser,
  resolveSession,
  setSessionCookie,
  verifyPassword,
} from '../services/auth.js';
import { users } from '../db/schema.js';
import { loadAirbnbAssessment } from '../services/airbnbAssessment.js';
import { buildBudgetWorkbook } from '../services/excelExport.js';
import { fetchUsdCrcRate, getCachedFxSnapshot } from '../services/fxRate.js';
import { syncAllProjectsFxRate } from '../services/fxProjectSync.js';
import { loadOwnedProject, projectFromRow, saveProjectPayload } from '../services/projects.js';
import { ProgressError, respondWithProgress } from '../services/progress.js';
import { projectStats } from '../../shared/projectStats.js';
import { MAX_SPECIFICATIONS, MAX_SPECIFICATION_LENGTH } from '../../shared/itemSpecifications.js';
import { normalizeSearch } from '../../shared/search.js';
import {
  attachQuoteToProject,
  quoteActivity,
  recordSearch,
  UnselectedQuote,
  quoteItemOnDemand,
  quoteProjectItems,
  replaceTierQuotes,
} from '../services/quoteOnDemand.js';
import { z } from 'zod';

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6),
});

const addStoreSchema = z.object({
  id: z.string().min(2).max(64),
  name: z.string().min(2),
  region: z.enum(['guanacaste', 'limon', 'san-jose', 'alajuela']),
  websiteUrl: z.string().url(),
});

const approveGateSchema = z.object({
  gate: z.enum(['zone_layout', 'final_budget']),
  threadId: z.string().min(8),
  projectId: z.string().uuid(),
});

type ReviewData = Pick<ReviewSnapshot, 'approvedGates' | 'notes' | 'activity' | 'startedAt' | 'events' | 'failed'>;

function listWorkspaceStores(workspaceId: string) {
  return getDb().select().from(regionalStores)
    .where(eq(regionalStores.workspaceId, workspaceId)).orderBy(asc(regionalStores.name), asc(regionalStores.id));
}

function readReview(row: typeof workflowRuns.$inferSelect): ReviewSnapshot {
  const state = (row.state ?? {}) as Partial<ReviewData>;
  return {
    threadId: row.threadId,
    stage: row.stage as ReviewResult['stage'],
    pendingGate: (row.pendingGate ?? undefined) as HitlGate | undefined,
    notes: state.notes ?? [],
    activity: state.activity ?? [],
    approvedGates: state.approvedGates ?? [],
    startedAt: state.startedAt,
    updatedAt: row.updatedAt.toISOString(),
    events: state.events ?? [],
    failed: state.failed === true,
  };
}

async function runReview(
  db: Parameters<typeof loadAirbnbAssessment>[0],
  state: GraphStateType,
  threadId: string,
  activity: ReviewActivity[],
  events: ReviewHistoryEvent[],
  publish: (activity: ReviewActivity) => void,
): Promise<ReviewResult> {
  const steps = new Map(activity.map((event) => [event.step, event]));
  const graph = buildFurnishingGraph((event) => {
    steps.set(event.step, event);
    events.push({ ...event, type: 'step', at: new Date().toISOString() });
    publish(event);
  }, (project) => loadAirbnbAssessment(db, project));
  const result = await graph.invoke(state, { configurable: { thread_id: threadId } });
  return { threadId, stage: result.stage, pendingGate: result.gate, notes: result.notes, activity: [...steps.values()] };
}

const quoteItemSchema = z.object({
  productUrl: z.string().url().optional(),
  allowEstimate: z.boolean().optional(),
  tier: z.enum(BUDGET_TIERS).optional(),
});
const budgetTiersSchema = z.object({
  tiers: z.array(z.enum(BUDGET_TIERS)).min(1).max(BUDGET_TIERS.length)
    .refine((tiers) => new Set(tiers).size === tiers.length),
});

const manualItemSchema = z.object({
  name: z.string().trim().min(2).max(120),
  zoneId: z.string().max(80),
  qty: z.number().int().min(1).max(999),
  notes: z.string().trim().max(500).optional(),
  specifications: z.array(z.string().trim().min(1).max(MAX_SPECIFICATION_LENGTH)).min(1).max(MAX_SPECIFICATIONS).optional(),
  purchaseUnit: z.enum(['unit', 'set']).optional(),
  requestId: z.string().regex(/^[0-9a-f]{32}$/).optional(),
});
const splitItemSchema = z.object({
  components: z.array(manualItemSchema.pick({ name: true, qty: true, zoneId: true, specifications: true }).partial({ zoneId: true })).min(2).max(12),
});
const itemEditSchema = manualItemSchema.pick({ qty: true, zoneId: true, specifications: true }).extend({
  tier: z.enum(BUDGET_TIERS),
}).partial().strict().refine(({ qty, zoneId, specifications }) =>
  qty !== undefined || zoneId !== undefined || specifications !== undefined);

export async function registerApiRoutes(app: FastifyInstance): Promise<void> {
  app.post('/api/auth/login', async (request, reply) => {
    const parsed = loginSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid credentials payload' });
    }
    const db = getDb();
    const rows = await db.select().from(users).where(eq(users.email, parsed.data.email)).limit(1);
    const user = rows[0];
    if (!user || !verifyPassword(parsed.data.password, user.passwordHash)) {
      return reply.code(401).send({ error: 'Invalid email or password' });
    }
    const sid = await createSession(user.id);
    setSessionCookie(reply, sid);
    return { email: user.email, isDemo: user.isDemo };
  });

  app.post('/api/auth/logout', async (_request, reply) => {
    clearSessionCookie(reply);
    return { ok: true };
  });

  app.get('/api/auth/me', async (request, reply) => {
    const sid = request.cookies.natarundi_sid;
    const user = await resolveSession(sid);
    if (!user) return reply.code(401).send({ error: 'Unauthorized' });
    return user;
  });

  app.get('/api/projects', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const db = getDb();
    const [rows, covers] = await Promise.all([
      db.select().from(projects).where(eq(projects.workspaceId, user.workspaceId))
        .orderBy(desc(projects.updatedAt), asc(projects.name), asc(projects.id)),
      db.selectDistinctOn([projectReferences.projectId], { projectId: projectReferences.projectId, id: projectReferences.id })
        .from(projectReferences).innerJoin(projects, eq(projects.id, projectReferences.projectId))
        .where(eq(projects.workspaceId, user.workspaceId))
        .orderBy(asc(projectReferences.projectId), asc(projectReferences.createdAt), asc(projectReferences.id)),
    ]);
    const coverByProject = new Map(covers.map(({ projectId, id }) => [projectId, id]));
    return rows.map((row): ProjectSummary => {
      const project = projectFromRow(row);
      const { id, name, address, region, usdCrcRate, items } = project;
      const budgetTier = primaryBudgetTier(project);
      return { id, name, address, region, usdCrcRate, budgetTier, updatedAt: row.updatedAt.toISOString(), coverReferenceId: coverByProject.get(id), ...projectStats(items, budgetTier) };
    });
  });

  app.get('/api/projects/:id', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    const project = params.success ? await loadOwnedProject(getDb(), user.workspaceId, params.data.id) : null;
    return project ?? reply.code(404).send({ error: 'Project not found' });
  });

  app.patch('/api/projects/:id/budget-tiers', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ id: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    const parsed = budgetTiersSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'Selecciona al menos un nivel de presupuesto válido.' });
    const db = getDb();
    const updated = await db.transaction(async (tx) => {
      const project = await loadOwnedProject(tx, user.workspaceId, params.data.id, true);
      if (!project) return null;
      const budgetTiers = selectedBudgetTiers({ budgetTiers: parsed.data.tiers });
      const next = { ...project, budgetTiers };
      await saveProjectPayload(tx, next);
      return next;
    });
    return updated ?? reply.code(404).send({ error: 'No se encontró el proyecto.' });
  });

  app.get('/api/stores', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    return listWorkspaceStores(user.workspaceId);
  });

  app.post('/api/stores', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const parsed = addStoreSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'Invalid store' });
    try {
      assertPublicHttpUrl(parsed.data.websiteUrl);
    } catch {
      return reply.code(400).send({ error: 'URL not allowed' });
    }
    const probe = await probeStoreHomepage(parsed.data.websiteUrl);
    const db = getDb();
    await db.insert(regionalStores).values({
      id: parsed.data.id,
      workspaceId: user.workspaceId,
      name: parsed.data.name,
      region: parsed.data.region,
      websiteUrl: parsed.data.websiteUrl,
      probeKind: probe.kind,
      lastProbe: probe,
    });
    return { ...parsed.data, probe };
  });

  app.post('/api/stores/:id/probe', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const { id } = request.params as { id: string };
    const db = getDb();
    const rows = await db
      .select()
      .from(regionalStores)
      .where(eq(regionalStores.id, id))
      .limit(1);
    const store = rows[0];
    if (!store || store.workspaceId !== user.workspaceId) {
      return reply.code(404).send({ error: 'Store not found' });
    }
    const probe = await probeStoreHomepage(store.websiteUrl);
    await db
      .update(regionalStores)
      .set({ lastProbe: probe, probeKind: probe.kind, updatedAt: new Date() })
      .where(eq(regionalStores.id, id));
    return probe;
  });

  app.get('/api/projects/:projectId/review', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ projectId: z.string().uuid() }).safeParse(request.params);
    const db = getDb();
    if (!params.success || !await loadOwnedProject(db, user.workspaceId, params.data.projectId)) {
      return reply.code(404).send({ error: 'Project not found' });
    }
    const [latest] = await db.select().from(workflowRuns).where(eq(workflowRuns.projectId, params.data.projectId))
      .orderBy(desc(workflowRuns.updatedAt), desc(workflowRuns.id)).limit(1);
    return latest ? readReview(latest) : null;
  });

  app.get('/api/projects/:projectId/reviews', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ projectId: z.string().uuid() }).safeParse(request.params);
    const query = z.object({ before: z.string().uuid().optional() }).safeParse(request.query);
    if (!query.success) return reply.code(400).send({ error: 'Invalid history cursor' });
    const db = getDb();
    if (!params.success || !await loadOwnedProject(db, user.workspaceId, params.data.projectId)) {
      return reply.code(404).send({ error: 'Project not found' });
    }
    const { projectId } = params.data;
    const { before } = query.data;
    const pageSize = 10;
    const boundary = before ? sql`(${workflowRuns.updatedAt}, ${workflowRuns.id}) < (
      SELECT updated_at, id FROM workflow_runs WHERE project_id = ${projectId} AND id = ${before}
    )` : undefined;
    const rows = await db.select().from(workflowRuns).where(and(eq(workflowRuns.projectId, projectId), boundary))
      .orderBy(desc(workflowRuns.updatedAt), desc(workflowRuns.id)).limit(pageSize + 1);
    const visible = rows.slice(0, pageSize);
    return { runs: visible.map(readReview), nextCursor: rows.length > pageSize ? visible.at(-1)?.id : undefined } satisfies ReviewHistoryPage;
  });

  app.post('/api/workflow/start', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const parsed = z.object({ projectId: z.string().uuid().optional() }).safeParse(request.body ?? {});
    if (!parsed.success) return reply.code(400).send({ error: 'Invalid project' });
    const db = getDb();
    const [row] = await db.select().from(projects).where(parsed.data.projectId
      ? and(eq(projects.workspaceId, user.workspaceId), eq(projects.id, parsed.data.projectId))
      : eq(projects.workspaceId, user.workspaceId)).limit(1);
    if (!row) return reply.code(404).send({ error: 'Project not found' });
    const project = projectFromRow(row);

    return respondWithProgress(request, reply, async (publish: (activity: ReviewActivity) => void) => {
      const threadId = randomUUID();
      const startedAt = new Date().toISOString();
      const events: ReviewHistoryEvent[] = [];
      let result: ReviewResult;
      try {
        result = await runReview(db, initialGraphState(project), threadId, [], events, publish);
      } catch (error) {
        request.log.error({ err: error }, 'Review start failed');
        const activity = [...new Map(events.flatMap((event) => event.type === 'step' ? [[event.step, event] as const] : [])).values()];
        await db.insert(workflowRuns).values({
          projectId: project.id, threadId, stage: activity.at(-1)?.step ?? 'idle',
          state: { startedAt, events, failed: true, approvedGates: [], notes: [], activity } satisfies ReviewData,
        });
        throw new ProgressError(500, 'La revisión se interrumpió. Puedes volver a ejecutarla.');
      }
      const [saved] = await db.insert(workflowRuns).values({
        projectId: project.id, threadId, stage: result.stage, pendingGate: result.pendingGate,
        state: { startedAt, events, failed: false, approvedGates: [], notes: result.notes, activity: result.activity } satisfies ReviewData,
      }).returning();
      return readReview(saved!);
    });
  });

  app.post('/api/workflow/approve', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const parsed = approveGateSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'Invalid approval' });
    const { projectId, threadId, gate } = parsed.data;
    const db = getDb();
    const project = await loadOwnedProject(db, user.workspaceId, projectId);
    if (!project) return reply.code(404).send({ error: 'Project not found' });
    const match = and(eq(workflowRuns.threadId, threadId), eq(workflowRuns.projectId, projectId));
    const [known] = await db.select({ id: workflowRuns.id }).from(workflowRuns).where(match).limit(1);
    if (!known) return reply.code(404).send({ error: 'Unknown workflow thread' });

    return respondWithProgress(request, reply, async (publish: (activity: ReviewActivity) => void) => {
      const result = await db.transaction(async (tx) => {
        const [row] = await tx.select().from(workflowRuns).where(eq(workflowRuns.id, known.id)).for('update');
        if (!row) throw new ProgressError(404, 'La revisión ya no está disponible.');
        const previous = readReview(row);
        if (previous.pendingGate !== gate) return previous;
        const approvedGates = [...previous.approvedGates, gate];
        const approval: ReviewHistoryEvent = { type: 'approval', gate, at: new Date().toISOString() };
        const events = [...previous.events];
        const state = { project, stage: previous.stage, notes: previous.notes, gate, approvedGates };
        let review: ReviewResult;
        try {
          review = await runReview(tx, state, threadId, previous.activity, events, publish);
        } catch (error) {
          request.log.error({ err: error }, 'Review approval failed');
          await tx.update(workflowRuns).set({
            state: { ...row.state as ReviewData, events, failed: true }, updatedAt: new Date(),
          }).where(eq(workflowRuns.id, row.id));
          return null;
        }
        events.splice(previous.events.length, 0, approval);
        const [saved] = await tx.update(workflowRuns).set({
          stage: review.stage, pendingGate: review.pendingGate ?? null, updatedAt: new Date(),
          state: { startedAt: previous.startedAt, events, failed: false, approvedGates, notes: review.notes, activity: review.activity } satisfies ReviewData,
        }).where(eq(workflowRuns.id, row.id)).returning();
        return readReview(saved!);
      });
      if (!result) throw new ProgressError(500, 'La aprobación se interrumpió. Inténtalo de nuevo.');
      return result;
    });
  });

  app.post('/api/projects/:projectId/items', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ projectId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    const input = manualItemSchema.safeParse(request.body);
    if (!input.success) return reply.code(400).send({ error: 'Revisa el nombre, el espacio, la cantidad y las especificaciones del artículo.' });
    const { name, qty, zoneId, notes, specifications, purchaseUnit, requestId } = input.data;
    const id = `manual-${requestId ?? randomUUID()}`;
    const result = await getDb().transaction(async (tx) => {
      const project = await loadOwnedProject(tx, user.workspaceId, params.data.projectId, true);
      if (!project) return { status: 'not-found' } as const;
      const existing = project.items.find((item) => item.id === id);
      if (existing) return { status: 'existing', project, item: existing } as const;
      if (zoneId && !project.zones.some((zone) => zone.id === zoneId)) return { status: 'invalid-zone' } as const;
      const item: CatalogItem = {
        id, name, qty, zoneId, notes: notes || undefined, specifications, purchaseUnit, category: 'equipamiento', quotes: [],
        unquotedReason: 'Pendiente de buscar precios en las tiendas registradas.',
      };
      const updated = { ...project, items: [...project.items, item] };
      await saveProjectPayload(tx, updated);
      return { status: 'created', project: updated, item } as const;
    });
    if (result.status === 'not-found') return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    if (result.status === 'invalid-zone') return reply.code(400).send({ error: 'La zona seleccionada no pertenece a este proyecto.' });
    return reply.code(result.status === 'created' ? 201 : 200).send({ project: result.project, item: result.item });
  });

  app.patch('/api/projects/:projectId/items/:itemId', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ projectId: z.string().uuid(), itemId: z.string().min(1).max(100) }).safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el artículo.' });
    const input = itemEditSchema.safeParse(request.body);
    if (!input.success) return reply.code(400).send({ error: 'Revisa el espacio, las especificaciones o la cantidad. La cantidad debe ser un entero entre 1 y 999.' });
    const { projectId, itemId } = params.data;
    const { zoneId, qty, tier, specifications } = input.data;
    const updated = await getDb().transaction(async (tx) => {
      const project = await loadOwnedProject(tx, user.workspaceId, projectId, true);
      const index = project?.items.findIndex(({ id }) => id === itemId) ?? -1;
      if (!project || index < 0) return null;
      if (zoneId && !project.zones.some(({ id }) => id === zoneId)) return { invalidZone: true } as const;
      const item = project.items[index]!;
      if (item.id === 'i-laundry-tower' && qty !== undefined && qty !== 1) return { fixedQuantity: true } as const;
      const next: CatalogItem = { ...item, zoneId: zoneId ?? item.zoneId };
      if (qty !== undefined) {
        if (tier) next.quantityByTier = { ...item.quantityByTier, [tier]: qty };
        else next.qty = qty;
      }
      if (specifications) {
        if (tier) next.specificationsByTier = { ...item.specificationsByTier, [tier]: specifications };
        else next.specifications = specifications;
      }
      if (next.qty !== item.qty || next.zoneId !== item.zoneId || (tier && qty !== undefined) || specifications) {
        const items = project.items.slice();
        items[index] = next;
        await saveProjectPayload(tx, { ...project, items });
      }
      return { qty: next.qty, zoneId: next.zoneId, quantityByTier: next.quantityByTier,
        specifications: next.specifications, specificationsByTier: next.specificationsByTier };
    });
    if (updated && 'invalidZone' in updated) return reply.code(400).send({ error: 'El ambiente seleccionado no pertenece a este proyecto.' });
    if (updated && 'fixedQuantity' in updated) return reply.code(400).send({ error: 'La lavandería tiene espacio para un solo centro de lavado.' });
    return updated ?? reply.code(404).send({ error: 'No se encontró el artículo.' });
  });

  app.post('/api/projects/:projectId/items/:itemId/split', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const params = z.object({ projectId: z.string().uuid(), itemId: z.string().min(1).max(100) }).safeParse(request.params);
    const parsed = splitItemSchema.safeParse(request.body);
    if (!params.success || !parsed.success) return reply.code(400).send({ error: 'Agrega entre 2 y 12 artículos con nombres distintos y cantidades válidas.' });
    const { projectId, itemId } = params.data;
    const db = getDb();
    const result = await db.transaction(async (tx) => {
      const project = await loadOwnedProject(tx, user.workspaceId, projectId, true);
      if (!project) return null;
      const bundle = project.items.find(({ id }) => id === itemId);
      if (!bundle) return project.itemizedBundles?.some(({ id }) => id === itemId) ? { project } : null;
      if (bundle.id === 'i-laundry-tower') return { error: 'Cotizá el centro de lavado como un equipo integrado.' };
      if (isTelevisionRequest(bundle) && parsed.data.components.some(({ name }) => !namesTelevision(name))) {
        return { error: 'Indica el producto de cada partida, por ejemplo «Televisor para dormitorio».' };
      }
      const zones = new Set(project.zones.map(({ id }) => id));
      if (parsed.data.components.some(({ zoneId }) => zoneId && !zones.has(zoneId))) {
        return { error: 'Elige ambientes que pertenezcan a este proyecto.' };
      }
      const components = parsed.data.components.map((component) => ({ ...component, zoneId: component.zoneId ?? bundle.zoneId, id: randomUUID() }));
      if (new Set(components.map(({ name, zoneId }) => JSON.stringify([normalizeSearch(name), zoneId]))).size !== components.length) {
        return { error: 'Los artículos de un mismo ambiente deben tener nombres distintos.' };
      }
      const next = itemizeBundle(project, bundle, components);
      await saveProjectPayload(tx, next);
      return { project: next };
    });
    if (!result) return reply.code(404).send({ error: 'No se encontró el artículo.' });
    if (result.error) return reply.code(400).send({ error: result.error });
    return result.project;
  });

  app.post('/api/projects/:projectId/items/:itemId/quote', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const { projectId, itemId } = request.params as { projectId: string; itemId: string };
    const parsed = quoteItemSchema.safeParse(request.body ?? {});
    if (!parsed.success) return reply.code(400).send({ error: 'Invalid quote request' });
    if (parsed.data.productUrl) {
      try {
        assertPublicHttpUrl(parsed.data.productUrl);
      } catch {
        return reply.code(400).send({ error: 'URL not allowed' });
      }
    }
    const db = getDb();
    const project = await loadOwnedProject(db, user.workspaceId, projectId);
    if (!project) return reply.code(404).send({ error: 'Project not found' });
    const item = project.items.find((i) => i.id === itemId);
    if (!item) return reply.code(404).send({ error: 'Item not found' });
    const tier = parsed.data.tier ?? primaryBudgetTier(project);
    return respondWithProgress(request, reply, async (publish: (activity: QuoteActivity) => void) => {
      let result;
      try {
        result = await quoteItemOnDemand(item, {
          productUrl: parsed.data.productUrl,
          region: project.region,
          usdCrcRate: project.usdCrcRate,
          tier,
          stores: parsed.data.productUrl ? undefined : await listWorkspaceStores(user.workspaceId),
          onActivity: publish,
        });
      } catch (error) {
        const detail = error instanceof Error ? error.message : 'No se pudo cotizar el artículo.';
        if (error instanceof UnselectedQuote) {
          const search = error.search;
          await db.transaction(async (tx) => {
            const latest = await loadOwnedProject(tx, user.workspaceId, project.id, true);
            const current = latest?.items.find((i) => i.id === itemId);
            if (!latest || !current || quotesForTier(current, tier)[0]?.id !== quotesForTier(item, tier)[0]?.id
              || (current.lastSearch?.[tier]?.checkedAt ?? '') > search.checkedAt) {
              throw new ProgressError(409, 'Otra ejecución actualizó este artículo. Recarga el inventario para ver el resultado.');
            }
            await saveProjectPayload(tx, { ...latest, items: latest.items.map((i) => i.id === itemId ? recordSearch(i, tier, search) : i) });
          });
          publish({ ...quoteActivity(item, 'failed', detail, tier), search });
        } else publish(quoteActivity(item, 'failed', detail, tier));
        throw new ProgressError(422, detail);
      }
      const { quote, alternatives, mode } = result;
      publish(quoteActivity(item, 'saving', 'Guardando la cotización en este proyecto.', tier));
      const updated = await db.transaction(async (tx) => {
        const latest = await loadOwnedProject(tx, user.workspaceId, projectId, true);
        const current = latest?.items.find(({ id }) => id === itemId);
        if (!latest || !current) return null;
        if (quotesForTier(current, tier)[0]?.id !== quotesForTier(item, tier)[0]?.id) throw new ProgressError(409, 'Otra ejecución ya actualizó el precio de este artículo.');
        const next = attachQuoteToProject(latest, itemId, quote, alternatives);
        await saveProjectPayload(tx, next);
        return next;
      });
      if (!updated) throw new ProgressError(404, 'No se encontró el artículo.');
      publish(quoteActivity(item, 'completed', 'Cotización guardada.', tier));
      return { mode, itemId, quote, alternatives, project: updated };
    });
  });

  app.post('/api/projects/:projectId/quote-pending', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const { projectId } = request.params as { projectId: string };
    const filters = z.object({ scope: z.enum(['pending', 'all']).default('pending'), zoneId: z.string().min(1).max(80).nullable().optional(),
      tier: z.enum(BUDGET_TIERS).optional() }).strict().safeParse(request.body ?? {});
    if (!filters.success) return reply.code(400).send({ error: 'Elige qué artículos cotizar.' });
    const { scope, zoneId } = filters.data;
    const db = getDb();
    const project = await loadOwnedProject(db, user.workspaceId, projectId);
    if (!project) return reply.code(404).send({ error: 'Project not found' });
    const tier = filters.data.tier ?? primaryBudgetTier(project);
    const zoneIds = new Set(project.zones.map(({ id }) => id));
    if (zoneId && !zoneIds.has(zoneId)) return reply.code(404).send({ error: 'No se encontró el ambiente.' });
    const scopeProject = zoneId === undefined ? project : {
      ...project,
      items: project.items.filter((item) => zoneId === null ? !zoneIds.has(item.zoneId) : item.zoneId === zoneId),
    };
    return respondWithProgress(request, reply, async (publish: (activity: QuoteActivity) => void) => {
      const result = await quoteProjectItems(scopeProject, publish, await listWorkspaceStores(user.workspaceId), scope, tier);
      const before = new Map(scopeProject.items.map((item) => [item.id, item]));
      const priced = new Map(result.project.items.map((item) => [item.id, item]));
      const changedDuringQuote = (item: CatalogItem, tier: BudgetQuote['tier']) =>
        quotesForTier(item, tier)[0]?.id !== quotesForTier(before.get(item.id)!, tier)[0]?.id;
      const updated = await db.transaction(async (tx) => {
        const latest = await loadOwnedProject(tx, user.workspaceId, projectId, true);
        if (!latest) return null;
        const items = new Map(latest.items.map((item) => [item.id, item]));
        const saved: BudgetQuote[] = [];
        for (const { itemId, tier } of result.saved) {
          const item = items.get(itemId);
          if (!item || changedDuringQuote(item, tier)) continue;
          items.set(itemId, replaceTierQuotes(item, tier, quotesForTier(priced.get(itemId)!, tier)));
          saved.push({ itemId, tier });
        }
        const failed = result.failed.filter(({ itemId, tier, search }) => {
          const item = items.get(itemId);
          if (!item || changedDuringQuote(item, tier) || (search && (item.lastSearch?.[tier]?.checkedAt ?? '') > search.checkedAt)) return false;
          if (search) items.set(itemId, recordSearch(item, tier, search));
          return true;
        });
        const next = { ...latest, items: [...items.values()] };
        if (saved.length || failed.some((entry) => entry.search)) await saveProjectPayload(tx, next);
        return { project: next, quoted: [...new Set(saved.map(({ itemId }) => itemId))], saved, failed };
      });
      if (!updated) throw new ProgressError(404, 'No se encontró el proyecto.');
      const currentItems = new Map(updated.project.items.map((item) => [item.id, item]));
      for (const { item, tier: jobTier } of budgetQuoteJobs(scopeProject, scope, tier)) {
        const current = currentItems.get(item.id);
        if (!current || !changedDuringQuote(current, jobTier) || !quotesForTier(current, jobTier).length) continue;
        const detail = updated.saved.some((saved) => saved.itemId === item.id && saved.tier === jobTier) ? 'Cotización guardada.' : 'La cotización de este nivel se actualizó en otra ejecución.';
        publish(quoteActivity(item, 'completed', detail, jobTier));
      }
      return updated;
    });
  });

  app.get('/api/projects/:id/export.xlsx', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const filter = z.object({ tier: z.enum(BUDGET_TIERS).optional() }).safeParse(request.query);
    if (!filter.success) return reply.code(400).send({ error: 'Elige un nivel de presupuesto válido.' });
    const { id } = request.params as { id: string };
    const db = getDb();
    const rows = await db.select().from(projects).where(eq(projects.id, id)).limit(1);
    const row = rows[0];
    if (!row || row.workspaceId !== user.workspaceId) {
      return reply.code(404).send({ error: 'Project not found' });
    }
    const project = projectFromRow(row);
    const slug = normalizeSearch(project.name).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 64) || 'proyecto';
    const budgetTiers = filter.data.tier ? [filter.data.tier] : [...BUDGET_TIERS];
    const buf = await buildBudgetWorkbook({ ...project, budgetTiers });
    reply
      .header('content-type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
      .header('content-disposition', `attachment; filename="${slug}-presupuesto.xlsx"`)
      .send(buf);
  });

  app.get('/api/fx', async (_request, reply) => {
    const snap = getCachedFxSnapshot() ?? (await fetchUsdCrcRate());
    return reply.send(snap);
  });

  app.post('/api/fx/refresh', async (request, reply) => {
    const user = await requireUser(request, reply);
    if (!user) return;
    const result = await syncAllProjectsFxRate(true);
    return reply.send(result);
  });

  app.get('/api/health', async () => ({
    ok: true,
    service: 'natarundi-apartamento',
    fx: getCachedFxSnapshot(),
  }));
}
