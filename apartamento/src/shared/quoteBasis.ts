import type { LineItemQuote } from './types.js';

export function quoteBasisLabel(quote: LineItemQuote) {
  if (quote.basis === 'published') return quote.review && quote.review.rating >= 4 && quote.review.count >= 3 ? 'Precio con buenas reseñas' : 'Precio publicado';
  if (quote.basis === 'catalog') return 'Catálogo de referencia';
  if (quote.basis === 'estimate' || quote.source === 'agent_estimate') return 'Estimación';
  if (quote.source === 'manual') return 'Referencia manual';
}

export function productPageUrl(raw: string) {
  try {
    const url = new URL(raw);
    if (!/^[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z][a-z0-9-]*$/i.test(url.hostname) || url.port || !/^\/[^/?#]/.test(url.pathname)
      || /^\/[a-z]{2}(?:-[a-z]{2})?\/?$/i.test(url.pathname)
      || /^\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?(?:collections|category|categoria|categories|product-category|catalogsearch|buscar|search|c)(?:\/|$)/i.test(url.pathname)) return;
    if ((url.protocol === 'https:' || url.protocol === 'http:') && !url.username && !url.password) {
      return url.href;
    }
  } catch {
    // Historical notes and estimates can have no web address.
  }
}

export function quoteLink(quote: LineItemQuote) {
  if (quote.basis !== 'published') return;
  const url = productPageUrl(quote.productUrl);
  if (url) return { url, label: 'Abrir ficha del producto' };
}
