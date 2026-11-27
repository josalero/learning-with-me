import { normalizeSearch } from '../shared/search.js';
import { matchesShowerEnclosure } from '../shared/showerEnclosure.js';
import type { BudgetTier, CatalogItem } from '../shared/types.js';

export function namesTelevision(value: string) {
  return /^(?:(?:smart )?tv|televisor|pantalla)\b/.test(normalizeSearch(value));
}

export function isTelevisionRequest(item: CatalogItem) {
  return item.id === 'i-tv' || item.parentItemId === 'i-tv' || (item.category === 'electronica' && namesTelevision(item.name));
}

// Enforce the purchase unit and options in the original apartment brief on each refresh.
export function matchesPurchase(item: CatalogItem, title: string, description = '', tier: BudgetTier = 'balanced') {
  const name = normalizeSearch(title);
  const evidence = name + ' ' + normalizeSearch(description);
  if (/\b(juguete|infantil de juguete|para munecas)\b/.test(name)) return false;
  const enclosure = matchesShowerEnclosure(item, tier, name, evidence);
  if (enclosure !== undefined) return enclosure;
  if (isTelevisionRequest(item)) {
    return namesTelevision(title) && !/^(?:pantalla|tv|televisor) (?:de repuesto|para reemplazo)\b/.test(name)
      && (item.id !== 'i-tv' || /\b(4[3-9]|50)\b/.test(evidence));
  }
  switch (item.id) {
    case 'i-laundry-tower': {
      const equipment = /\b(?:(?:centro|torre) de lavado|wash ?tower|lavadora y secadora (?:vertical|apilada))\b/;
      if (!equipment.test(name) || /^(?:kit|base|pedestal|repuesto|accesorio|soporte)\b/.test(name)
        || /\b(?:para (?:el |un )?(?:centro|torre) de lavado|a gas)\b/.test(name) || /\bsecadora (?:de|a) gas\b/.test(evidence)) return false;
      if (!/\b(electric[oa]s?|220\s*v(?:oltios)?|240\s*v(?:oltios)?)\b/.test(evidence)) return false;
      return tier !== 'premium' || /\b(inverter|bomba de calor|secado por condensacion|vapor|antivibracion)\b/.test(evidence);
    }
    case 'i-refri': return /^(refrigerador|nevera|frigorifico)/.test(name);
    case 'i-micro': return /^(?:horno (?:de )?)?microondas/.test(name);
    case 'i-washer': return /^lavadora/.test(name);
    case 'i-dryer': return /^secadora/.test(name) && !/cabello|pelo/.test(name);
    case 'i-fan-kitchen':
    case 'i-fan-master':
    case 'i-fan-living': return /^(abanico|ventilador)/.test(name) && /techo|plafon/.test(evidence);
    case 'i-sofa': return /sofa/.test(name) && /cama|futon/.test(evidence);
    case 'i-terrace-set': return /juego|set|conjunto/.test(name) && /exterior|terraza|patio|jardin/.test(evidence);
    case 'i-cookware-vajilla': return /vajilla/.test(name);
    case 'i-master-bed':
    case 'i-br2-bed': return /\b(colchon|mattress)\b/.test(name) && /\b(cama|base)\b/.test(name)
      && (item.id === 'i-master-bed' ? /queen/ : /matrimonial|full|queen/).test(name);
    case 'i-br3-bed': return /\b(cama|litera|camarote|base)\b/.test(name) && /individual|twin|litera|camarote|unipersonal/.test(evidence) && !/sofa|inflable/.test(name);
    case 'i-br3-desk': return /repisa|(?:escritorio.*(?:plegable|abatible))|(?:(?:plegable|abatible).*escritorio)/.test(name);
    case 'i-dining-set': return /comedor/.test(name) && /4|6|cuatro|seis/.test(evidence) && !/exterior|terraza|jardin/.test(name);
    case 'i-island-stools': return /taburete|(?:silla|banco).*(?:bar|desayunador|alto|alta)/.test(name);
    case 'i-cookware-utensilios': return /utensilio/.test(name) && !/parrilla|barbacoa|asador/.test(name);
    case 'i-cookware-sarten': return /sarten/.test(name) && /antiadherente|non.?stick/.test(evidence) && !/juego|\bset\b|electrico/.test(name);
    case 'i-cookware-cubiertos': return /cubiertos/.test(name) && !/infantil|plastico|parrilla|bbq/.test(name) && /cuchara|tenedor.*cuchillo|(?:16|20|24|30|48|60)\s*(?:piezas|pcs|pzs)/.test(evidence);
    case 'i-cookware-ollas': return /ollas|bateria/.test(name) && /tapas?|lids?/.test(evidence);
    case 'i-linens-sabanas-queen': return /sabana/.test(name) && /queen/.test(name);
    case 'i-linens-sabanas-individual': return /sabana/.test(name) && /individual|twin/.test(name);
    case 'i-linens-sabanas-dormitorio-2': return /sabana/.test(name) && /full|matrimonial|queen/.test(name);
    case 'i-linens-toallas': return /toallas?/.test(name) && /bano|cuerpo/.test(evidence) && !/bebe|infantil|cocina/.test(name) && !/^juego de toallas faciales|^set de toallas faciales/.test(name);
    case 'i-bath-showerhead': return /cabeza|cabezal|regadera/.test(name) && /ducha|regadera/.test(name);
    case 'i-bath-soapdish': return /jabonera/.test(name);
    case 'i-bath-sets': return /ducha|regadera/.test(evidence) && /jabonera/.test(evidence) && /cortina/.test(evidence);
    case 'i-freight': return /flete|transporte|mudanza/.test(name) && /portegolpe/.test(evidence) && /grecia|san jose/.test(evidence);
    default: {
      const keywords = normalizeSearch(item.name).split(/[^a-z0-9]+/).filter(word => word.length > 2
        && !['para', 'con', 'del', 'las', 'los', 'una', 'juego', 'set', 'kit', 'unidad'].includes(word));
      return keywords.some(word => name.includes(word.replace(/s$/, '')));
    }
  }
}
