import { BUDGET_TIERS, type BudgetTier, type CatalogItem, type FurnishingProject, type LineItemQuote } from './types.js';

export const DEFAULT_BUDGET_TIER: BudgetTier = 'balanced';

export const budgetProfiles = {
  economy: { label: 'Económica', description: 'Funcional y confiable, con prioridad al precio.' },
  balanced: { label: 'Gama media', description: 'Equilibrio entre calidad, duración y precio.' },
  premium: { label: 'Alta gama sin lujo', description: 'Mejores materiales, desempeño o garantía a un precio justificable.' },
} satisfies Record<BudgetTier, { label: string; description: string }>;

export function selectedBudgetTiers(project: Pick<FurnishingProject, 'budgetTiers'>) {
  const selected = BUDGET_TIERS.filter((tier) => project.budgetTiers?.includes(tier));
  return selected.length ? selected : [DEFAULT_BUDGET_TIER];
}

export function primaryBudgetTier(project: Pick<FurnishingProject, 'budgetTiers'>): BudgetTier {
  const tiers = selectedBudgetTiers(project);
  return tiers.includes(DEFAULT_BUDGET_TIER) ? DEFAULT_BUDGET_TIER : tiers[0]!;
}

export function quoteTier(quote: LineItemQuote): BudgetTier {
  return quote.tier ?? DEFAULT_BUDGET_TIER;
}

export function quotesForTier(item: CatalogItem, tier: BudgetTier) {
  return item.quotes.filter((quote) => quoteTier(quote) === tier);
}

export function budgetQuoteJobs(project: FurnishingProject, scope: 'pending' | 'all' = 'pending') {
  const tiers = selectedBudgetTiers(project);
  return project.items.flatMap((item) => tiers
    .filter((tier) => scope === 'all' || !item.quotes.some((quote) => quoteTier(quote) === tier))
    .map((tier) => ({ item, tier })));
}
