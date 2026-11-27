import snapshots from './natarundi-offers.json' with { type: 'json' };
import { itemizeBundle, itemizeCatalogBundles } from './itemize-bundle.js';
import { buildNatarundiProject } from './natarundi-catalog.js';
import { quoteTier } from '../shared/budgetProfiles.js';
import type { CatalogItem, FurnishingProject, LineItemQuote } from '../shared/types.js';

const bathParts = [
  { id: 'i-bath-showerhead', name: 'Cabeza de ducha' },
  { id: 'i-bath-soapdish', name: 'Jabonera' },
  { id: 'i-bath-enclosure', name: 'Cierre de ducha' },
];

function itemizeBath(project: FurnishingProject) {
  const bundle = project.items.find(i => i.id === 'i-bath-sets' && i.name === 'Accesorios baño (ducha, jaboneras, cortinas)' && i.zoneId === 'z-baths');
  if (!bundle || bathParts.some(part => project.items.some(i => i.id === part.id || (i.zoneId === bundle.zoneId && i.name === part.name)))) return project;
  // Keep a custom quotation for the entire bundle. A single soap dish was formerly offered for this bundle.
  if (bundle.quotes.some(q => q.basis === 'published' && !/jabonera de pl.stico para pared/i.test(q.title))) return project;
  return itemizeBundle(project, bundle, bathParts.map(part => ({ ...part, qty: bundle.qty, purchaseUnit: 'unit' })));
}

type Snapshot = Pick<LineItemQuote, 'productUrl' | 'priceCrc' | 'title' | 'storeName' | 'capturedAt'>
  & Partial<Pick<LineItemQuote, 'brand' | 'modelIds' | 'tier' | 'review' | 'availability' | 'description' | 'dimensions'>>;
const offers = snapshots as Record<string, Snapshot[]>;
const catalog = buildNatarundiProject('', '', 1);
const template = itemizeBath(itemizeCatalogBundles(catalog));
const expectedItems = new Map(template.items.map(i => [i.id, i]));

function applyOpeningOffers(project: FurnishingProject, onlyItemId?: string) {
  return { ...project, items: project.items.map((item): CatalogItem => {
    if (onlyItemId && item.id !== onlyItemId) return item;
    const expected = expectedItems.get(item.id);
    const available = offers[item.id];
    if (!expected || !available || item.name !== expected.name || item.zoneId !== expected.zoneId || item.category !== expected.category
      || item.notes !== expected.notes || item.purchaseUnit !== expected.purchaseUnit
      || (['w','h','d'] as const).some(axis => item.dimsM?.[axis] !== expected.dimsM?.[axis])) return item;
    const quotes = item.quotes.map(q => {
      const same = available.find(s => s.productUrl === q.productUrl && (s.tier ?? 'balanced') === quoteTier(q));
      return same ? { ...q, brand: q.brand ?? same.brand, modelIds: q.modelIds?.length ? q.modelIds : same.modelIds,
        ...(same.description && !q.description ? { description: same.description } : {}),
        ...(same.dimensions && !q.dimensions ? { dimensions: same.dimensions } : {}) } : q;
    });
    const lastSearch = { ...item.lastSearch };
    for (const offer of available) {
      const tier = offer.tier ?? 'balanced';
      if (quotes.some(q => quoteTier(q) === tier)) continue;
      quotes.push({ ...offer, id: item.id + '-' + tier + '-opening', source: 'store_direct', basis: 'published', tier,
        storeUrl: new URL(offer.productUrl).origin, availability: offer.availability ?? 'unknown' });
      delete lastSearch[tier];
    }
    return { ...item, quotes, lastSearch };
  }) };
}

export function applyNatarundiOpeningPrices(project: FurnishingProject) {
  if (project.name !== catalog.name || project.address !== catalog.address) return project;
  return applyOpeningOffers(itemizeBath(project));
}

export function applyNatarundiLaundryPrices(project: FurnishingProject) {
  if (project.name !== catalog.name || project.address !== catalog.address) return project;
  return applyOpeningOffers(project, 'i-laundry-tower');
}
