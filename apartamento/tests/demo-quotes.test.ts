import { describe, expect, it } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { applyDemoQuotes, repairLegacyQuoteLinks } from '../src/domain/demo-quotes.js';

describe('applyDemoQuotes', () => {
  it('quotes most catalog items and keeps only intentional gaps', () => {
    const p = applyDemoQuotes(buildNatarundiProject('ws', 'proj'));
    const unquoted = p.items.filter((i) => i.quotes.length === 0);
    expect(p.items.length).toBe(20);
    expect(unquoted).toHaveLength(3);
    expect(unquoted.map((i) => i.id).sort()).toEqual(['i-br3-desk', 'i-dryer', 'i-freight']);
    for (const q of p.items.flatMap((i) => i.quotes)) {
      expect(q.storeName.length).toBeGreaterThan(0);
      expect(q.productUrl).toMatch(/^https:\/\//);
    }
  });

  it('repairs old catalog links without changing the original price, date or published links', () => {
    const item = applyDemoQuotes(buildNatarundiProject('ws', 'proj')).items.find(({ id }) => id === 'i-sofa')!;
    const current = item.quotes[0]!;
    const oldQuote = { ...current, basis: undefined, storeUrl: 'https://www.siman.com/', productUrl: 'https://www.siman.com/buscar?q=sofa+cama' };
    const publishedQuote = { ...oldQuote, basis: 'published' as const };
    const [repaired] = repairLegacyQuoteLinks([{ ...item, quotes: [oldQuote, publishedQuote] }]);
    expect(repaired?.quotes).toEqual([current, publishedQuote]);
  });
});
