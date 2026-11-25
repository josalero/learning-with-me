import type { LineItemQuote, ProductReview } from '../../shared/types.js';
import { findReviewPages } from '../agents/pricingAgent.js';
import { fetchProductReviews } from '../scrapers/fetchPage.js';
import { assertPublicHttpUrl } from '../scrapers/ssrf-guard.js';

export type PricedProduct = { quote: LineItemQuote; modelIds: string[] };

function reviewScore(review: ProductReview) {
  return (review.rating * review.count + 3.5 * 10) / (review.count + 10);
}

function relativePrice(a: LineItemQuote, b: LineItemQuote) {
  return a.priceCrc - b.priceCrc || a.storeName.localeCompare(b.storeName, 'es') || a.productUrl.localeCompare(b.productUrl);
}

export async function rankReviewedProducts(products: PricedProduct[], onReview: () => void): Promise<LineItemQuote[]> {
  const unique = [...new Map(products.map((product) => [product.quote.productUrl, product])).values()];
  if (!unique.length) return [];
  onReview();
  const byModel = new Map<string, ProductReview>();
  function remember(modelIds: string[], review: ProductReview) {
    for (const id of modelIds) {
      if ((byModel.get(id)?.count ?? 0) < review.count) byModel.set(id, review);
    }
  }
  for (const { quote, modelIds } of unique) {
    if (quote.review) remember(modelIds, quote.review);
  }
  function evidence({ quote, modelIds }: PricedProduct) {
    return [quote.review, ...modelIds.map((id) => byModel.get(id))]
      .reduce<ProductReview | undefined>((best, review) => review && review.count > (best?.count ?? 0) ? review : best, undefined);
  }

  const seen = new Set<string>();
  const missing = unique.flatMap((product, index) => {
    if ((evidence(product)?.count ?? 0) >= 3 || !product.modelIds.length || product.modelIds.some((id) => seen.has(id))) return [];
    for (const id of product.modelIds) seen.add(id);
    return [{ index, title: product.quote.title, modelIds: product.modelIds }];
  }).slice(0, 8);
  if (missing.length) {
    const pages = await findReviewPages(missing).catch(() => new Map<number, string>());
    await Promise.all(missing.map(async ({ index, modelIds }) => {
      const source = pages.get(index);
      if (!source) return;
      try {
        const url = assertPublicHttpUrl(source);
        if (url.protocol !== 'https:' || url.port || url.username || url.password) return;
        for (const result of await fetchProductReviews(url.href)) {
          const titleTokens: string[] = result.title.toUpperCase().match(/[A-Z0-9]+/g) ?? [];
          const matchingIds = modelIds.filter((id) => result.modelIds?.includes(id) || titleTokens.includes(id));
          if (result.review) remember(matchingIds, result.review);
        }
      } catch {
        // If the source cannot verify this model, the candidate keeps its original evidence.
      }
    }));
  }

  const quotes = unique.map((product) => ({ ...product.quote, review: evidence(product) }));
  const qualified = quotes.filter(({ review }) => review && review.count >= 3 && review.rating >= 4);
  if (!qualified.length) return [];
  const inStock = qualified.filter(({ availability }) => availability === 'in_stock');
  const available = inStock.length ? inStock : qualified;
  const bestScore = Math.max(...available.map(({ review }) => reviewScore(review!)));
  const best = available.filter(({ review }) => reviewScore(review!) >= bestScore - 0.15).sort(relativePrice)[0]!;
  const alternatives = quotes.filter((quote) => quote !== best).sort((a, b) => {
    const rating = (quote: LineItemQuote) => quote.review ? reviewScore(quote.review) : 0;
    return rating(b) - rating(a) || relativePrice(a, b);
  });
  return [best, ...alternatives];
}
