import type { StoreRef } from '../../shared/types.js';

export const SEED_STORES: StoreRef[] = [
  {
    id: 'siman-cr',
    name: 'Siman Costa Rica',
    region: 'san-jose',
    websiteUrl: 'https://cr.siman.com/',
    probeKind: 'vtex',
  },
  {
    id: 'aliss',
    name: 'Aliss',
    region: 'guanacaste',
    websiteUrl: 'https://aliss.cr/',
    probeKind: 'ssr_price',
  },
  {
    id: 'epa',
    name: 'EPA',
    region: 'san-jose',
    websiteUrl: 'https://cr.epaenlinea.com/es-cr',
    probeKind: 'shopify',
  },
  {
    id: 'fantini-liberia',
    name: 'Fantini Liberia',
    region: 'guanacaste',
    websiteUrl: 'https://fantini.cr/es-cr',
    probeKind: 'shopify',
  },
  {
    id: 'nalakalu',
    name: 'Nalakalu',
    region: 'guanacaste',
    websiteUrl: 'https://nalakalu.com/',
    probeKind: 'woocommerce',
  },
  {
    id: 'mister-precios',
    name: 'MisterPrecios',
    region: 'san-jose',
    websiteUrl: 'https://misterprecios.com/',
    probeKind: 'json_ld',
  },
  {
    id: 'ahorra-ya',
    name: 'AhorraYa',
    region: 'san-jose',
    websiteUrl: 'https://www.ahorraya.app/ahorraya-costa-rica',
    probeKind: 'json_ld',
  },
];