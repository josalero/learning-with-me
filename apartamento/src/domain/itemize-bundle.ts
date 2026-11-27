import type { CatalogItem, FurnishingProject } from '../shared/types.js';

export type BundleComponent = Pick<CatalogItem, 'id' | 'name' | 'qty' | 'purchaseUnit' | 'notes' | 'specifications'> & { zoneId?: string };

export function itemizeBundle(project: FurnishingProject, bundle: CatalogItem, components: BundleComponent[]): FurnishingProject {
  const items = project.items.flatMap((item) => item.id === bundle.id ? components.map((component): CatalogItem => ({
    ...component,
    zoneId: component.zoneId ?? bundle.zoneId,
    category: bundle.category,
    parentItemId: bundle.id,
    quotes: [],
    unquotedReason: 'Pendiente de cotizar individualmente.',
  })) : [item]);
  return { ...project, items, itemizedBundles: [...(project.itemizedBundles ?? []), bundle] };
}

const catalogBundles: { id: string; category: string; parts: (Omit<BundleComponent, 'qty'> & { qty?: number })[] }[] = [
  { id: 'i-cookware', category: 'menaje', parts: [
    { id: 'ollas', name: 'Juego de ollas con tapa', purchaseUnit: 'set' },
    { id: 'sarten', name: 'Sartén antiadherente', purchaseUnit: 'unit' },
    { id: 'utensilios', name: 'Juego de utensilios de cocina', purchaseUnit: 'set', notes: 'Espátula, cucharón y pinzas.' },
    { id: 'vajilla', name: 'Juego de vajilla', purchaseUnit: 'set', notes: 'Platos y tazas para la capacidad del apartamento.' },
    { id: 'cubiertos', name: 'Juego de cubiertos', purchaseUnit: 'set', notes: 'Cucharas, tenedores, cuchillos y cucharillas.' },
  ] },
  { id: 'i-linens', category: 'textil', parts: [
    { id: 'sabanas-queen', name: 'Juego de sábanas queen', purchaseUnit: 'set', qty: 1, zoneId: 'z-master' },
    { id: 'sabanas-dormitorio-2', name: 'Juego de sábanas para dormitorio 2', purchaseUnit: 'set', qty: 1, zoneId: 'z-br2', notes: 'Confirmar la talla de la cama entre full y queen.' },
    { id: 'sabanas-individual', name: 'Juego de sábanas individual', purchaseUnit: 'set', qty: 1, zoneId: 'z-br3' },
    { id: 'toallas', name: 'Juego de toallas', purchaseUnit: 'set', zoneId: 'z-baths' },
  ] },
];

export function itemizeCatalogBundles(project: FurnishingProject): FurnishingProject {
  for (const { id, category, parts } of catalogBundles) {
    const bundle = project.items.find((item) => item.id === id && item.category === category);
    if (!bundle || bundle.quotes.some(({ basis }) => basis === 'published')) continue;
    project = itemizeBundle(project, bundle, parts.map((part) => ({
      ...part,
      id: `${id}-${part.id}`,
      qty: part.qty ?? bundle.qty,
      zoneId: project.zones.some((zone) => zone.id === part.zoneId) ? part.zoneId : bundle.zoneId,
    })));
  }
  return project;
}
