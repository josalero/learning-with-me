import { randomUUID } from 'node:crypto';
import { matchesPurchase } from '../../domain/product-match.js';
import { productPageUrl } from '../../shared/quoteBasis.js';
import { purchaseName } from '../../shared/showerEnclosure.js';
import { productProvenance } from '../../shared/brands.js';
import { isPurchaseSet } from '../../shared/purchaseUnit.js';
import { normalizeSearch } from '../../shared/search.js';
import { findSimanOffers, simanTerm } from '../scrapers/siman.js';
import { budgetQuoteJobs, DEFAULT_BUDGET_TIER, quoteTier, quotesForTier } from '../../shared/budgetProfiles.js';
import { QUOTE_CONCURRENCY, type BudgetQuote, type BudgetTier, type CatalogItem, type FurnishingProject, type LineItemQuote, type QuoteActivity, type QuoteSearch, type StoreRef } from '../../shared/types.js';
import { offlineReason } from '../agents/modelRouter.js';
import { findProductPages } from '../agents/pricingAgent.js';
import { fetchJsonLdOffers } from '../scrapers/fetchPage.js';
import type { ParsedOffer } from '../scrapers/jsonld-offer.js';
import { assertPublicHttpUrl, matchesStoreWebsite } from '../scrapers/ssrf-guard.js';
import { rankReviewedProducts, type PricedProduct } from './reviewRanking.js';

export type QuoteMode = 'url' | 'agent';
export type QuoteItemResult = { quote: LineItemQuote; mode: QuoteMode; alternatives?: LineItemQuote[] };
type QuoteStore = Pick<StoreRef, 'name' | 'websiteUrl'>;

type QuoteOptions = {
  productUrl?: string;
  tier?: BudgetTier;
  region?: FurnishingProject['region'];
  usdCrcRate?: number;
  stores?: QuoteStore[];
  onActivity?: (activity: QuoteActivity) => void;
};

export function quoteActivity(item: CatalogItem, stage: QuoteActivity['stage'], detail: string, tier: BudgetTier = DEFAULT_BUDGET_TIER): QuoteActivity {
  return { itemId: item.id, itemName: purchaseName(item, tier), stage, detail, tier };
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
  if (!Number.isFinite(amount) || Math.round(amount) <= 0 || !productPageUrl(offer.productUrl)) return null;
  const stock = offer.availability?.split('/').at(-1)?.toLowerCase();
  return {
    id: randomUUID(),
    ...storeForUrl(assertPublicHttpUrl(offer.productUrl)),
    productUrl: offer.productUrl,
    basis: 'published',
    title: offer.title, brand: offer.brand?.trim().slice(0,120), modelIds: offer.modelIds,
    priceCrc: Math.round(amount),
    priceUsd: offer.priceUsd,
    availability: stock === 'instock' ? 'in_stock'
      : stock && ['outofstock', 'soldout', 'discontinued'].includes(stock) ? 'out_of_stock' : 'unknown',
    capturedAt: new Date().toISOString(),
    review: offer.review,
  };
}

