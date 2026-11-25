import { randomUUID } from 'node:crypto';
import { buildCatalogQuoteForItem } from '../../domain/demo-quotes.js';
import { budgetProfiles, budgetQuoteJobs, DEFAULT_BUDGET_TIER, quoteTier, quotesForTier } from '../../shared/budgetProfiles.js';
import { QUOTE_CONCURRENCY, type BudgetQuote, type BudgetTier, type CatalogItem, type FurnishingProject, type LineItemQuote, type QuoteActivity, type StoreRef } from '../../shared/types.js';
import { isAiOffline } from '../agents/modelRouter.js';
import { findProductPages } from '../agents/pricingAgent.js';
import { fetchJsonLdOffers } from '../scrapers/fetchPage.js';
import type { ParsedOffer } from '../scrapers/jsonld-offer.js';
import { assertPublicHttpUrl, matchesStoreWebsite } from '../scrapers/ssrf-guard.js';
import { rankReviewedProducts, type PricedProduct } from './reviewRanking.js';

export type QuoteMode = 'url' | 'agent' | 'catalog' | 'estimate';
export type QuoteItemResult = { quote: LineItemQuote; mode: QuoteMode; alternatives?: LineItemQuote[] };
type QuoteStore = Pick<StoreRef, 'name' | 'websiteUrl'>;

type QuoteOptions = {
  productUrl?: string;
  tier?: BudgetTier;
  allowEstimate?: boolean;
  region?: FurnishingProject['region'];
  usdCrcRate?: number;
  stores?: QuoteStore[];
  onActivity?: (activity: QuoteActivity) => void;
};

export function quoteActivity(item: CatalogItem, stage: QuoteActivity['stage'], detail: string, tier: BudgetTier = DEFAULT_BUDGET_TIER): QuoteActivity {
  return { itemId: item.id, itemName: item.name, stage, detail, tier };
}

function storeForUrl(url: URL): Pick<LineItemQuote, 'storeName' | 'storeUrl' | 'source'> {
  const host = url.hostname.toLowerCase();
  if (['misterprecios.com', 'misterprecios.cr'].includes(host.replace(/^www\./, ''))) {
    return { storeName: 'MisterPrecios', storeUrl: url.origin, source: 'mister_precios' };
  }
  if (['ahorraya.app', 'ahorraya.cr'].includes(host.replace(/^www\./, ''))) {
    return { storeName: 'AhorraYa', storeUrl: url.origin, source: 'ahorra_ya' };
  }
  return { storeName: host.replace(/^www\./, ''), storeUrl: url.origin, source: 'store_direct' };
}

function offerToQuote(offer: ParsedOffer, usdCrcRate = 0): LineItemQuote | null {
  const amount = offer.priceCrc ?? (offer.priceUsd && usdCrcRate > 0 ? offer.priceUsd * usdCrcRate : 0);
  if (!Number.isFinite(amount) || amount <= 0) return null;
  const stock = offer.availability?.split('/').at(-1)?.toLowerCase();
  return {
    id: randomUUID(),
    ...storeForUrl(assertPublicHttpUrl(offer.productUrl)),
    productUrl: offer.productUrl,
    basis: 'published',
    title: offer.title,
    priceCrc: Math.round(amount),
    priceUsd: offer.priceUsd,
    availability: stock === 'instock' ? 'in_stock'
      : stock && ['outofstock', 'soldout', 'discontinued'].includes(stock) ? 'out_of_stock' : 'unknown',
    capturedAt: new Date().toISOString(),
    review: offer.review,
  };
}

async function quoteFromPage(url: string, usdCrcRate?: number, website?: URL, limits?: CatalogItem['dimsM']) {
  const offers = await fetchJsonLdOffers(assertPublicHttpUrl(url).toString(), website);
  const products = offers.flatMap((offer): PricedProduct[] => {
    for (const axis of ['w', 'h', 'd'] as const) {
      const maximum = limits?.[axis];
      const measurement = offer.dimensions?.[axis];
      if (maximum !== undefined && measurement !== undefined && measurement - maximum > 1e-6) return [];
    }
    const quote = offerToQuote(offer, usdCrcRate);
    return quote ? [{ quote, modelIds: offer.modelIds ?? [] }] : [];
  });
  return products.find(({ quote }) => quote.availability !== 'out_of_stock') ?? products[0];
}

