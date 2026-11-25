import { randomUUID } from 'node:crypto';
import type { FurnishingProject, LineItemQuote } from '../shared/types.js';

type QuoteSeed = {
  itemId: string;
  storeName: string;
  storeUrl: string;
  productUrl: string;
  legacyProductUrl: string;
  title: string;
  priceCrc: number;
  source: LineItemQuote['source'];
  availability: LineItemQuote['availability'];
};

/** Referencia local — precios ilustrativos con tienda y enlace (no scrape en vivo). */
const DEMO_QUOTE_SEEDS: QuoteSeed[] = [
  {
    itemId: 'i-refri',
    storeName: 'MisterPrecios',
    storeUrl: 'https://misterprecios.com/',
    productUrl: 'https://misterprecios.com/c/refrigeradoras',
    legacyProductUrl: 'https://www.misterprecios.cr/buscar?q=refrigeradora',
    title: 'Refrigeradora 10–12 pies',
    priceCrc: 478_000,
    source: 'mister_precios',
    availability: 'unknown',
  },
  {
    itemId: 'i-micro',
    storeName: 'AhorraYa',
    storeUrl: 'https://www.ahorraya.app/ahorraya-costa-rica',
    productUrl: 'https://www.ahorraya.app/ahorraya-costa-rica',
    legacyProductUrl: 'https://www.ahorraya.cr/precios/electrodomesticos',
    title: 'Microondas digital',
    priceCrc: 89_500,
    source: 'ahorra_ya',
    availability: 'unknown',
  },
  {
    itemId: 'i-cookware',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/accesorios-cocina',
    legacyProductUrl: 'https://epa.cr/search?q=set+ollas',
    title: 'Set menaje cocina',
    priceCrc: 45_900,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-island-stools',
    storeName: 'Aliss',
    storeUrl: 'https://aliss.cr/',
    productUrl: 'https://aliss.cr/muebleria-aliss',
    legacyProductUrl: 'https://www.aliss.cr/catalogsearch/result/?q=taburete',
    title: 'Taburete alto (c/u)',
    priceCrc: 32_500,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-fan-kitchen',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/ventiladores-techo',
    legacyProductUrl: 'https://epa.cr/search?q=abanico+techo',
    title: 'Abanico techo 52"',
    priceCrc: 92_000,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-washer',
    storeName: 'MisterPrecios',
    storeUrl: 'https://misterprecios.com/',
    productUrl: 'https://misterprecios.com/c/lavadoras',
    legacyProductUrl: 'https://www.misterprecios.cr/buscar?q=lavadora',
    title: 'Lavadora carga superior',
    priceCrc: 385_000,
    source: 'mister_precios',
    availability: 'unknown',
  },
  {
    itemId: 'i-dining-set',
    storeName: 'Fantini Liberia',
    storeUrl: 'https://fantini.cr/es-cr',
    productUrl: 'https://fantini.cr/es-cr/collections/hogar',
    legacyProductUrl: 'https://fantini.cr/search?q=comedor',
    title: 'Mesa + 4 sillas',
    priceCrc: 289_000,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-sofa',
    storeName: 'Siman Costa Rica',
    storeUrl: 'https://cr.siman.com/',
    productUrl: 'https://cr.siman.com/c/sofa-camas',
    legacyProductUrl: 'https://www.siman.com/buscar?q=sofa+cama',
    title: 'Sofá cama',
    priceCrc: 349_900,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-tv',
    storeName: 'MisterPrecios',
    storeUrl: 'https://misterprecios.com/',
    productUrl: 'https://misterprecios.com/c/televisores',
    legacyProductUrl: 'https://www.misterprecios.cr/buscar?q=televisor+50',
    title: 'Smart TV 50"',
    priceCrc: 279_000,
    source: 'mister_precios',
    availability: 'unknown',
  },
  {
    itemId: 'i-fan-living',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/ventiladores-techo',
    legacyProductUrl: 'https://epa.cr/search?q=abanico+techo',
    title: 'Abanico techo sala',
    priceCrc: 89_900,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-master-bed',
    storeName: 'Nalakalu',
    storeUrl: 'https://nalakalu.com/',
    productUrl: 'https://nalakalu.com/product-category/dormitorio/',
    legacyProductUrl: 'https://nalakalu.com/?s=colchon+queen',
    title: 'Cama queen + colchón',
    priceCrc: 425_000,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-fan-master',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/ventiladores-techo',
    legacyProductUrl: 'https://epa.cr/search?q=abanico+techo',
    title: 'Abanico techo dormitorio',
    priceCrc: 87_500,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-br2-bed',
    storeName: 'Aliss',
    storeUrl: 'https://aliss.cr/',
    productUrl: 'https://aliss.cr/muebleria-aliss',
    legacyProductUrl: 'https://www.aliss.cr/catalogsearch/result/?q=cama+full',
    title: 'Cama full + colchón',
    priceCrc: 298_000,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-br3-bed',
    storeName: 'Fantini Liberia',
    storeUrl: 'https://fantini.cr/es-cr',
    productUrl: 'https://fantini.cr/es-cr/collections/hogar',
    legacyProductUrl: 'https://fantini.cr/search?q=cama+individual',
    title: 'Cama individual compacta',
    priceCrc: 165_000,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-linens',
    storeName: 'Siman Costa Rica',
    storeUrl: 'https://cr.siman.com/',
    productUrl: 'https://cr.siman.com/hogar/dormitorio/sabanas-y-sobrefundas',
    legacyProductUrl: 'https://www.siman.com/buscar?q=sabanas',
    title: 'Juego sábanas + toallas (por habitación)',
    priceCrc: 42_000,
    source: 'store_direct',
    availability: 'unknown',
  },
  {
    itemId: 'i-bath-sets',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/accesorios-bano',
    legacyProductUrl: 'https://epa.cr/search?q=accesorios+baño',
    title: 'Set accesorios baño (por baño)',
    priceCrc: 38_500,
    source: 'store_direct',
    availability: 'in_stock',
  },
  {
    itemId: 'i-terrace-set',
    storeName: 'Aliss',
    storeUrl: 'https://aliss.cr/',
    productUrl: 'https://aliss.cr/verano-playero-aliss/muebles-de-exterior',
    legacyProductUrl: 'https://www.aliss.cr/catalogsearch/result/?q=muebles+exterior',
    title: 'Set terraza (mesa + sillas)',
    priceCrc: 198_000,
    source: 'store_direct',
    availability: 'unknown',
  },
];

