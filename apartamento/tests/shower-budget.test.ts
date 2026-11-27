import { expect, it } from 'vitest';
import { applyNatarundiOpeningPrices } from '../src/domain/natarundi-opening.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';
import { matchesPurchase } from '../src/domain/product-match.js';
import { quotesForTier } from '../src/shared/budgetProfiles.js';
import { itemSpecifications } from '../src/shared/itemSpecifications.js';
import { productPageUrl } from '../src/shared/quoteBasis.js';
import { purchaseName } from '../src/shared/showerEnclosure.js';
import { BUDGET_TIERS } from '../src/shared/types.js';

const base = itemizeCatalogBundles(buildNatarundiProject('w', 'p', 520));
const opened = applyNatarundiOpeningPrices(base);
const enclosure = opened.items.find(i => i.id === 'i-bath-enclosure')!;

it('loads one physical purchase per line with a real price and link and leaves the bespoke freight pending', () => {
  const physical = opened.items.filter(i => i.category !== 'logistica');
  expect(physical).toHaveLength(27);
  for (const item of physical) {
    const quote = quotesForTier(item, 'balanced')[0]!;
    expect(quote.priceCrc).toBeGreaterThan(0);
    expect(productPageUrl(quote.productUrl)).toBe(quote.productUrl);
  }
  expect(opened.items.find(i => i.id === 'i-freight')?.quotes).toEqual([]);
  expect(applyNatarundiOpeningPrices(opened)).toEqual(opened);
});

it('has planned specifications for every item and quotes exactly one integrated electrical laundry tower per tier', () => {
  for (const item of opened.items) {
    for (const tier of BUDGET_TIERS) expect(itemSpecifications(item, tier).length).toBeGreaterThan(1);
  }
  const [tower, ...other] = opened.items.filter(({ zoneId }) => zoneId === 'z-laundry');
  expect(other).toHaveLength(0);
  expect(tower).toMatchObject({ id: 'i-laundry-tower', qty: 1 });
  for (const [index, tier] of BUDGET_TIERS.entries()) {
    const [quote, ...others] = quotesForTier(tower!, tier);
    expect(others).toHaveLength(0);
    expect(quote).toMatchObject({ priceCrc: [589000, 659000, 1213338][index], tier });
    expect(matchesPurchase(tower!, quote!.title, quote!.description, tier)).toBe(true);
  }
  for (const title of ['Lavadora eléctrica individual 24 kg', 'Secadora eléctrica 20 kg', 'Kit para torre de lavado 220 V',
    'Centro de lavado a gas de 20 kg', 'Lavasecadora eléctrica de un tambor']) {
    expect(matchesPurchase(tower!, title)).toBe(false);
  }
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
