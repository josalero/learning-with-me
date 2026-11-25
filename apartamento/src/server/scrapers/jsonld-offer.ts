import * as cheerio from 'cheerio';

export type ParsedOffer = {
  title: string;
  priceCrc?: number;
  priceUsd?: number;
  currency?: string;
  availability?: string;
  productUrl: string;
};

function readOffers(node: unknown, acc: ParsedOffer[], pageUrl: string, title: string): void {
  if (Array.isArray(node)) {
    node.forEach((value) => readOffers(value, acc, pageUrl, title));
    return;
  }
  if (!node || typeof node !== 'object') return;
  const rec = node as Record<string, unknown>;
  if (rec['@type'] === 'AggregateOffer' || rec['@type'] === 'Offer') {
    const low = rec.lowPrice ?? rec.price;
    const currency = typeof rec.priceCurrency === 'string' ? rec.priceCurrency.toUpperCase() : 'CRC';
    const num = typeof low === 'string' ? Number(low) : typeof low === 'number' ? low : undefined;
    if (num && Number.isFinite(num)) {
      acc.push({
        title,
        priceCrc: currency === 'CRC' ? num : undefined,
        priceUsd: currency === 'USD' ? num : undefined,
        currency,
        availability: typeof rec.availability === 'string' ? rec.availability : undefined,
        productUrl: pageUrl,
      });
    }
  }
  if (rec['@type'] === 'AggregateOffer') readOffers(rec.offers, acc, pageUrl, title);
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

export function parseJsonLdOffers(html: string, pageUrl: string): ParsedOffer[] {
  const $ = cheerio.load(html);
  const offers: ParsedOffer[] = [];
  const pageTitle = $('title').first().text().trim() || pageUrl;
  $('script[type="application/ld+json"]').each((_, el) => {
    const raw = $(el).text();
    try {
      for (const product of productNodes(JSON.parse(raw))) {
        const title = typeof product.name === 'string' ? product.name : pageTitle;
        readOffers(product.offers, offers, pageUrl, title);
      }
    } catch {
      /* skip malformed JSON-LD */
    }
  });
  if (offers.length && new URL(pageUrl).hostname === 'cr.epaenlinea.com') {
    // EPA's structured price excludes tax; the product page displays the total in colones.
    const displayedPrice = $('main [data-epa-price-type].tax-inclusive-price').first().text().trim();
    const crc = displayedPrice.match(/^[₡¢]\s*([\d.]+)(?:,\d{2})?$/)?.[1];
    return crc ? [{ ...offers[0]!, priceCrc: Number(crc.replaceAll('.', '')), priceUsd: undefined, currency: 'CRC' }] : [];
  }
  return offers;
}
