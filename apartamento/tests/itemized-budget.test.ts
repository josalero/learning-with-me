import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { expect, it } from 'vitest';
import { applyDemoQuotes } from '../src/domain/demo-quotes.js';
import { itemizeBundle, itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { projectStats } from '../src/shared/projectStats.js';
import { matchesPurchase } from '../src/domain/product-match.js';
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

it('repairs the split television and project freight without losing quantities or quote history', async () => {
  const db = new PGlite();
  try {
    const original = applyDemoQuotes(buildNatarundiProject('workspace', 'natarundi'));
    original.zones = original.zones.filter(({ id }) => id !== 'z-logistics');
    original.items.find(({ id }) => id === 'i-freight')!.zoneId = 'z-living';
    const television = original.items.find(({ id }) => id === 'i-tv')!;
    const legacy = itemizeBundle(original, television, [{ id: 'legacy-sala', name: 'Sala', qty: 1 },
      { id: 'legacy-dormitorio', name: 'Dormitorio', qty: 2 }]);
    const furniture = legacy.items.find(({ id }) => id === 'legacy-sala')!;
    furniture.quotes = [{ ...television.quotes[0]!, title: 'Juego de sala de cinco plazas', priceCrc: 199_990 }];
    const unrelated = { ...legacy, id: 'unrelated', name: 'Otro proyecto' };
    await db.exec('CREATE TABLE projects (id text PRIMARY KEY, name text, address text, payload jsonb NOT NULL, updated_at timestamptz)');
    for (const project of [legacy, unrelated]) {
      await db.query('INSERT INTO projects (id, name, address, payload) VALUES ($1, $2, $3, $4::jsonb)',
        [project.id, project.name, project.address, JSON.stringify(project)]);
    }
    const migration = await readFile(new URL('../src/server/db/migrations/0007_natarundi_item_locations.sql', import.meta.url), 'utf8');
    await db.exec(migration);
    await db.exec(migration);
    const { rows: [row, other] } = await db.query<{ payload: FurnishingProject }>('SELECT payload FROM projects ORDER BY id');
    const project = row!.payload;
    expect(project.items).toHaveLength(legacy.items.length);
    expect(project.items.find(({ id }) => id === 'i-tv')).toEqual(television);
    expect(project.items.some(({ id }) => id === 'legacy-sala')).toBe(false);
    expect(project.itemizedBundles?.filter(({ id }) => id === furniture.id)).toEqual([furniture]);
    const bedroom = project.items.find(({ id }) => id === 'legacy-dormitorio')!;
    expect(bedroom).toMatchObject({ name: 'Televisor para dormitorio', qty: 2, zoneId: '', quotes: [] });
    expect(matchesPurchase(bedroom, 'Juego para dormitorio')).toBe(false);
    expect(matchesPurchase(bedroom, 'Televisor Smart TV de 32 pulgadas')).toBe(true);
    expect(project.items.find(({ id }) => id === 'i-freight')?.zoneId).toBe('z-logistics');
    expect(project.zones.filter(({ id }) => id === 'z-logistics')).toHaveLength(1);
    expect(other!.payload).toEqual(JSON.parse(JSON.stringify(unrelated)));
  } finally {
    await db.close();
  }
});
