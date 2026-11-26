import { normalizeSearch } from './search.js';
import type { BudgetTier, CatalogItem } from './types.js';

const enclosureNames: Record<BudgetTier, string> = {
  economy: 'Cortina de baño para ducha',
  balanced: 'Puerta o mampara de ducha de vidrio templado',
  premium: 'Puerta o mampara de ducha de vidrio templado con acabado superior',
};

export function isFlexibleEnclosure(item: CatalogItem) {
  return item.id === 'i-bath-enclosure' || /^(cierre|cerramiento) (de|para) (la )?ducha$/.test(normalizeSearch(item.name));
}

export function purchaseName(item: CatalogItem, tier: BudgetTier) {
  return isFlexibleEnclosure(item) ? enclosureNames[tier] : item.name;
}

export function enclosureAdvice(tier: BudgetTier) {
  return tier === 'economy'
    ? 'Comprueba el ancho, la altura y si hacen falta barra y anillas.'
    : 'Mide el ancho y la altura de cada ducha y comprueba el espacio de apertura. Confirma con la tienda qué herrajes, instalación y transporte incluye el precio.';
}

// An explicit request for glass or for a curtain keeps that material in every budget.
export function matchesShowerEnclosure(item: CatalogItem, tier: BudgetTier, name: string, evidence: string): boolean | undefined {
  const request = normalizeSearch(item.name);
  const flexible = isFlexibleEnclosure(item);
  const curtain = flexible ? tier === 'economy' : /cortina/.test(request) && /ducha|bano/.test(request);
  const glass = flexible ? tier !== 'economy' : /mampara|puerta/.test(request) && /ducha|bano/.test(request);
  if (!curtain && !glass) return;
  if (/^(anillas?|aros?|ganchos?|barras?|rieles?|kit de herrajes|herrajes|rodines?|burletes?|bisagras?|tirador|repuesto|suministro de vidrio)\b/.test(name)) return false;
  if (curtain) return /cortina/.test(name) && /bano|ducha/.test(name);
  if (!/mampara|puerta/.test(name) || !/ducha|bano/.test(name) || !/vidrio (?:de seguridad |de )?(?:templado|temperado)/.test(evidence)) return false;
  return !flexible || tier !== 'premium' || /\b(8|10|12)\s*mm\b|cierre suave|acero inoxidable|tratamiento antical/.test(evidence);
}
