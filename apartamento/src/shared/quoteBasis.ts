import type { LineItemQuote } from './types.js';

export function quoteBasisLabel(quote: LineItemQuote) {
  if (quote.basis === 'published') return 'Precio publicado';
  if (quote.basis === 'catalog') return 'Catálogo de referencia';
  if (quote.basis === 'estimate' || quote.source === 'agent_estimate') return 'Estimación';
  if (quote.source === 'manual') return 'Referencia manual';
}

export function quoteLink(quote: LineItemQuote) {
  if (quote.basis !== 'published') return;
  try {
    const url = new URL(quote.productUrl);
    if ((url.protocol === 'https:' || url.protocol === 'http:') && !url.username && !url.password) {
      return { url: url.href, label: 'Ver producto' };
    }
  } catch {
    // Historical notes and estimates can have no web address.
  }
}
