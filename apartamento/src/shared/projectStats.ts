import type { CatalogItem } from './types.js';

export function projectStats(items: CatalogItem[]) {
  let quotedCount = 0;
  let totalCrc = 0;
  for (const { qty, quotes } of items) {
    const quote = quotes[0];
    if (!quote) continue;
    quotedCount++;
    totalCrc += quote.priceCrc * qty;
  }
  return { itemCount: items.length, quotedCount, totalCrc };
}