function seedToQuote(seed: QuoteSeed, basis: 'catalog' | 'estimate', capturedAt = new Date().toISOString()): LineItemQuote {
  return {
    id: randomUUID(),
    storeName: seed.storeName,
    storeUrl: seed.storeUrl,
    productUrl: seed.productUrl,
    title: seed.title,
    priceCrc: seed.priceCrc,
    availability: seed.availability,
    source: seed.source,
    basis,
    capturedAt,
  };
}

/** Cotización de respaldo para ítems que requieren decisión humana previa. */
const ON_DEMAND_ESTIMATES: QuoteSeed[] = [
  {
    itemId: 'i-dryer',
    storeName: 'MisterPrecios',
    storeUrl: 'https://misterprecios.com/',
    productUrl: 'https://misterprecios.com/c/linea-blanca',
    legacyProductUrl: 'https://www.misterprecios.cr/buscar?q=secadora',
    title: 'Secadora (estimado — confirmar torre vs par)',
    priceCrc: 335_000,
    source: 'agent_estimate',
    availability: 'unknown',
  },
  {
    itemId: 'i-br3-desk',
    storeName: 'EPA',
    storeUrl: 'https://cr.epaenlinea.com/es-cr',
    productUrl: 'https://cr.epaenlinea.com/es-cr/collections/muebles-interior',
    legacyProductUrl: 'https://epa.cr/search?q=escritorio+plegable',
    title: 'Escritorio plegable (estimado)',
    priceCrc: 89_900,
    source: 'agent_estimate',
    availability: 'unknown',
  },
  {
    itemId: 'i-freight',
    storeName: 'Cotización logística',
    storeUrl: '',
    productUrl: '',
    legacyProductUrl: 'https://www.ahorraya.cr/',
    title: 'Flete GAM → Guanacaste (estimado volumen medio)',
    priceCrc: 185_000,
    source: 'manual',
    availability: 'unknown',
  },
];

export function buildCatalogQuoteForItem(itemId: string, allowEstimate = false): LineItemQuote | null {
  const seed = DEMO_QUOTE_SEEDS.find((s) => s.itemId === itemId);
  if (seed) return seedToQuote(seed, 'catalog');
  if (!allowEstimate) return null;
  const est = ON_DEMAND_ESTIMATES.find((s) => s.itemId === itemId);
  return est ? seedToQuote(est, 'estimate') : null;
}

export function applyDemoQuotes(project: FurnishingProject): FurnishingProject {
  const capturedAt = new Date().toISOString();
  const items = project.items.map((it) => {
    const seed = DEMO_QUOTE_SEEDS.find((s) => s.itemId === it.id);
    if (!seed) {
      return { ...it, quotes: [...it.quotes] };
    }
    const quote = seedToQuote(seed, 'catalog', capturedAt);
    return { ...it, quotes: [quote], unquotedReason: undefined };
  });
  return { ...project, items };
}

export function repairLegacyQuoteLinks(items: FurnishingProject['items']): FurnishingProject['items'] {
  return items.map((item) => {
    const catalog = DEMO_QUOTE_SEEDS.find((seed) => seed.itemId === item.id);
    const seed = catalog ?? ON_DEMAND_ESTIMATES.find((estimate) => estimate.itemId === item.id);
    if (!seed) return item;
    const quotes = item.quotes.map((quote): LineItemQuote => {
      if (quote.basis === 'published' || quote.productUrl !== seed.legacyProductUrl || quote.storeName !== seed.storeName) return quote;
      return { ...quote, storeUrl: seed.storeUrl, productUrl: seed.productUrl, basis: quote.basis ?? (catalog ? 'catalog' : 'estimate') };
    });
    return { ...item, quotes };
  });
}
