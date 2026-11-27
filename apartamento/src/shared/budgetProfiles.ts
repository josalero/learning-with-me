import { BUDGET_TIERS, type BudgetTier, type CatalogItem, type FurnishingProject, type LineItemQuote } from './types.js';

export const DEFAULT_BUDGET_TIER: BudgetTier = 'balanced';

export const budgetProfiles = {
  economy: {
    label: 'Gama baja', description: 'Prioriza el precio y cubre las funciones esenciales.',
    quality: 'Materiales sencillos y de fácil reposición.',
    brands: 'Líneas de entrada o genéricos con ficha, precio y respaldo del vendedor.',
  },
  balanced: {
    label: 'Gama media', description: 'Equilibra el precio con la durabilidad para uso frecuente.',
    quality: 'Materiales, acabados y garantía adecuados para el uso diario.',
    brands: 'Modelos con soporte local, disponibilidad de repuestos o buenas valoraciones documentadas.',
  },
  premium: {
    label: 'Gama alta', description: 'Paga por mejoras comprobables en el producto.',
    quality: 'Mejor material, rendimiento o garantía, según la ficha del modelo.',
    brands: 'Líneas superiores de cualquier marca que documenten esa mejora.',
  },
} satisfies Record<BudgetTier, { label: string; description: string; quality: string; brands: string }>;

export function quantityForTier(item: CatalogItem, tier: BudgetTier) {
  return item.quantityByTier?.[tier] ?? item.qty;
}

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

export function budgetQuoteJobs(project: FurnishingProject, scope: 'pending' | 'all' = 'pending', tier?: BudgetTier) {
  const tiers = tier ? [tier] : selectedBudgetTiers(project);
  return project.items.flatMap((item) => tiers
    .filter((tier) => scope === 'all' || !item.quotes.some((quote) => quoteTier(quote) === tier))
    .map((tier) => ({ item, tier })));
}
