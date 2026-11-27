import { afterEach, expect, it, vi } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { fetchJsonLdOffers, fetchProductReviews } from '../src/server/scrapers/fetchPage.js';
import { quoteItemOnDemand, quoteProjectItems } from '../src/server/services/quoteOnDemand.js';
import { applyDemoQuotes } from '../src/domain/demo-quotes.js';
import { quotesForTier } from '../src/shared/budgetProfiles.js';
import { QUOTE_CONCURRENCY, type QuoteActivity } from '../src/shared/types.js';

vi.mock('../src/server/scrapers/fetchPage.js', () => ({ fetchJsonLdOffers: vi.fn(), fetchProductReviews: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.resetAllMocks(); });

it('searches using specs from the selected tier and gives its verified matches priority over a previous price', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '0');
  vi.stubEnv('OPENROUTER_API_KEY', 'test-key');
  const item = buildNatarundiProject('workspace', 'project').items.find(({ id }) => id === 'i-master-bed')!;
  const preferred = 'https://tienda.example.cr/cama-queen-madera';
  const previous = 'https://tienda.example.cr/cama-queen-metal';
  item.specificationsByTier = { balanced: ['Material: madera.', 'Respaldo: alto.'], premium: ['Material: nogal.'] };
  item.quotes = [{ id: 'old', storeUrl: 'https://tienda.example.cr', storeName: 'Tienda', productUrl: previous,
    title: 'Cama base queen de metal con colchón', priceCrc: 100_000, source: 'store_direct', basis: 'published',
    availability: 'in_stock', capturedAt: '2026-11-26T12:00:00Z' }];
  const search = vi.fn(async (_url, options: RequestInit) => {
    const request = JSON.parse(String(options.body));
    const query = JSON.parse(request.messages[1].content);
    expect(query.especificaciones).toEqual(item.specificationsByTier!.balanced);
    expect(query.nivel.label).toBe('Gama media');
    return Response.json({ choices: [{ message: { content: preferred } }] });
  });
  vi.stubGlobal('fetch', search);
  vi.mocked(fetchJsonLdOffers).mockImplementation(async (productUrl) => [{ productUrl,
    title: `Cama base queen con colchón de ${productUrl === preferred ? 'madera' : 'metal'}`,
    availability: 'InStock', priceCrc: productUrl === preferred ? 150_000 : 100_000,
    description: productUrl === preferred ? 'Respaldo alto.' : undefined,
    review: { rating: 4.8, count: 50, sourceUrl: productUrl },
  }]);
  const result = await quoteItemOnDemand(item, { tier: 'balanced' });
  expect(search).toHaveBeenCalledOnce();
  expect(result.quote).toMatchObject({ productUrl: preferred, description: 'Respaldo alto.', tier: 'balanced' });
});

it('quotes pending items in a bounded parallel pool and uses prices published by the store', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '0');
  vi.stubEnv('OPENROUTER_API_KEY', 'test-key');
  const project = buildNatarundiProject('workspace', 'project');
  project.items = project.items.slice(0, 5).map((item, index) => ({ ...item, id: `pool-${index}`, name: 'Producto de prueba' }));
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
    dimensions: { w: .6, h: 1.7, d: .65 }, title: 'Producto de prueba', priceCrc: 12345, currency: 'CRC', productUrl: 'https://tienda.example.cr/producto',
    review: { rating: 4.7, count: 31, sourceUrl: 'https://tienda.example.cr/producto' },
  }]);
  const activity: QuoteActivity[] = [];
  const result = await quoteProjectItems(project, (event) => activity.push(event));

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
      productUrl, dimensions: { w: .6, h: 1.7, d: .65 }, title: 'Refrigeradora de prueba', priceCrc: host === 'a.example.cr' ? 42_000 : 38_000,
      availability: host === 'd.example.cr' ? 'https://schema.org/OutOfStock' : 'https://schema.org/InStock',
      review: { rating: 4.6, count: 70, sourceUrl: productUrl },
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
  expect(activity.some(({ detail }) => detail.includes('4/4 tiendas revisadas'))).toBe(true);

  vi.mocked(fetchJsonLdOffers).mockResolvedValue([]);
  await expect(quoteItemOnDemand({ ...item, quotes: [result.quote] }, { stores }))
    .rejects.toThrow('Se conservó la cotización anterior');
});

