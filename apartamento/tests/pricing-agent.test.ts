import { afterEach, expect, it, vi } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { fetchJsonLdOffers } from '../src/server/scrapers/fetchPage.js';
import { quoteItemOnDemand, quoteUnquotedItems } from '../src/server/services/quoteOnDemand.js';
import { QUOTE_CONCURRENCY, type QuoteActivity } from '../src/shared/types.js';

vi.mock('../src/server/scrapers/fetchPage.js', () => ({ fetchJsonLdOffers: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.resetAllMocks(); });

it('quotes pending items in a bounded parallel pool and uses prices published by the store', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '0');
  vi.stubEnv('OPENROUTER_API_KEY', 'test-key');
  const project = buildNatarundiProject('workspace', 'project');
  project.items = project.items.slice(0, 5);
  let active = 0;
  let maximum = 0;
  const fetchAgent = vi.fn(async () => {
    active++;
    maximum = Math.max(maximum, active);
    await new Promise((resolve) => setTimeout(resolve, 10));
    active--;
    return Response.json({ choices: [{ message: { content: 'https://tienda.example.cr/producto' } }] });
  });
  vi.stubGlobal('fetch', fetchAgent);
  vi.mocked(fetchJsonLdOffers).mockResolvedValue([{
    title: 'Producto de prueba', priceCrc: 12345, currency: 'CRC', productUrl: 'https://tienda.example.cr/producto',
  }]);
  const activity: QuoteActivity[] = [];
  const result = await quoteUnquotedItems(project, (event) => activity.push(event));

  expect(maximum).toBe(QUOTE_CONCURRENCY);
  expect(result.quoted).toEqual(project.items.map(({ id }) => id));
  expect(result.failed).toEqual([]);
  expect(result.project.items.every((item) => item.quotes[0]?.priceCrc === 12345)).toBe(true);
  expect(activity.filter(({ stage }) => stage === 'searching')).toHaveLength(5);
  expect(activity.filter(({ stage }) => stage === 'verifying')).toHaveLength(5);
  expect(activity.filter(({ stage }) => stage === 'saving')).toHaveLength(5);
  expect(fetchAgent).toHaveBeenCalledWith('https://openrouter.ai/api/v1/chat/completions', expect.objectContaining({
    body: expect.stringContaining('openrouter:web_search'),
  }));
});

it('checks every registered website and compares only verified products on those sites', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '0');
  vi.stubEnv('OPENROUTER_API_KEY', 'test-key');
  const item = buildNatarundiProject('workspace', 'project').items[0]!;
  const stores = [
    { name: 'Tienda A', websiteUrl: 'https://www.a.example.cr/costarica' },
    { name: 'Tienda B', websiteUrl: 'https://b.example.cr/' },
    { name: 'Tienda C', websiteUrl: 'https://c.example.cr/' },
    { name: 'Tienda D', websiteUrl: 'https://d.example.cr/' },
  ];
  const visits: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (_url: string, options: RequestInit) => {
    const request = JSON.parse(String(options.body));
    const { sitio } = JSON.parse(request.messages[1].content);
    visits.push(sitio);
    const domain = new URL(sitio).hostname.replace(/^www\./, '');
    expect(request.tools[0].parameters.allowed_domains).toEqual([domain]);
    if (domain === 'c.example.cr') return new Response(null, { status: 503 });
    return Response.json({ choices: [{ message: { content: `https://outside.example.cr/producto\nhttps://${domain}/producto` } }] });
  }));
  vi.mocked(fetchJsonLdOffers).mockImplementation(async (productUrl) => {
    const host = new URL(productUrl).hostname;
    return [{
      productUrl, title: 'Producto de prueba', priceCrc: host === 'a.example.cr' ? 42_000 : 38_000,
      availability: host === 'd.example.cr' ? 'https://schema.org/OutOfStock' : 'https://schema.org/InStock',
    }];
  });
  const activity: QuoteActivity[] = [];

  const result = await quoteItemOnDemand(item, { stores, onActivity: (event) => activity.push(event) });

  expect(visits.sort()).toEqual(stores.map(({ websiteUrl }) => websiteUrl).sort());
  expect(vi.mocked(fetchJsonLdOffers).mock.calls.map(([url]) => url).sort()).toEqual([
    'https://a.example.cr/producto', 'https://b.example.cr/producto', 'https://d.example.cr/producto',
  ]);
  expect(result.quote).toMatchObject({ storeName: 'Tienda B', priceCrc: 38_000, basis: 'published' });
  expect(result.alternatives).toEqual([expect.objectContaining({ storeName: 'Tienda A', priceCrc: 42_000 })]);
  expect(activity.at(-1)?.detail).toContain('4/4 tiendas revisadas');

  vi.mocked(fetchJsonLdOffers).mockResolvedValue([]);
  await expect(quoteItemOnDemand({ ...item, quotes: [result.quote] }, { stores }))
    .rejects.toThrow('Se conservó la cotización anterior');
});
