import { DEFAULT_BUDGET_TIER, quantityForTier, quoteTier } from './budgetProfiles.js';
import type { BudgetTier, CatalogItem } from './types.js';

export function projectStats(items: CatalogItem[], tier: BudgetTier = DEFAULT_BUDGET_TIER) {
  let quotedCount = 0;
  let totalCrc = 0;
  for (const item of items) {
    const quote = item.quotes.find((candidate) => quoteTier(candidate) === tier);
    if (!quote) continue;
    quotedCount++;
    totalCrc += quote.priceCrc * quantityForTier(item, tier);
  }
  return { itemCount: items.length, quotedCount, totalCrc };
}
