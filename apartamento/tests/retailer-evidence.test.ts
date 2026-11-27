import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { expect, it } from 'vitest';
import { simanCandidates } from '../src/server/scrapers/siman.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';
import { matchesPurchase } from '../src/domain/product-match.js';
import { rankReviewedProducts } from '../src/server/services/reviewRanking.js';
import type { LineItemQuote } from '../src/shared/types.js';

const productUrl = 'https://cr.siman.com/frigorifico-abc/p';
const retailer = { productName: 'Refrigeradora RT123', link:productUrl, linkText:'frigorifico-abc', description:'', Modelo:['RT123'],
  Dimensiones:['Alto 170.0 x Ancho 60.0 x Profundidad 65.0 cm aprox.'], items:[{sellers:[{commertialOffer:{Price:200000, IsAvailable:true}}]}] };

it('keeps only the single available SKU within all measured limits, on the actual retailer domain', () => {
  const item = buildNatarundiProject('w','p').items[0]!;
  const candidates = simanCandidates([retailer, { ...retailer, Dimensiones:[] }, { ...retailer, Dimensiones:['Alto 170 x Ancho 80 x Profundidad 65 cm'] },
    { ...retailer, link:'https://otro.cr/frigorifico-abc/p' }, { ...retailer, items:[] }, { ...retailer, productName:'Repuesto para refrigeradora' }], item, new URL('https://cr.siman.com/'));
  expect(candidates).toMatchObject([{ productUrl, priceCrc:200000, dimensions:{ h:1.7, w:.6, d:.65 } }]);
});
it('exposes published offers with absent, low or sparse reviews without promoting them', async () => {
  const quote: LineItemQuote = { id:'q', productUrl, basis:'published', priceCrc:40000, title:'Producto', source:'store_direct', tier:'balanced',
    capturedAt:'2026-11-25T10:00:00Z', availability:'in_stock', storeName:'Siman', storeUrl:'https://cr.siman.com/' };
  const cases = [quote, { ...quote, productUrl:productUrl+'1', review:{rating:3,count:20,sourceUrl:productUrl} },
    { ...quote, productUrl:productUrl+'2', review:{rating:5,count:2,sourceUrl:productUrl} }];
  const result = await rankReviewedProducts(cases.map((q) => ({quote:q, modelIds:[]})), () => {});
  expect(result.recommendation).toBeUndefined();
  expect(result.alternatives).toEqual(cases);
});

it('binds an explicitly selected size to its own SKU, stock, amount and direct URL', () => {
  const product = { ...retailer, productName:'Juego de sábanas', items:[
    { itemId:'123', name:'Twin gris', sellers:[{commertialOffer:{Price:8450,IsAvailable:true}}] },
    { itemId:'456', name:'Queen gris', sellers:[{commertialOffer:{Price:12000,IsAvailable:true}}] },
  ] };
  const item = { ...buildNatarundiProject('w','p').items[1]!, name:'Juego de sábanas' };
  const site = new URL('https://cr.siman.com/');
  expect(simanCandidates([product], item, site)).toEqual([]);
  expect(simanCandidates([product], item, site, '123')).toMatchObject([{priceCrc:8450, title:'Juego de sábanas · Twin gris',productUrl: productUrl+'?skuId=123'}]);
  expect(simanCandidates([product], item, site, '555')).toEqual([]);
});

it('does not quote a set of silverware sold under a translated dinnerware title', () => {
  const project = itemizeCatalogBundles(buildNatarundiProject('w', 'p'));
  const dinnerware = project.items.find(({ id }) => id === 'i-cookware-vajilla')!;
  expect(matchesPurchase(dinnerware, 'Juego de vajilla de 5 piezas', 'El set incluye dos tenedores, un cuchillo y una cucharilla.')).toBe(false);
  expect(matchesPurchase(dinnerware, 'Vajilla de 16 piezas', 'Cerámica para 4 puestos.')).toBe(true);
});

it('registers the new retailers in each workspace and preserves a retailer already registered at the same domain', async () => {
  const db = new PGlite();
  try {
    await db.exec(`CREATE TABLE workspaces (id uuid PRIMARY KEY);
      CREATE TABLE regional_stores (id text NOT NULL, workspace_id uuid NOT NULL REFERENCES workspaces,
        name text, region text, website_url text, probe_kind text, PRIMARY KEY (id, workspace_id));
      INSERT INTO workspaces VALUES ('00000000-0000-0000-0000-000000000001'), ('00000000-0000-0000-0000-000000000002');
      INSERT INTO regional_stores VALUES ('custom-cemaco', '00000000-0000-0000-0000-000000000001',
        'Cemaco existente', 'san-jose', 'https://cemaco.co.cr/products', 'json_ld');`);
    const migration = await readFile(new URL('../src/server/db/migrations/0009_home_retailers.sql', import.meta.url), 'utf8');
    await db.exec(migration);
    await db.exec(migration);
    const { rows } = await db.query<{ name: string }>('SELECT name FROM regional_stores ORDER BY workspace_id, name');
    expect(rows.map(({ name }) => name)).toEqual([
      'Cemaco existente', 'Crate & Barrel Costa Rica', 'Cemaco Costa Rica', 'Crate & Barrel Costa Rica',
    ]);
  } finally {
    await db.close();
  }
});
