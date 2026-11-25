import { randomUUID } from 'node:crypto';
import { buildCatalogQuoteForItem } from '../../domain/demo-quotes.js';
import { QUOTE_CONCURRENCY, type CatalogItem, type FurnishingProject, type LineItemQuote, type QuoteActivity, type StoreRef } from '../../shared/types.js';
import { isAiOffline } from '../agents/modelRouter.js';
import { findProductPages } from '../agents/pricingAgent.js';
import { fetchJsonLdOffers } from '../scrapers/fetchPage.js';
import type { ParsedOffer } from '../scrapers/jsonld-offer.js';
import { assertPublicHttpUrl, matchesStoreWebsite } from '../scrapers/ssrf-guard.js';

export type QuoteMode = 'url' | 'agent' | 'catalog' | 'estimate';
export type QuoteItemResult = { quote: LineItemQuote; mode: QuoteMode; alternatives?: LineItemQuote[] };
type QuoteStore = Pick<StoreRef, 'name' | 'websiteUrl'>;

type QuoteOptions = {
  productUrl?: string;
  allowEstimate?: boolean;
  region?: FurnishingProject['region'];
  usdCrcRate?: number;
  stores?: QuoteStore[];
  onActivity?: (activity: QuoteActivity) => void;
};

export function quoteActivity(item: CatalogItem, stage: QuoteActivity['stage'], detail: string): QuoteActivity {
  return { itemId: item.id, itemName: item.name, stage, detail };
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
  };
}

async function quoteFromPage(url: string, usdCrcRate?: number, website?: URL) {
  const offers = await fetchJsonLdOffers(assertPublicHttpUrl(url).toString(), website);
  const quotes = offers.map((offer) => offerToQuote(offer, usdCrcRate)).filter((quote) => quote !== null);
  return quotes.find(({ availability }) => availability !== 'out_of_stock') ?? quotes[0];
}

function publicUrl(raw: string) {
  try {
    const url = assertPublicHttpUrl(raw);
    if (!url.port && !url.username && !url.password) return url;
  } catch {
    // A malformed or private address cannot provide a quote.
  }
}

async function verifyCandidates(candidates: string[], usdCrcRate?: number, website?: URL) {
  const quotes = await Promise.all(candidates.map(async (candidate) => {
    const url = publicUrl(candidate);
    if (url?.protocol !== 'https:' || (website && !matchesStoreWebsite(url, website))) return undefined;
    return quoteFromPage(url.href, usdCrcRate, website).catch(() => undefined);
  }));
  return quotes.find((quote) => quote && quote.availability !== 'out_of_stock');
}

async function quoteRegisteredStores(item: CatalogItem, stores: QuoteStore[], options: QuoteOptions,
  report: (stage: QuoteActivity['stage'], detail: string) => void): Promise<LineItemQuote[]> {
  const total = stores.length;
  const quotes: LineItemQuote[] = [];
  const jobs = stores.values();
  const current = item.quotes.filter(({ basis }) => basis === 'published').map(({ productUrl }) => productUrl);
  let checked = 0;
  report('searching', `Consultando las tiendas registradas (0/${total}).`);

  async function worker() {
    for (const store of jobs) {
      const website = publicUrl(store.websiteUrl);
      let quote: LineItemQuote | undefined;
      if (website) {
        const candidates: string[] = await findProductPages(item, options.region, website).catch(() => []);
        quote = await verifyCandidates(candidates, options.usdCrcRate, website);
        if (!quote) {
          quote = await verifyCandidates(current.filter((url) => !candidates.includes(url)), options.usdCrcRate, website);
        }
        if (quote) quotes.push({ ...quote, storeName: store.name, storeUrl: website.href });
      }
      checked++;
      report('searching', `${checked}/${total} tiendas revisadas · ${store.name}${quote ? ' · Precio verificado' : ' · Sin precio verificable'}`);
    }
  }

  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, total) }, worker));
  quotes.sort((a, b) => Number(b.availability === 'in_stock') - Number(a.availability === 'in_stock')
    || a.priceCrc - b.priceCrc || a.storeName.localeCompare(b.storeName, 'es'));
  const seen = new Set<string>();
  return quotes.filter(({ productUrl }) => {
    if (seen.has(productUrl)) return false;
    seen.add(productUrl);
    return true;
  });
}

