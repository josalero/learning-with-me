import type { LineItemQuote } from './types.js';

export function quoteBasisLabel(quote: LineItemQuote) {
  if (quote.basis === 'published') return 'Precio publicado';
  if (quote.basis === 'catalog') return 'Catálogo de referencia';
  if (quote.basis === 'estimate' || quote.source === 'agent_estimate') return 'Estimación';
  if (quote.source === 'manual') return 'Referencia manual';
}

export function quoteLink(quote: LineItemQuote) {
  try {
    const url = new URL(quote.productUrl);
    if ((url.protocol === 'https:' || url.protocol === 'http:') && !url.username && !url.password) {
      const label = quote.basis === 'catalog' || quote.basis === 'estimate' || quote.source === 'agent_estimate'
        ? 'Ver opciones' : 'Ver producto';
      return { url: url.href, label };
    }
  } catch {
    // Historical notes and estimates can have no web address.
  }
}
