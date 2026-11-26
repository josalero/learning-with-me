import { expect, it } from 'vitest';
import { applyNatarundiOpeningPrices } from '../src/domain/natarundi-opening.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';
import { matchesPurchase } from '../src/domain/product-match.js';
import { quotesForTier } from '../src/shared/budgetProfiles.js';
import { productPageUrl } from '../src/shared/quoteBasis.js';
import { purchaseName } from '../src/shared/showerEnclosure.js';
import { BUDGET_TIERS } from '../src/shared/types.js';

const base = itemizeCatalogBundles(buildNatarundiProject('w', 'p', 520));
const opened = applyNatarundiOpeningPrices(base);
const enclosure = opened.items.find(i => i.id === 'i-bath-enclosure')!;

it('loads one physical purchase per line with a real price and link and leaves the bespoke freight pending', () => {
  const physical = opened.items.filter(i => i.category !== 'logistica');
  expect(physical).toHaveLength(28);
  for (const item of physical) {
    const quote = quotesForTier(item, 'balanced')[0]!;
    expect(quote.priceCrc).toBeGreaterThan(0);
    expect(productPageUrl(quote.productUrl)).toBe(quote.productUrl);
  }
  expect(opened.items.find(i => i.id === 'i-freight')?.quotes).toEqual([]);
  expect(applyNatarundiOpeningPrices(opened)).toEqual(opened);
});

it('budgets a single enclosure per shower and keeps the published curtain and two glass door prices in their own tiers', () => {
  expect(enclosure.qty).toBe(2);
  const prices = BUDGET_TIERS.map(tier => quotesForTier(enclosure, tier)[0]!);
  expect(new Set(prices.map(q => q.productUrl)).size).toBe(3);
  for (const [index, tier] of BUDGET_TIERS.entries()) {
    expect(purchaseName(enclosure, tier)).toContain(index ? 'vidrio' : 'Cortina');
    expect(matchesPurchase(enclosure, prices[index]!.title, '', tier)).toBe(true);
  }
  expect(matchesPurchase(enclosure, prices[0]!.title, '', 'premium')).toBe(false);
  expect(matchesPurchase(enclosure, prices[1]!.title, '', 'premium')).toBe(false);
  expect(matchesPurchase(enclosure, 'Herrajes para puerta de ducha de vidrio templado 8 mm', '', 'premium')).toBe(false);
  const explicit = { ...enclosure, id:'custom', name:'Mampara de ducha de vidrio' };
  expect(matchesPurchase(explicit, prices[1]!.title, '', 'economy')).toBe(true);
  expect(matchesPurchase(explicit, prices[0]!.title, '', 'economy')).toBe(false);
});

it('preserves a changed purchase specification and an existing user price', () => {
  const fridge = base.items.find(i => i.id === 'i-refri')!;
  const glass = quotesForTier(enclosure, 'balanced')[0]!;
  const custom = { ...base, items:[{ ...fridge, dimsM:{w:.45} }, { ...enclosure, quotes:[{ ...glass, priceCrc:222000 }] }] };
  const result = applyNatarundiOpeningPrices(custom);
  expect(result.items[0]?.quotes).toEqual([]);
  expect(quotesForTier(result.items[1]!, 'balanced')[0]?.priceCrc).toBe(222000);
});