export async function quoteItemOnDemand(item: CatalogItem, options?: QuoteOptions): Promise<QuoteItemResult> {
  const report = (stage: QuoteActivity['stage'], detail: string) => options?.onActivity?.(quoteActivity(item, stage, detail));
  if (options?.productUrl) {
    report('verifying', 'Leyendo el precio publicado en el enlace de la tienda.');
    const quote = await quoteFromPage(options.productUrl, options.usdCrcRate).catch(() => undefined);
    if (quote) return { quote, mode: 'url' };
    throw new Error('No se pudo verificar un precio en el enlace de la tienda.');
  }

  const useAgent = !isAiOffline();
  if (useAgent) {
    if (options?.stores?.length) {
      const [quote, ...alternatives] = await quoteRegisteredStores(item, options.stores, options, report);
      if (quote) return { quote, alternatives, mode: 'agent' };
    } else {
      report('searching', 'El agente de IA busca opciones en tiendas de Costa Rica.');
      const candidates = await findProductPages(item, options?.region).catch(() => []);
      if (candidates.length) {
        report('verifying', 'Comprobando los precios publicados en las páginas encontradas.');
        const quote = await verifyCandidates(candidates, options?.usdCrcRate);
        if (quote) return { quote, mode: 'agent' };
      }
    }
  }

  if (item.quotes.length) {
    throw new Error(useAgent ? 'No se encontró un precio verificable. Se conservó la cotización anterior.'
      : 'La búsqueda automática no está disponible. Se conservó la cotización anterior.');
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
  return { ...project, items: project.items.map((item) => item.id === itemId ? { ...item, quotes: [quote, ...alternatives], unquotedReason: undefined } : item) };
}

export async function quoteUnquotedItems(project: FurnishingProject, onActivity?: (activity: QuoteActivity) => void, stores?: QuoteStore[]): Promise<{
  project: FurnishingProject;
  quoted: string[];
  failed: { itemId: string; reason: string }[];
}> {
  const pending = project.items.filter((item) => !item.quotes.length);
  const jobs = pending.entries();
  const results: ({ item: CatalogItem; quotes: LineItemQuote[] } | { item: CatalogItem; reason: string })[] = [];
  for (const item of pending) onActivity?.(quoteActivity(item, 'queued', 'En espera de cotización.'));

  async function worker() {
    for (const [index, item] of jobs) {
      try {
        const { quote, alternatives = [], mode } = await quoteItemOnDemand(item, { region: project.region, usdCrcRate: project.usdCrcRate, onActivity, stores });
        results[index] = { item, quotes: [quote, ...alternatives] };
        const label = mode === 'agent' || mode === 'url' ? `Precio verificado en ${quote.storeName}.`
          : mode === 'estimate' ? 'Estimación de referencia encontrada.' : 'Precio de catálogo encontrado.';
        onActivity?.(quoteActivity(item, 'saving', label));
      } catch (error) {
        const reason = error instanceof Error ? error.message : 'No se pudo cotizar el artículo.';
        results[index] = { item, reason };
        onActivity?.(quoteActivity(item, 'failed', reason));
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, pending.length) }, worker));
  const prices = new Map<string, LineItemQuote[]>();
  const failed: { itemId: string; reason: string }[] = [];
  for (const result of results) {
    if ('quotes' in result) prices.set(result.item.id, result.quotes);
    else failed.push({ itemId: result.item.id, reason: result.reason });
  }
  const items = project.items.map((item) => {
    const quotes = prices.get(item.id);
    return quotes ? { ...item, quotes, unquotedReason: undefined } : item;
  });
  return { project: { ...project, items }, quoted: [...prices.keys()], failed };
}