function publicUrl(raw: string) {
  try {
    const url = assertPublicHttpUrl(raw);
    if (!url.port && !url.username && !url.password) return url;
  } catch {
    // A malformed or private address cannot provide a quote.
  }
}

async function verifyCandidates(candidates: string[], item: CatalogItem, usdCrcRate?: number, website?: URL) {
  const quotes = await Promise.all(candidates.map(async (candidate) => {
    const url = publicUrl(candidate);
    if (url?.protocol !== 'https:' || (website && !matchesStoreWebsite(url, website))) return undefined;
    return quoteFromPage(url.href, usdCrcRate, website, item.dimsM).catch(() => undefined);
  }));
  return quotes.filter((result): result is PricedProduct => !!result && result.quote.availability !== 'out_of_stock'
    && new URL(result.quote.productUrl).protocol === 'https:');
}

async function quoteRegisteredStores(item: CatalogItem, stores: QuoteStore[], tier: BudgetTier, options: QuoteOptions,
  report: (stage: QuoteActivity['stage'], detail: string) => void): Promise<PricedProduct[]> {
  const total = stores.length;
  const jobs = stores.values();
  const products = new Map<string, PricedProduct[]>();
  const current = quotesForTier(item, tier).filter(({ basis }) => basis === 'published').map(({ productUrl }) => productUrl);
  let checked = 0;
  report('searching', `Consultando las tiendas registradas (0/${total}).`);

  async function worker() {
    for (const store of jobs) {
      const website = publicUrl(store.websiteUrl);
      let found: PricedProduct[] = [];
      if (website) {
        const candidates = await findProductPages(item, options.region, website, tier).catch(() => []);
        found = await verifyCandidates([...new Set([...candidates, ...current])], item, options.usdCrcRate, website);
        products.set(store.websiteUrl, found.map(({ quote, modelIds }) => ({
          quote: { ...quote, storeName: store.name, storeUrl: website.href }, modelIds,
        })));
      }
      checked++;
      report('searching', `${checked}/${total} tiendas revisadas · ${store.name}${found.length ? ' · Precio verificado' : ' · Sin precio verificable'}`);
    }
  }

  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, total) }, worker));
  return stores.flatMap((store) => products.get(store.websiteUrl) ?? []);
}

export async function quoteItemOnDemand(item: CatalogItem, options?: QuoteOptions): Promise<QuoteItemResult> {
  const tier = options?.tier ?? DEFAULT_BUDGET_TIER;
  const previous = quotesForTier(item, tier)[0];
  const hasPrevious = Boolean(previous);
  const previousLink = previous?.basis === 'published' ? publicUrl(previous.productUrl) : undefined;
  const report = (stage: QuoteActivity['stage'], detail: string) => options?.onActivity?.(quoteActivity(item, stage, detail, tier));
  if (options?.productUrl) {
    report('verifying', 'Leyendo el precio publicado en el enlace de la tienda.');
    const product = await quoteFromPage(options.productUrl, options.usdCrcRate, undefined, item.dimsM).catch(() => undefined);
    if (product) return { quote: { ...product.quote, tier }, mode: 'url' };
    throw new Error('No se pudo verificar una oferta compatible en el enlace de la tienda.');
  }

  const useAgent = !isAiOffline();
  if (useAgent) {
    let products: PricedProduct[] = [];
    if (options?.stores?.length) {
      const registered = options.stores.some(({ websiteUrl }) => {
        const website = publicUrl(websiteUrl);
        return previousLink && website && matchesStoreWebsite(previousLink, website);
      });
      const [fromStores, fromPreviousLink] = await Promise.all([
        quoteRegisteredStores(item, options.stores, tier, options, report),
        previousLink && !registered ? verifyCandidates([previousLink.href], item, options.usdCrcRate) : [],
      ]);
      products = [...fromStores, ...fromPreviousLink];
    } else {
      report('searching', 'El agente de IA busca opciones en tiendas de Costa Rica.');
      const candidates = await findProductPages(item, options?.region, undefined, tier).catch(() => []);
      const links = previousLink ? [...new Set([...candidates, previousLink.href])] : candidates;
      products = await verifyCandidates(links, item, options?.usdCrcRate);
    }
    const [quote, ...alternatives] = await rankReviewedProducts(products, () =>
      report('verifying', 'Comparando reseñas de compradores y precios para el modelo exacto.'));
    if (quote) return { quote: { ...quote, tier }, alternatives: alternatives.map((entry) => ({ ...entry, tier })), mode: 'agent' };
    if (products.length) {
      throw new Error(`Ninguna oferta alcanzó el mínimo de 4/5 con al menos 3 valoraciones verificables.${hasPrevious ? ' Se conservó la cotización anterior.' : ''}`);
    }
  }

  if (hasPrevious) {
    throw new Error(useAgent ? 'No se encontró un precio verificable. Se conservó la cotización anterior.'
      : 'La búsqueda automática no está disponible. Se conservó la cotización anterior.');
  }
  if (tier !== DEFAULT_BUDGET_TIER) {
    throw new Error(useAgent ? `No se encontró una oferta verificable para ${budgetProfiles[tier].label.toLocaleLowerCase('es-CR')}.`
      : 'La búsqueda automática no está disponible para este nivel.');
  }

  report('catalog', useAgent ? 'Revisando el catálogo de referencia tras la búsqueda.' : 'Buscando un precio en el catálogo de referencia.');
  const catalog = buildCatalogQuoteForItem(item.id, false);
  if (catalog) return { quote: catalog, mode: 'catalog' };
  if (options?.allowEstimate !== false) {
    const estimate = buildCatalogQuoteForItem(item.id, true);
    if (estimate) return { quote: estimate, mode: 'estimate' };
  }
  throw new Error(useAgent ? 'No se encontró un precio verificable para este artículo.'
    : 'La búsqueda automática no está disponible y el artículo no tiene un precio de referencia.');
}

