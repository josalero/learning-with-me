import { DEFAULT_BUDGET_TIER, quoteTier } from './budgetProfiles.js';
import type { BudgetTier, CatalogItem } from './types.js';

export function projectStats(items: CatalogItem[], tier: BudgetTier = DEFAULT_BUDGET_TIER) {
  let quotedCount = 0;
  let totalCrc = 0;
  for (const { qty, quotes } of items) {
    const quote = quotes.find((candidate) => quoteTier(candidate) === tier);
    if (!quote) continue;
    quotedCount++;
    totalCrc += quote.priceCrc * qty;
  }
  return { itemCount: items.length, quotedCount, totalCrc };
}