async function quoteFromPage(url: string, usdCrcRate?: number, website?: URL, item?: CatalogItem, tier: BudgetTier = DEFAULT_BUDGET_TIER) {
  const address = assertPublicHttpUrl(url);
  const offers = item && address.origin === 'https://cr.siman.com' && (!website || matchesStoreWebsite(address, website))
    ? await findSimanOffers(item, new URL(address.origin), address)
    : await fetchJsonLdOffers(address.href, website);
  const products = offers.flatMap((offer): PricedProduct[] => {
    for (const axis of ['w', 'h', 'd'] as const) {
      const maximum = item?.dimsM?.[axis];
      const measurement = offer.dimensions?.[axis];
      if (maximum !== undefined && (measurement === undefined || measurement - maximum > 1e-6)) return [];
    }
    if (item && !matchesPurchase(item, offer.title, offer.description, tier)) return [];
    if (item && isPurchaseSet(item) && !/\b(juego|set|kit|pack|paquete|combo|vajilla|bateria|piezas|pzas|pzs|pcs)\b/.test(normalizeSearch(offer.title))) return [];
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

type SearchCounts = { queries: number; errors: number; pages: number; unreadable: number };

async function verifyCandidates(candidates: string[], item: CatalogItem, counts: SearchCounts, tier: BudgetTier, usdCrcRate?: number, website?: URL) {
  const results = await Promise.all([...new Set(candidates)].map(async (candidate) => {
    const url = publicUrl(candidate);
    if (!url || !productPageUrl(url.href) || url.protocol !== 'https:' || (website && !matchesStoreWebsite(url, website))) return;
    counts.pages++;
    try {
      const product = await quoteFromPage(url.href, usdCrcRate, website, item, tier);
      if (product && product.quote.availability !== 'out_of_stock') return product;
    } catch { counts.unreadable++; }
  }));
  return results.filter((p): p is PricedProduct => !!p);
}

export class UnselectedQuote extends Error {
  readonly search: QuoteSearch;
  constructor(detail: string, offers: LineItemQuote[] = []) {
    super(detail);
    this.search = { checkedAt: new Date().toISOString(), detail, offers };
  }
}

export async function quoteItemOnDemand(item: CatalogItem, options: QuoteOptions = {}): Promise<QuoteItemResult> {
  const tier = options.tier ?? DEFAULT_BUDGET_TIER;
  const current = quotesForTier(item, tier);
  const report = (stage: QuoteActivity['stage'], detail: string) => options.onActivity?.(quoteActivity(item, stage, detail, tier));
  if (options.productUrl) {
    report('verifying', 'Leyendo el precio publicado en el enlace de la tienda.');
    const product = await quoteFromPage(options.productUrl, options.usdCrcRate, undefined, item, tier).catch(() => undefined);
    if (product && product.quote.availability !== 'out_of_stock') return { quote: { ...product.quote, tier }, mode: 'url' };
    throw new Error('La página indicada no proporcionó una oferta disponible que coincida con el artículo y sus medidas.');
  }

  const offline = offlineReason();
  const counts: SearchCounts = { queries: 0, errors: 0, pages: 0, unreadable: 0 };
  const known = [...current, ...(item.lastSearch?.[tier]?.offers ?? [])]
    .filter((q) => q.basis === 'published').map((q) => q.productUrl);
  const stores = options.stores ?? [];
  const products: PricedProduct[] = [];
  let checked = 0;
  async function discover(website?: URL) {
    if (offline) return [];
    counts.queries++;
    try { return await findProductPages(item, options.region, website, tier); }
    catch { counts.errors++; return []; }
  }
  report(offline ? 'verifying' : 'searching', offline ? 'Comprobando las fuentes directas disponibles.' : 'Buscando fichas de producto en las tiendas.');
  const jobs = stores.values();
  const covered = new Set<string>();
  async function worker() {
    for (const store of jobs) {
      const website = publicUrl(store.websiteUrl);
      let found: PricedProduct[] = [];
      if (website) {
        const saved = known.filter((raw) => { const url = publicUrl(raw); return url && matchesStoreWebsite(url, website); });
        saved.forEach((raw) => covered.add(raw));
        const links = await discover(website);
        found = await verifyCandidates([...links, ...saved], item, counts, tier, options.usdCrcRate, website);
        if (website.origin === 'https://cr.siman.com' && tier === DEFAULT_BUDGET_TIER && simanTerm(item)) {
          counts.queries++;
          try {
            const published = await findSimanOffers(item, website);
            for (const offer of published) {
              if (!matchesPurchase(item, offer.title, offer.description, tier)) continue;
              const quote = offerToQuote(offer, options.usdCrcRate);
              if (quote) found.push({ quote, modelIds: offer.modelIds ?? [] });
            }
          } catch { counts.errors++; }
        }
        for (const p of found) products.push({ ...p, quote: { ...p.quote, storeName: store.name, storeUrl: website.href } });
      }
      checked++;
      report(offline ? 'verifying' : 'searching', checked + '/' + stores.length + ' tiendas revisadas · ' + store.name
        + (found.length ? ' · Precio verificado' : ' · Sin oferta utilizable'));
    }
  }
  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, stores.length) }, worker));
  const remaining = known.filter((url) => !covered.has(url));
  if (!stores.length) remaining.push(...await discover());
  products.push(...await verifyCandidates(remaining, item, counts, tier, options.usdCrcRate));
  const eligible = products.filter((p) => productProvenance(p.quote.title, p.quote.brand) !== 'replica');
  const result = await rankReviewedProducts(eligible, () => report('verifying', 'Comparando precio y reseñas del modelo exacto.'), !offline);
  const alternatives = result.alternatives.map((entry) => ({ ...entry, tier }));
  if (result.recommendation) return { quote: { ...result.recommendation, tier }, alternatives, mode: 'agent' };

  const previous = current.length ? ' Se conservó la cotización anterior.' : ' Pendiente de cotizar.';
  if (alternatives.length) {
    const inStock = alternatives.filter((entry) => entry.availability === 'in_stock');
    const chosen = (inStock.length ? inStock : alternatives)[0]!;
    return { quote: chosen, alternatives: alternatives.filter((entry) => entry !== chosen), mode: 'agent' };
  }
  const summary = [];
  if (eligible.length !== products.length) summary.push('Se excluyeron las fichas anunciadas como réplicas.');
  if (offline) summary.push(offline);
  if (counts.queries) summary.push('Consultas realizadas: ' + counts.queries + '.');
  if (counts.errors) summary.push('Consultas que fallaron: ' + counts.errors + '.');
  if (counts.pages) {
    summary.push('Se intentó verificar ' + counts.pages + (counts.pages === 1 ? ' enlace directo.' : ' enlaces directos.'));
    if (counts.unreadable) summary.push('Páginas que no se pudieron leer: ' + counts.unreadable + '.');
    if (counts.pages > counts.unreadable) summary.push('Las fichas leídas no confirmaron un precio disponible con las especificaciones solicitadas.');
  } else summary.push('No se obtuvo una ficha de producto con un precio compatible.');
  throw new UnselectedQuote(summary.join(' ') + previous);
}

