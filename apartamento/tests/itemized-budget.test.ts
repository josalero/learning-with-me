import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { expect, it } from 'vitest';
import { applyDemoQuotes } from '../src/domain/demo-quotes.js';
import { itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { projectStats } from '../src/shared/projectStats.js';
import type { FurnishingProject } from '../src/shared/types.js';

it('migrates legacy bundles once while preserving independently priced items and real sets', async () => {
  const db = new PGlite();
  try {
    const legacy = applyDemoQuotes(buildNatarundiProject('workspace', 'legacy'));
    const originalTotal = projectStats(legacy.items).totalCrc;
    const migration = await readFile(new URL('../src/server/db/migrations/0004_itemize_reference_bundles.sql', import.meta.url), 'utf8');
    await db.exec('CREATE TABLE projects (id text PRIMARY KEY, payload jsonb NOT NULL, updated_at timestamptz)');
    await db.query('INSERT INTO projects (id, payload) VALUES ($1, $2::jsonb)', [legacy.id, JSON.stringify(legacy)]);
    await db.exec(migration);
    await db.exec(migration);
    const { rows: [row] } = await db.query<{ payload: FurnishingProject }>('SELECT payload FROM projects');
    const project = row!.payload;
    expect(project).toEqual(JSON.parse(JSON.stringify(itemizeCatalogBundles(legacy))));
    expect(project.itemizedBundles?.map(({ id }) => id)).toEqual(['i-cookware', 'i-linens']);
    expect(projectStats(project.items).totalCrc).toBe(originalTotal - 45_900 - 3 * 42_000);
    expect(project.items.find(({ id }) => id === 'i-cookware-cubiertos')).toMatchObject({ zoneId: 'z-kitchen', purchaseUnit: 'set', qty: 1, quotes: [] });
    expect(project.items.find(({ id }) => id === 'i-linens-sabanas-individual')).toMatchObject({ zoneId: 'z-br3', purchaseUnit: 'set', qty: 1 });
    expect(project.items.find(({ id }) => id === 'i-dining-set')?.quotes).toHaveLength(1);
  } finally {
    await db.close();
  }
});
