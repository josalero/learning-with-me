import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { attachQuoteToProject, quoteItemOnDemand } from '../src/server/services/quoteOnDemand.js';
import { buildCatalogQuoteForItem } from '../src/domain/demo-quotes.js';

describe('quoteItemOnDemand', () => {
  beforeEach(() => vi.stubEnv('AI_OFFLINE_STUB', '1'));
  afterEach(() => vi.unstubAllEnvs());
  it('returns catalog quote for known item', async () => {
    const project = buildNatarundiProject('ws', 'p');
    const item = project.items.find((i) => i.id === 'i-micro')!;
    const { quote, mode } = await quoteItemOnDemand(item);
    expect(mode).toBe('catalog');
    expect(quote.priceCrc).toBeGreaterThan(0);
  });

  it('returns estimate for freight on demand', async () => {
    const project = buildNatarundiProject('ws', 'p');
    const item = project.items.find((i) => i.id === 'i-freight')!;
    const { mode } = await quoteItemOnDemand(item, { allowEstimate: true });
    expect(mode).toBe('estimate');
    expect(buildCatalogQuoteForItem('i-freight', true)?.storeName).toBeTruthy();
  });

  it('attachQuoteToProject replaces line on item', () => {
    const project = buildNatarundiProject('ws', 'p');
    const q = buildCatalogQuoteForItem('i-tv', false)!;
    const updated = attachQuoteToProject(project, 'i-tv', q);
    expect(updated.items.find((i) => i.id === 'i-tv')?.quotes[0]?.priceCrc).toBe(q.priceCrc);
  });
});