export function recordSearch(item: CatalogItem, tier: BudgetTier, search: QuoteSearch): CatalogItem {
  return { ...item, lastSearch: { ...item.lastSearch, [tier]: search } };
}

export function attachQuoteToProject(project: FurnishingProject, itemId: string, quote: LineItemQuote, alternatives: LineItemQuote[] = []): FurnishingProject {
  return { ...project, items: project.items.map((item) => item.id === itemId ? replaceTierQuotes(item, quoteTier(quote), [quote, ...alternatives]) : item) };
}

export function replaceTierQuotes(item: CatalogItem, tier: BudgetTier, quotes: LineItemQuote[]): CatalogItem {
  const lastSearch = { ...item.lastSearch };
  delete lastSearch[tier];
  return { ...item, lastSearch, quotes: [...item.quotes.filter((quote) => quoteTier(quote) !== tier), ...quotes] };
}

export async function quoteProjectItems(project: FurnishingProject, onActivity?: (activity: QuoteActivity) => void, stores?: QuoteStore[],
  scope: 'pending' | 'all' = 'pending'): Promise<{
  project: FurnishingProject;
  quoted: string[];
  saved: BudgetQuote[];
  failed: (BudgetQuote & { reason: string; search?: QuoteSearch })[];
}> {
  const pending = budgetQuoteJobs(project, scope);
  const jobs = pending.entries();
  const results: (BudgetQuote & ({ quotes: LineItemQuote[] } | { reason: string; search?: QuoteSearch }))[] = [];
  for (const { item, tier } of pending) onActivity?.(quoteActivity(item, 'queued', 'En espera de cotización.', tier));

  async function worker() {
    for (const [index, { item, tier }] of jobs) {
      try {
        const { quote, alternatives = [] } = await quoteItemOnDemand(item, { region: project.region, usdCrcRate: project.usdCrcRate, onActivity, stores, tier });
        results[index] = { itemId: item.id, tier, quotes: [quote, ...alternatives] };
        onActivity?.(quoteActivity(item, 'saving', 'Precio verificado en ' + quote.storeName + '.', tier));
      } catch (error) {
        const reason = error instanceof Error ? error.message : 'No se pudo cotizar el artículo.';
        results[index] = { itemId: item.id, tier, reason, search: error instanceof UnselectedQuote ? error.search : undefined };
        onActivity?.({ ...quoteActivity(item, 'failed', reason, tier), search: error instanceof UnselectedQuote ? error.search : undefined });
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(QUOTE_CONCURRENCY, pending.length) }, worker));
  const items = new Map(project.items.map((item) => [item.id, item]));
  const saved: BudgetQuote[] = [];
  const failed: (BudgetQuote & { reason: string; search?: QuoteSearch })[] = [];
  for (const result of results) {
    if ('quotes' in result) {
      const { itemId, tier, quotes } = result;
      items.set(itemId, replaceTierQuotes(items.get(itemId)!, tier, quotes));
      saved.push({ itemId, tier });
    }
    else {
      if (result.search) items.set(result.itemId, recordSearch(items.get(result.itemId)!, result.tier, result.search));
      failed.push(result);
    }
  }
  return { project: { ...project, items: [...items.values()] }, quoted: [...new Set(saved.map(({ itemId }) => itemId))], saved, failed };
}