export function attachQuoteToProject(project: FurnishingProject, itemId: string, quote: LineItemQuote, alternatives: LineItemQuote[] = []): FurnishingProject {
  return { ...project, items: project.items.map((item) => item.id === itemId ? replaceTierQuotes(item, quoteTier(quote), [quote, ...alternatives]) : item) };
}

export function replaceTierQuotes(item: CatalogItem, tier: BudgetTier, quotes: LineItemQuote[]): CatalogItem {
  return { ...item, quotes: [...item.quotes.filter((quote) => quoteTier(quote) !== tier), ...quotes] };
}

export async function quoteProjectItems(project: FurnishingProject, onActivity?: (activity: QuoteActivity) => void, stores?: QuoteStore[],
  scope: 'pending' | 'all' = 'pending'): Promise<{
  project: FurnishingProject;
  quoted: string[];
  saved: BudgetQuote[];
  failed: (BudgetQuote & { reason: string })[];
}> {
  const pending = budgetQuoteJobs(project, scope);
  const jobs = pending.entries();
  const results: (BudgetQuote & ({ quotes: LineItemQuote[] } | { reason: string }))[] = [];
  for (const { item, tier } of pending) onActivity?.(quoteActivity(item, 'queued', 'En espera de cotización.', tier));

  async function worker() {
    for (const [index, { item, tier }] of jobs) {
      try {
        const { quote, alternatives = [], mode } = await quoteItemOnDemand(item, { region: project.region, usdCrcRate: project.usdCrcRate, onActivity, stores, tier });
        results[index] = { itemId: item.id, tier, quotes: [quote, ...alternatives] };
        const label = mode === 'agent' || mode === 'url' ? `Precio verificado en ${quote.storeName}.`
          : mode === 'estimate' ? 'Estimación de referencia encontrada.' : 'Precio de catálogo encontrado.';
        onActivity?.(quoteActivity(item, 'saving', label, tier));
      } catch (error) {
        const reason = error instanceof Error ? error.message : 'No se pudo cotizar el artículo.';
        results[index] = { itemId: item.id, tier, reason };
        onActivity?.(quoteActivity(item, 'failed', reason, tier));
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, pending.length) }, worker));
  const items = new Map(project.items.map((item) => [item.id, item]));
  const saved: BudgetQuote[] = [];
  const failed: (BudgetQuote & { reason: string })[] = [];
  for (const result of results) {
    if ('quotes' in result) {
      const { itemId, tier, quotes } = result;
      items.set(itemId, replaceTierQuotes(items.get(itemId)!, tier, quotes));
      saved.push({ itemId, tier });
    }
    else failed.push(result);
  }
  return { project: { ...project, items: [...items.values()] }, quoted: [...new Set(saved.map(({ itemId }) => itemId))], saved, failed };
}
