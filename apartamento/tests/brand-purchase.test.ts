import { expect, it } from 'vitest';
import { productClass, productProvenance } from '../src/shared/brands.js';
import { matchesPurchase } from '../src/domain/product-match.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import type { LineItemQuote } from '../src/shared/types.js';

it('distinguishes a named product line from a brand, a declared replica, and a material finish', () => {
  const quote = { title:'Sartén All-Clad D3', brand:'All-Clad' } as LineItemQuote;
  expect(productClass(quote)).toBe('Alta gama documentada');
  expect(productClass({ ...quote, title:'Sartén All-Clad' })).toBe('Línea por identificar');
  expect(productClass({ ...quote, title:'Réplica de sartén All-Clad D3' })).toBe('Réplica declarada');
  expect(productProvenance('Mesa de comedor imitación de madera')).toBe('unverified');
  expect(productClass({ ...quote, brand:'sin marca' })).toBe('Genérico declarado');
});

it('rejects a frame without its mattress and outdoor dining sets for the indoor dining room', () => {
  const items = buildNatarundiProject('w', 'p').items;
  const bed = items.find(i => i.id === 'i-master-bed')!;
  const table = items.find(i => i.id === 'i-dining-set')!;
  expect(matchesPurchase(bed,'Cama queen Kaia')).toBe(false);
  expect(matchesPurchase(bed,'Combo base Queen con colchón')).toBe(true);
  expect(matchesPurchase(table,'Set terraza para 6 personas')).toBe(false);
  expect(matchesPurchase(table,'Juego de comedor para 4 personas')).toBe(true);
});