it('refreshes only the requested tier and preserves existing prices in other tiers', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '0');
  vi.stubEnv('OPENROUTER_API_KEY', 'test-key');
  const project = applyDemoQuotes(buildNatarundiProject('workspace', 'project'));
  const item = project.items[0]!;
  project.items = [item];
  project.budgetTiers = ['balanced', 'premium'];
  const labels: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (_url: string, options: RequestInit) => {
    const request = JSON.parse(String(options.body));
    const query = JSON.parse(request.messages[1].content);
    if (Array.isArray(query)) {
      return Response.json({ choices: [{ message: { content: '0 https://reviews.example.cr/nevera' } }] });
    }
    labels.push(query.nivel.label);
    return Response.json({ choices: [{ message: { content: [
      'https://tienda.example.cr/alto',
      'https://tienda.example.cr/alternativa',
      'https://tienda.example.cr/barato',
    ].join('\n') } }] });
  }));
  vi.mocked(fetchJsonLdOffers).mockImplementation(async (productUrl) => {
    const common = { productUrl, dimensions: { w: .6, h: 1.7, d: .65 }, title: 'Nevera HG12345', availability: 'https://schema.org/InStock' };
    if (productUrl.endsWith('/alto')) return [{ ...common, priceCrc: 410_000, modelIds: ['HG12345'] }];
    if (productUrl.endsWith('/alternativa')) return [{ ...common, priceCrc: 510_000, review: { rating: 4.8, count: 90, sourceUrl: productUrl } }];
    return [{ ...common, priceCrc: 220_000, review: { rating: 5, count: 1, sourceUrl: productUrl } }];
  });
  vi.mocked(fetchProductReviews).mockResolvedValue([
    { title: 'Modelo incorrecto HH5555', modelIds: ['HH5555'], productUrl: 'https://reviews.example.cr/nevera', review: {
      rating: 5, count: 500, sourceUrl: 'https://reviews.example.cr/incorrecto',
    } },
    { title: 'Nevera HG12345', productUrl: 'https://reviews.example.cr/nevera', review: {
      rating: 4.75, count: 90, sourceUrl: 'https://reviews.example.cr/nevera',
    } },
  ]);

  const result = await quoteProjectItems(project, undefined, undefined, 'all', 'premium');
  expect(labels).toEqual(['Gama alta']);
  expect(result.saved).toEqual([{ itemId: item.id, tier: 'premium' }]);
  expect(quotesForTier(result.project.items[0]!, 'balanced')).toEqual(quotesForTier(item, 'balanced'));
  expect(quotesForTier(result.project.items[0]!, 'premium')).toHaveLength(3);
  expect(quotesForTier(result.project.items[0]!, 'premium')[0]).toMatchObject({
    priceCrc: 410_000, productUrl: 'https://tienda.example.cr/alto', review: { count: 90, sourceUrl: 'https://reviews.example.cr/nevera' },
  });
});

it('saves an available matching price and direct product URL even when the model has no customer reviews', async () => {
  vi.stubEnv('AI_OFFLINE_STUB', '1');
  const url = 'https://tienda.example.cr/microondas-20l';
  const item = buildNatarundiProject('workspace', 'project').items.find(product => product.id === 'i-micro')!;
  const previous = { ...applyDemoQuotes(buildNatarundiProject('workspace', 'project')).items[0]!.quotes[0]!,
    productUrl: url, basis:'published' as const, priceCrc:55000 };
  vi.mocked(fetchJsonLdOffers).mockResolvedValue([{ title: 'Microondas 20 L', productUrl: url, priceCrc:49950, availability:'InStock' }]);
  const result = await quoteItemOnDemand({ ...item, quotes:[previous] });
  expect(result.quote).toMatchObject({ productUrl:url, priceCrc:49950, basis:'published' });
  expect(result.quote.review).toBeUndefined();
});
