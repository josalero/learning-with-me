import { load } from 'cheerio';
import { z } from 'zod';
import { normalizeSearch } from '../../shared/search.js';
import type { CatalogItem } from '../../shared/types.js';
import { fetchProductPage } from './fetchPage.js';
import type { ParsedOffer } from './jsonld-offer.js';

const product = z.object({
  brand: z.string().optional(), productName: z.string(), link: z.string().url(), linkText: z.string(), description: z.string(),
  Modelo: z.array(z.string()).optional(), Dimensiones: z.array(z.string()).optional(),
  items: z.array(z.object({ itemId: z.string().optional(), name: z.string().optional(), ean: z.string().optional(), sellers: z.array(z.object({
    commertialOffer: z.object({ Price: z.number(), IsAvailable: z.boolean() }),
  })) })),
});

export function simanTerm(item: CatalogItem) {
  const name = normalizeSearch(item.name).split(' (')[0];
  if (name === 'juego de ollas con tapa') return 'juego de ollas';
  if (name === 'microondas' || name === 'refrigeradora') return name;
}

export function simanCandidates(raw: unknown, item: CatalogItem, website: URL, skuId?: string): ParsedOffer[] {
  const term = simanTerm(item);
  if (!Array.isArray(raw)) return [];
  const offers: ParsedOffer[] = [];
  for (const entry of raw) {
    const parsed = product.safeParse(entry);
    if (!parsed.success) continue;
    const p = parsed.data;
    const url = new URL(p.link);
    if (url.origin !== website.origin || url.pathname !== '/' + p.linkText + '/p') continue;
    const sku = skuId ? p.items.find(i => i.itemId === skuId) : p.items.length === 1 ? p.items[0] : undefined;
    if (!sku) continue;
    if (skuId) url.searchParams.set('skuId', skuId);
    const name = normalizeSearch(p.productName);
    if (term && !name.startsWith(term) && !(term === 'microondas' && /^horno (de )?microondas\b/.test(name))) continue;
    if (term === 'juego de ollas' && !/\btapas?\b/i.test(p.description)) continue;
    const measurement = p.Dimensiones?.[0]?.match(/^Al(?:to|\.)\s*([\d.,]+)\s*x\s*An(?:cho|\.)\s*([\d.,]+)\s*x\s*Prof(?:undidad|\.)\s*([\d.,]+)\s*cm\b/i);
    const cm = (v?: string) => Number(v?.replace(',', '.')) / 100;
    const dimensions = measurement ? { h: cm(measurement[1]), w: cm(measurement[2]), d: cm(measurement[3]) } : {};
    if (Object.entries(item.dimsM ?? {}).some(([axis, limit]) => {
      const size = dimensions[axis as keyof typeof dimensions];
      return size === undefined || size <= 0 || size > limit;
    })) continue;
    const prices = sku.sellers.map((s) => s.commertialOffer).filter((o) => o.IsAvailable && o.Price > 0);
    if (!prices.length) continue;
    const modelIds = [...(p.Modelo ?? []), sku.ean ?? ''].map((s) => s.toUpperCase().replace(/[^A-Z0-9]/g, ''))
      .filter((s) => s.length >= 5 && /\d/.test(s));
    const title = skuId && sku.name ? p.productName + ' · ' + sku.name : p.productName;
    offers.push({ title, description: p.description.slice(0,4000), brand: p.brand, productUrl: url.href, priceCrc: Math.min(...prices.map((o) => o.Price)), dimensions, modelIds,
      availability: 'https://schema.org/InStock' });
  }
  return offers.sort((a, b) => a.priceCrc! - b.priceCrc!);
}

async function accessiblePage(offer: ParsedOffer, website: URL) {
  try {
    const page = await fetchProductPage(offer.productUrl, website);
    const $ = load(page.html);
    const canonical = $('link[rel="canonical"]').attr('href');
    const address = new URL(offer.productUrl);
    const skuId = address.searchParams.get('skuId');
    if (!canonical || new URL(canonical, page.url).href !== address.origin + address.pathname || page.url !== offer.productUrl) return;
    if (skuId) {
      const selected = $('script[type="application/ld+json"]').toArray().some(el => {
        try {
          const product = JSON.parse($(el).text());
          return product['@type'] === 'Product' && String(product.sku).replace(/^0+/, '') === skuId;
        } catch { return false; }
      });
      if (!selected) return;
    }
    return offer;
  } catch { /* The link must open the same product. */ }
}

export async function findSimanOffers(item: CatalogItem, website: URL, exactUrl?: URL) {
  if (website.origin !== 'https://cr.siman.com') return [];
  const term = simanTerm(item);
  if (!exactUrl && !term) return [];
  if (exactUrl && (exactUrl.origin !== website.origin || !/^\/[a-z0-9-]+\/p$/i.test(exactUrl.pathname))) return [];
  const skuId = exactUrl?.searchParams.get('skuId') ?? undefined;
  if (skuId && !/^\d+$/.test(skuId)) return [];
  const url = new URL('/api/catalog_system/pub/products/search/' + (exactUrl ? exactUrl.pathname.slice(1) : encodeURIComponent(term!)), website);
  if (!exactUrl) url.search = '_from=0&_to=11';
  const result = await fetchProductPage(url.href, website);
  const candidates = simanCandidates(JSON.parse(result.html), item, website, skuId).filter((q) => !exactUrl || q.productUrl === exactUrl.href).slice(0, 3);
  const verified = await Promise.all(candidates.map((offer) => accessiblePage(offer, website)));
  return verified.filter((offer): offer is ParsedOffer => !!offer);
}
