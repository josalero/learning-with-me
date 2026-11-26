import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { expect, it } from 'vitest';

it('archives unsupported prices and enforces direct product links on insert, update and candidate offers', async () => {
  const db = new PGlite();
  const valid = { basis: 'published', priceCrc: 12000, productUrl: 'https://tienda.cr/productos/abc', storeUrl: 'https://tienda.cr/' };
  const invalid = { ...valid, productUrl: null };
  const id = 'abc12345-1234-4123-abcd-123456789abc';
  try {
    await db.exec('CREATE TABLE projects (id uuid PRIMARY KEY, payload jsonb NOT NULL, updated_at timestamptz)');
    await db.query('INSERT INTO projects (id,payload) VALUES ($1,$2)', [id, { items: [{ id:'a', quotes:[invalid, valid] }], itemizedBundles: [{ id:'b', quotes:[invalid] }] }]);
    await db.exec(await readFile(new URL('../src/server/db/migrations/0005_require_product_links.sql', import.meta.url), 'utf8'));
    expect((await db.query('SELECT item_id FROM archived_apartment_quotes ORDER BY item_id')).rows).toEqual([{item_id:'a'}, {item_id:'b'}]);
    expect((await db.query("SELECT payload->'items'->0->'quotes' AS quotes FROM projects")).rows).toEqual([{ quotes:[valid] }]);
    for (const productUrl of [null, '', '  ', 'file:///p', 'https://tienda.cr', 'https://tienda.cr/', 'https://tienda.cr/es-cr', 'https://tienda.cr/search?q=x', 'https://tienda.cr/es-cr/collections/cocina']) {
      const payload = { items: [{ id: 'x', quotes: [{ ...valid, productUrl }] }] };
      await expect(db.query('UPDATE projects SET payload=$1', [payload])).rejects.toMatchObject({ code: '23514' });
    }
    await expect(db.query('INSERT INTO projects (id,payload) VALUES (gen_random_uuid(),$1)', [{ items:[{ id:'b', quotes:[], lastSearch:{balanced:{ offers:[invalid] }} }] }])).rejects.toMatchObject({ code:'23514' });
    await db.query('UPDATE projects SET payload=$1', [{items:[{ id:'b', quotes:[valid], lastSearch:{balanced:{ offers:[valid] }} }]}]);
  } finally { await db.close(); }
});
