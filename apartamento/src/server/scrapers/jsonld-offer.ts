import * as cheerio from 'cheerio';
import type { CatalogItem, ProductReview } from '../../shared/types.js';

export type ParsedOffer = {
  title: string;
  brand?: string;
  description?: string;
  priceCrc?: number;
  priceUsd?: number;
  currency?: string;
  availability?: string;
  productUrl: string;
  review?: ProductReview;
  modelIds?: string[];
  dimensions?: CatalogItem['dimsM'];
};

type ProductDetails = Pick<ParsedOffer, 'title' | 'brand' | 'description' | 'productUrl' | 'review' | 'modelIds' | 'dimensions'>;
const lengthUnits: Record<string, number> = { mtr: 1, m: 1, cmt: .01, cm: .01, mmt: .001, mm: .001, inh: .0254, in: .0254 };

function metres(measure: unknown) {
  if (!measure || typeof measure !== 'object') return;
  const { value, unitCode, unitText } = measure as Record<string, unknown>;
  const multiplier = lengthUnits[String(unitCode ?? unitText).toLowerCase()];
  if (!multiplier) return;
  const result = Number(value) * multiplier;
  return Number.isFinite(result) && result > 0 ? result : undefined;
}

function readOffers(node: unknown, acc: ParsedOffer[], product: ProductDetails): void {
  if (Array.isArray(node)) {
    node.forEach((value) => readOffers(value, acc, product));
    return;
  }
  if (!node || typeof node !== 'object') return;
  const rec = node as Record<string, unknown>;
  if (rec['@type'] === 'AggregateOffer' || rec['@type'] === 'Offer') {
    const aggregate = rec['@type'] === 'AggregateOffer';
    if (aggregate && (rec.offers || (rec.highPrice !== undefined && Number(rec.highPrice) !== Number(rec.lowPrice)))) {
      readOffers(rec.offers, acc, product);
      return;
    }
    let productUrl = product.productUrl;
    if (typeof rec.url === 'string') {
      const offered = new URL(rec.url, productUrl);
      const current = new URL(productUrl);
      if (offered.origin !== current.origin || offered.pathname.replace(/\/$/, '') !== current.pathname.replace(/\/$/, '')) return;
      if (current.searchParams.has('variant') && offered.searchParams.get('variant') !== current.searchParams.get('variant')) return;
      if (offered.searchParams.has('variant')) productUrl = offered.href;
    }
    const low = rec.lowPrice ?? rec.price;
    const currency = typeof rec.priceCurrency === 'string' ? rec.priceCurrency.toUpperCase() : undefined;
    const num = typeof low === 'string' ? Number(low) : typeof low === 'number' ? low : undefined;
    if (num && Number.isFinite(num)) {
      acc.push({
        ...product, productUrl,
        priceCrc: currency === 'CRC' ? num : undefined,
        priceUsd: currency === 'USD' ? num : undefined,
        currency,
        availability: typeof rec.availability === 'string' ? rec.availability : undefined,
      });
    }
  }
}

function productNodes(node: unknown): Record<string, unknown>[] {
  if (Array.isArray(node)) return node.flatMap(productNodes);
  if (!node || typeof node !== 'object') return [];
  const record = node as Record<string, unknown>;
  const types = [record['@type']].flat();
  if (types.includes('Product') || types.includes('ProductGroup')) return [record];
  // ItemList entries are cards on a category page, with prices for different products.
  return productNodes(record['@graph']);
}

function productDetails(product: Record<string, unknown>, pageUrl: string, pageTitle: string): ProductDetails {
  const aggregate = product.aggregateRating as Record<string, unknown> | undefined;
  const rating = Number(aggregate?.ratingValue);
  const best = Number(aggregate?.bestRating ?? 5);
  const worst = Number(aggregate?.worstRating ?? 1);
  const count = Math.max(Number(aggregate?.reviewCount) || 0, Number(aggregate?.ratingCount) || 0);
  const validRating = best > worst && rating >= worst && rating <= best && Number.isSafeInteger(count) && count > 0;
  const scaled = best === 5 && worst === 1 ? rating : 1 + 4 * (rating - worst) / (best - worst);
  const modelIds = new Set<string>();
  for (const field of ['gtin', 'gtin8', 'gtin12', 'gtin13', 'gtin14', 'mpn', 'model']) {
    const value = product[field];
    if (typeof value !== 'string' && typeof value !== 'number') continue;
    const id = String(value).toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (id.length >= 5 && id.length <= 25 && /\d/.test(id)) modelIds.add(id);
  }
  return {
    title: typeof product.name === 'string' ? cheerio.load(product.name.replaceAll('<', '&lt;'), {}, false).text() : pageTitle,
    description: typeof product.description === 'string' ? cheerio.load(product.description, {}, false).text().slice(0,4000) : undefined,
    brand: typeof product.brand === 'string' ? product.brand : product.brand !== null && typeof product.brand === 'object' && typeof (product.brand as { name?: unknown }).name === 'string' ? (product.brand as { name: string }).name : undefined,
    productUrl: pageUrl,
    modelIds: [...modelIds],
    dimensions: { w: metres(product.width), h: metres(product.height), d: metres(product.depth) },
    review: validRating ? { rating: scaled, count, sourceUrl: pageUrl } : undefined,
  };
}

function readProducts($: cheerio.CheerioAPI, pageUrl: string) {
  const products: { details: ProductDetails; offers: unknown }[] = [];
  const pageTitle = $('title').first().text().trim() || pageUrl;
  $('script[type="application/ld+json"]').each((_, el) => {
    const raw = $(el).text();
    try {
      for (const product of productNodes(JSON.parse(raw))) {
        if (typeof product.url === 'string') {
          const declared = new URL(product.url, pageUrl);
          const current = new URL(pageUrl);
          const pathname = (url: URL) => url.pathname.replace(/\/+$/, '');
          if (declared.hostname.replace(/^www\./, '') !== current.hostname.replace(/^www\./, '') || pathname(declared) !== pathname(current)) continue;
        }
        products.push({ details: productDetails(product, pageUrl, pageTitle), offers: product.offers });
      }
    } catch {
      /* skip malformed JSON-LD */
    }
  });
  return products;
}

export function parseProductReviews(html: string, pageUrl: string) {
  return readProducts(cheerio.load(html), pageUrl).flatMap(({ details }) => details.review ? [details] : []);
}

export function parseJsonLdOffers(html: string, pageUrl: string): ParsedOffer[] {
  const $ = cheerio.load(html);
  const offers: ParsedOffer[] = [];
  for (const product of readProducts($, pageUrl)) readOffers(product.offers, offers, product.details);
  if (offers.length && new URL(pageUrl).hostname === 'cr.epaenlinea.com') {
    // EPA's structured price excludes tax; the product page displays the total in colones.
    const displayedPrice = $('main [data-epa-price-type].tax-inclusive-price').first().text().trim();
    const crc = displayedPrice.match(/^[₡¢]\s*([\d.]+)(?:,\d{2})?$/)?.[1];
    return crc ? [{ ...offers[0]!, priceCrc: Number(crc.replaceAll('.', '')), priceUsd: undefined, currency: 'CRC' }] : [];
  }
  return offers;
}
