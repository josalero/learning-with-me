import type { CatalogItem, FurnishingProject, ProjectZone } from '../shared/types.js';

export const NATARUNDI_ZONES: ProjectZone[] = [
  { id: 'z-kitchen', slug: 'cocina-isla', labelEs: 'Cocina + isla', areaM2: 18.5, notes: 'Isla 2.16×0.90 m; nicho refri 1.10 m' },
  { id: 'z-laundry', slug: 'lavanderia', labelEs: 'Lavandería', notes: 'Línea eléctrica; torre lavadora' },
  { id: 'z-dining', slug: 'comedor', labelEs: 'Comedor', notes: 'Junto a cocina' },
  { id: 'z-living', slug: 'sala', labelEs: 'Sala', notes: 'Abanico de techo' },
  { id: 'z-master', slug: 'dormitorio-principal', labelEs: 'Dormitorio principal', notes: 'Abanico; walk-in closet original' },
  { id: 'z-br2', slug: 'dormitorio-2', labelEs: 'Dormitorio 2' },
  { id: 'z-br3', slug: 'dormitorio-3', labelEs: 'Dormitorio 3 (ex closet)', notes: 'Layout compacto tipo habitación' },
  { id: 'z-baths', slug: 'banos', labelEs: '2 baños', notes: 'Master + social' },
  { id: 'z-terrace', slug: 'terraza', labelEs: 'Terraza', areaM2: 18.8, notes: '7.38×2.55 m aprox.' },
];

function item(
  partial: Omit<CatalogItem, 'quotes'> & { quotes?: CatalogItem['quotes'] },
): CatalogItem {
  return { quotes: [], ...partial };
}

/** Seed catalog — some items have demo quotes; others intentionally unquoted for UI. */
export const NATARUNDI_ITEMS: CatalogItem[] = [
  item({ id: 'i-refri', zoneId: 'z-kitchen', name: 'Refrigeradora (nicho 1.10 m)', category: 'electrodomesticos', qty: 1, dimsM: { w: 0.7, h: 1.75, d: 0.7 } }),
  item({ id: 'i-micro', zoneId: 'z-kitchen', name: 'Microondas', category: 'electrodomesticos', qty: 1 }),
  item({ id: 'i-cookware', zoneId: 'z-kitchen', name: 'Menaje cocina Airbnb (ollas, sartenes, utensilios)', category: 'menaje', qty: 1 }),
  item({ id: 'i-island-stools', zoneId: 'z-kitchen', name: 'Taburetes isla (×3)', category: 'mobiliario', qty: 3 }),
  item({ id: 'i-fan-kitchen', zoneId: 'z-kitchen', name: 'Abanico techo cocina', category: 'climatizacion', qty: 1 }),
  item({ id: 'i-washer', zoneId: 'z-laundry', name: 'Lavadora (torre o individual)', category: 'electrodomesticos', qty: 1 }),
  item({ id: 'i-dryer', zoneId: 'z-laundry', name: 'Secadora (si no es torre)', category: 'electrodomesticos', qty: 1, unquotedReason: 'Pendiente confirmar si hay espacio para torre vs par' }),
  item({ id: 'i-dining-set', zoneId: 'z-dining', name: 'Mesa comedor + sillas (4–6)', category: 'mobiliario', qty: 1 }),
  item({ id: 'i-sofa', zoneId: 'z-living', name: 'Sofá cama o sofá + futón', category: 'mobiliario', qty: 1 }),
  item({ id: 'i-tv', zoneId: 'z-living', name: 'TV smart 43–50"', category: 'electronica', qty: 1 }),
  item({ id: 'i-fan-living', zoneId: 'z-living', name: 'Abanico techo sala', category: 'climatizacion', qty: 1 }),
  item({ id: 'i-master-bed', zoneId: 'z-master', name: 'Cama queen + colchón', category: 'mobiliario', qty: 1 }),
  item({ id: 'i-fan-master', zoneId: 'z-master', name: 'Abanico techo principal', category: 'climatizacion', qty: 1 }),
  item({ id: 'i-br2-bed', zoneId: 'z-br2', name: 'Cama full/queen + colchón', category: 'mobiliario', qty: 1 }),
  item({ id: 'i-br3-bed', zoneId: 'z-br3', name: 'Cama individual o litera (habitación 3)', category: 'mobiliario', qty: 1, notes: 'Ex walk-in closet; priorizar footprint pequeño' }),
  item({ id: 'i-br3-desk', zoneId: 'z-br3', name: 'Escritorio plegable / repisa', category: 'mobiliario', qty: 1, unquotedReason: 'Medida final del closet-convertido pendiente de corte en obra' }),
  item({ id: 'i-linens', zoneId: 'z-master', name: 'Juego sábanas + toallas (×3 habitaciones)', category: 'textil', qty: 3 }),
  item({ id: 'i-bath-sets', zoneId: 'z-baths', name: 'Accesorios baño (ducha, jaboneras, cortinas)', category: 'acabados', qty: 2 }),
  item({ id: 'i-terrace-set', zoneId: 'z-terrace', name: 'Muebles terraza resistente clima', category: 'exterior', qty: 1 }),
  item({ id: 'i-freight', zoneId: 'z-living', name: 'Flete San José / Grecia → Portegolpe', category: 'logistica', qty: 1, unquotedReason: 'Cotización manual según volumen final' }),
];

export function buildNatarundiProject(
  workspaceId: string,
  projectId: string,
  usdCrcRate?: number,
): FurnishingProject {
  const rate = usdCrcRate ?? Number(process.env.DEFAULT_USD_CRC_RATE ?? '520');
  return {
    id: projectId,
    workspaceId,
    name: 'Condominio Natarundi — Portegolpe',
    address: 'Condominio Natarundi, Portegolpe, Guanacaste',
    region: 'guanacaste',
    zones: NATARUNDI_ZONES,
    items: NATARUNDI_ITEMS.map((i) => ({ ...i, quotes: [...i.quotes] })),
    usdCrcRate: rate,
  };
}