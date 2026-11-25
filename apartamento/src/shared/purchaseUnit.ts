import { normalizeSearch } from './search.js';
import type { CatalogItem } from './types.js';

export function isPurchaseSet(item: Pick<CatalogItem, 'name' | 'purchaseUnit'>) {
  return item.purchaseUnit ? item.purchaseUnit === 'set'
    : /\b(juego|set|kit|combo|vajilla)\b|mesa.*comedor.*\+.*sillas/.test(normalizeSearch(item.name));
}

export function purchaseQuantity(item: Pick<CatalogItem, 'name' | 'purchaseUnit' | 'qty'>) {
  return `${item.qty} ${isPurchaseSet(item) ? item.qty === 1 ? 'juego' : 'juegos' : item.qty === 1 ? 'unidad' : 'unidades'}`;
}
