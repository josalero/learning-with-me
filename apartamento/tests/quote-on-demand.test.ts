import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { attachQuoteToProject, quoteItemOnDemand } from '../src/server/services/quoteOnDemand.js';
import { buildCatalogQuoteForItem } from '../src/domain/demo-quotes.js';

describe('quoteItemOnDemand', () => {
  beforeEach(() => vi.stubEnv('AI_OFFLINE_STUB', '1'));
  afterEach(() => vi.unstubAllEnvs());
  it('leaves catalog prices and estimates without product pages out of the budget', async () => {
    const project = buildNatarundiProject('ws', 'p');
    for (const id of ['i-micro', 'i-freight']) {
      const item = project.items.find((i) => i.id === id)!;
      await expect(quoteItemOnDemand(item)).rejects.toMatchObject({ search: { offers: [], detail: expect.stringContaining('modo sin IA') } });
    }
  });

  it('distinguishes a missing provider key from offline mode', async () => {
    vi.stubEnv('AI_OFFLINE_STUB', '0');
    vi.stubEnv('OPENROUTER_API_KEY', '');
    const item = buildNatarundiProject('ws', 'p').items[0]!;
    await expect(quoteItemOnDemand(item)).rejects.toThrow('clave de búsqueda de OpenRouter');
  });

  it('attachQuoteToProject replaces line on item', () => {
    const project = buildNatarundiProject('ws', 'p');
    const q = buildCatalogQuoteForItem('i-tv', false)!;
    const updated = attachQuoteToProject(project, 'i-tv', q);
    expect(updated.items.find((i) => i.id === 'i-tv')?.quotes[0]?.priceCrc).toBe(q.priceCrc);
  });
});