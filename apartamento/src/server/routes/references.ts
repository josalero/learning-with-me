import path from 'node:path';
import { readFile } from 'node:fs/promises';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { and, asc, eq, inArray } from 'drizzle-orm';
import { z } from 'zod';
import { NATARUNDI_REFERENCES } from '../../domain/natarundi-references.js';
import { REFERENCE_MAX_ANALYSIS_BYTES, REFERENCE_MAX_ANALYSIS_IMAGES, REFERENCE_MAX_BYTES, type CatalogItem, type ProjectReference, type ReferenceAnalysis } from '../../shared/types.js';
import { isAiOffline } from '../agents/modelRouter.js';
import { getDb } from '../db/client.js';
import { projectReferenceAnalyses, projectReferences } from '../db/schema.js';
import { requireUser } from '../services/auth.js';
import { loadOwnedProject, saveProjectPayload } from '../services/projects.js';
import { identifyQuoteSuggestions, suggestedItemId, suggestionAlreadyInInventory } from '../services/referenceVision.js';

const projectParams = z.object({ projectId: z.string().uuid() });
const referenceParams = projectParams.extend({ referenceId: z.string().uuid() });
const suggestionParams = projectParams.extend({ suggestionId: z.string().uuid() });
const analysisInput = z.object({ referenceIds: z.array(z.string().uuid()).min(1).max(REFERENCE_MAX_ANALYSIS_IMAGES) });
const pngHeader = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const assetNames = new Set(NATARUNDI_REFERENCES.map(({ assetName }) => assetName));
const selection = { id: projectReferences.id, title: projectReferences.title, mediaType: projectReferences.mediaType, createdAt: projectReferences.createdAt };

type ReferenceRow = typeof projectReferences.$inferSelect;

function referenceMetadata(row: Pick<ReferenceRow, keyof ProjectReference>): ProjectReference {
  return { id: row.id, title: row.title, mediaType: row.mediaType, createdAt: row.createdAt.toISOString() };
}

