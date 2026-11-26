import { expect, it } from 'vitest';
import { simanCandidates } from '../src/server/scrapers/siman.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
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