function imageType(content: Buffer): ProjectReference['mediaType'] | null {
  if (content.length >= 4 && content[0] === 255 && content[1] === 216 && content[2] === 255
    && content.at(-2) === 255 && content.at(-1) === 217) return 'image/jpeg';
  if (content.length >= 45 && content.subarray(0, 8).equals(pngHeader)
    && content.toString('ascii', 12, 16) === 'IHDR' && content.toString('ascii', content.length - 8, content.length - 4) === 'IEND') return 'image/png';
  if (content.length >= 20 && content.toString('ascii', 0, 4) === 'RIFF'
    && content.readUInt32LE(4) + 8 === content.length && content.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  return null;
}

async function authorize(request: FastifyRequest, reply: FastifyReply, projectId: string) {
  const user = await requireUser(request, reply);
  if (!user) return null;
  const db = getDb();
  const project = await loadOwnedProject(db, user.workspaceId, projectId);
  if (!project) {
    reply.code(404).send({ error: 'No se encontró el proyecto.' });
    return null;
  }
  return { db, project };
}

export function registerReferenceRoutes(app: FastifyInstance, assetDir: string) {
  async function readReference(row: ReferenceRow) {
    if (row.content) return row.content;
    if (row.assetName && assetNames.has(row.assetName)) return readFile(path.join(assetDir, row.assetName));
    throw new Error('Missing reference asset');
  }

  app.get('/api/projects/:projectId/references', async (request, reply) => {
    const params = projectParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    const { db, project } = scope;
    const [rows, [analysis]] = await Promise.all([
      db.select(selection).from(projectReferences).where(eq(projectReferences.projectId, project.id))
        .orderBy(asc(projectReferences.createdAt), asc(projectReferences.id)),
      db.select().from(projectReferenceAnalyses).where(eq(projectReferenceAnalyses.projectId, project.id)),
    ]);
    return { references: rows.map(referenceMetadata), analysis: analysis?.result ?? null, analysisAvailable: !isAiOffline() };
  });

  app.post('/api/projects/:projectId/references', async (request, reply) => {
    const params = projectParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    const { db, project } = scope;
    if (!request.isMultipart()) return reply.code(400).send({ error: 'Adjunta una imagen para subirla.' });
    try {
      const file = await request.file({ limits: { files: 1, fields: 0, fileSize: REFERENCE_MAX_BYTES + 1 } });
      if (!file) return reply.code(400).send({ error: 'Selecciona una imagen.' });
      const content = await file.toBuffer();
      if (file.file.truncated || content.length > REFERENCE_MAX_BYTES) return reply.code(413).send({ error: 'La imagen supera el límite de 5 MB.' });
      const mediaType = imageType(content);
      if (!mediaType) return reply.code(415).send({ error: 'Sube una imagen JPG, PNG o WebP válida.' });
      const title = file.filename.replace(/\\/g, '/').split('/').at(-1)?.replace(/\.[^.]+$/, '')
        .replace(/[\p{Cc}\p{Cf}]/gu, '').trim().slice(0, 120) || 'Referencia';
      const [row] = await db.insert(projectReferences).values({ projectId: project.id, title, mediaType, content }).returning(selection);
      if (!row) throw new Error('Reference upload returned no row');
      return reply.code(201).send(referenceMetadata(row));
    } catch (error) {
      if (error instanceof app.multipartErrors.RequestFileTooLargeError) {
        return reply.code(413).send({ error: 'La imagen supera el límite de 5 MB.' });
      }
      throw error;
    }
  });

  app.get('/api/projects/:projectId/references/:referenceId/image', async (request, reply) => {
    const params = referenceParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró la referencia.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    const [row] = await scope.db.select().from(projectReferences).where(and(
      eq(projectReferences.projectId, scope.project.id), eq(projectReferences.id, params.data.referenceId),
    ));
    if (!row) return reply.code(404).send({ error: 'No se encontró la referencia.' });
    return reply.type(row.mediaType).header('cache-control', 'private, no-store')
      .header('x-content-type-options', 'nosniff').send(await readReference(row));
  });

  app.delete('/api/projects/:projectId/references/:referenceId', async (request, reply) => {
    const params = referenceParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró la referencia.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    const { db, project } = scope;
    await db.transaction(async (tx) => {
      if (!await loadOwnedProject(tx, project.workspaceId, project.id, true)) return;
      await tx.delete(projectReferences).where(and(eq(projectReferences.projectId, project.id), eq(projectReferences.id, params.data.referenceId)));
    });
    return { ok: true };
  });

  app.post('/api/projects/:projectId/references/analyze', async (request, reply) => {
    const params = projectParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró el proyecto.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    if (isAiOffline()) return reply.code(503).send({ error: 'El análisis por IA no está habilitado en este entorno.' });
    const body = analysisInput.safeParse(request.body);
    if (!body.success) return reply.code(400).send({ error: `Selecciona de 1 a ${REFERENCE_MAX_ANALYSIS_IMAGES} referencias para analizar.` });
    const ids = [...new Set(body.data.referenceIds)];
    const { db, project } = scope;
    const rows = await db.select().from(projectReferences).where(and(
      eq(projectReferences.projectId, project.id), inArray(projectReferences.id, ids),
    ));
    if (rows.length !== ids.length) return reply.code(404).send({ error: 'Alguna referencia ya no está disponible en este proyecto.' });
    const images = await Promise.all(rows.map(async (row) => ({ ...row, content: await readReference(row) })));
    if (images.reduce((bytes, image) => bytes + image.content.length, 0) > REFERENCE_MAX_ANALYSIS_BYTES) {
      return reply.code(413).send({ error: 'Las imágenes seleccionadas superan 12 MB en total. Analiza menos referencias a la vez.' });
    }
    let suggestions;
    try {
      suggestions = await identifyQuoteSuggestions(project, images);
    } catch (error) {
      request.log.warn({ errorType: error instanceof Error ? error.name : 'unknown' }, 'Reference analysis provider failed');
      return reply.code(502).send({ error: 'No se pudo analizar las referencias. Inténtalo de nuevo en un momento.' });
    }
    const analysis = await db.transaction(async (tx) => {
      const latest = await loadOwnedProject(tx, project.workspaceId, project.id, true);
      if (!latest) return null;
      const currentImages = await tx.select({ id: projectReferences.id }).from(projectReferences)
        .where(and(eq(projectReferences.projectId, project.id), inArray(projectReferences.id, ids)));
      if (currentImages.length !== ids.length) return null;
      const result: ReferenceAnalysis = {
        createdAt: new Date().toISOString(),
        suggestions: suggestions.filter((suggestion) => !suggestionAlreadyInInventory(latest.items, suggestion)),
      };
      await tx.insert(projectReferenceAnalyses).values({ projectId: project.id, result }).onConflictDoUpdate({
        target: projectReferenceAnalyses.projectId, set: { result },
      });
      return result;
    });
    return analysis ?? reply.code(409).send({ error: 'Las referencias cambiaron durante el análisis. Selecciónalas otra vez.' });
  });

  app.post('/api/projects/:projectId/reference-suggestions/:suggestionId/add', async (request, reply) => {
    const params = suggestionParams.safeParse(request.params);
    if (!params.success) return reply.code(404).send({ error: 'No se encontró la sugerencia.' });
    const scope = await authorize(request, reply, params.data.projectId);
    if (!scope) return;
    const { db, project } = scope;
    const result = await db.transaction(async (tx) => {
      const current = await loadOwnedProject(tx, project.workspaceId, project.id, true);
      if (!current) return null;
      const itemId = suggestedItemId(params.data.suggestionId);
      if (current.items.some(({ id }) => id === itemId)) return current;
      const [analysis] = await tx.select().from(projectReferenceAnalyses).where(eq(projectReferenceAnalyses.projectId, current.id));
      const suggestion = analysis?.result.suggestions.find(({ id }) => id === params.data.suggestionId);
      if (!suggestion) return null;
      if (suggestionAlreadyInInventory(current.items, suggestion)) return current;
      const item: CatalogItem = {
        id: itemId, zoneId: suggestion.zoneId ?? '', name: suggestion.name, qty: suggestion.qty,
        category: 'equipamiento', notes: suggestion.reason, quotes: [],
        unquotedReason: 'Pendiente de buscar precios en las tiendas registradas.',
      };
      const updated = { ...current, items: [...current.items, item] };
      await saveProjectPayload(tx, updated);
      return updated;
    });
    return result ? { project: result } : reply.code(404).send({ error: 'La sugerencia ya no está disponible.' });
  });
}
