import { normalizeSearch } from './search.js';
import type { LineItemQuote } from './types.js';

export const brandGuide = [
  { brand: 'All-Clad', line: 'D3', category: 'Menaje', trait: 'Acero y aluminio unidos en tres capas. La sartén D3 de 12 pulgadas destacó en las pruebas de cocción de America’s Test Kitchen.', url: 'https://www.americastestkitchen.com/equipment-reviews/1944' },
  { brand: 'Tramontina', line: 'Signature Tri-Ply Clad', category: 'Menaje', trait: 'Acero inoxidable 18/10 con núcleo de aluminio y compatibilidad con inducción. Compara el precio local por pieza con D3.', url: 'https://www.tramontina.com/collections/signature-2/products/signature-tri-ply-clad-stainless-steel-cookware-set' },
  { brand: 'Tefal', line: 'Ingenio Expertise', category: 'Menaje', trait: 'Antiadherente para inducción y mango desmontable. Su formato apilable ahorra espacio en la cocina.', url: 'https://www.tefal.com/instructions-for-use/Products-/Pressure-cookers--pots-and-pans/Pots-And-Pans-Stackabled-Aluminium/INGENIO-EXPERTISE-NON-STICK-INDUCTION/csp/RANGE-24AJ-G' },
  { brand: 'Corelle', line: 'Vitrelle', category: 'Vajilla', trait: 'Vidrio de tres capas laminadas. Comprueba que la pieza elegida pertenezca a esta línea.', url: 'https://corelle.com/pages/frequently-asked-questions' },
  { brand: 'Frigidaire', line: 'Gallery', category: 'Refrigeración', trait: 'El modelo GRFS287DAF incorpora control automático de humedad. Verifica las funciones y medidas de cada modelo.', url: 'https://www.frigidaire.com/en/p/Kitchen/Refrigerators/French-Door-Refrigerators/GRFS287DAF' },
  { brand: 'Hunter', line: 'SureSpeed', category: 'Ventilación', trait: 'La línea se diseña según la velocidad del aire. Compara diámetro, caudal y ruido para el ambiente.', url: 'https://support.hunterfan.com/hc/en-us/articles/115001398273-How-much-air-do-ceiling-fans-move' },
  { brand: 'LG', line: 'OLED evo', category: 'Televisores', trait: 'Pantallas OLED 4K disponibles en distintos tamaños. Compara el tamaño y la serie exactos.', url: 'https://solutions.lg.com/us/oled-tvs/view-all' },
  { brand: 'Sealy', line: 'Posturepedic', category: 'Colchones', trait: 'La colección incorpora resortes de soporte localizado. Compara firmeza, tamaño y garantía local.', url: 'https://help.sealy.com/hc/en-us/articles/30376642823063-What-are-the-Coil-Technologies-in-the-Posturepedic-Collection' },
];

export type ProductProvenance = 'replica' | 'generic' | 'unverified';
export function productProvenance(title: string, brand?: string): ProductProvenance {
  const text = normalizeSearch(title);
  if (/\b(replica|replicas|knockoff|falsificacion)\b/.test(text) || /\bimitacion (?:de (?:la )?)?marca\b/.test(text)) return 'replica';
  if (/^(generica?|generico|sin marca|unbranded|generic)$/i.test(normalizeSearch(brand ?? '')) || /\b(sin marca|unbranded)\b/.test(text)) return 'generic';
  return 'unverified';
}

export function brandLabel(quote?: LineItemQuote) {
  if (!quote) return 'Por identificar';
  if (productProvenance(quote.title, quote.brand) === 'generic') return 'Genérico';
  if (quote.brand) return brandGuide.find((entry) => normalizeSearch(entry.brand) === normalizeSearch(quote.brand!))?.brand ?? quote.brand;
  const title = ' ' + normalizeSearch(quote.title).replace(/[^a-z0-9-]/g,' ') + ' ';
  return brandGuide.find((entry) => title.includes(' ' + normalizeSearch(entry.brand) + ' '))?.brand ?? 'Por identificar';
}

export function productClass(quote?: LineItemQuote) {
  if (!quote) return 'Sin producto elegido';
  const identity = productProvenance(quote.title, quote.brand);
  if (identity === 'replica') return 'Réplica declarada';
  if (identity === 'generic') return 'Genérico declarado';
  const brand = normalizeSearch(brandLabel(quote));
  if (brand === 'glassmar') {
    const glass = normalizeSearch(quote.title);
    if (/\blusso\b/.test(glass) && /\b8\s*mm\b/.test(glass)) return 'Alta gama documentada';
    if (/\batlantis\b/.test(glass) && /\b6\s*mm\b/.test(glass)) return 'Gama media documentada';
  }
  const title = normalizeSearch(quote.title);
  const entry = brandGuide.find((model) => normalizeSearch(model.brand) === brand && (' ' + title.replace(/[^a-z0-9]+/g, ' ') + ' ').includes(' ' + normalizeSearch(model.line).replace(/[^a-z0-9]+/g, ' ') + ' '));
  if (!entry) return 'Línea por identificar';
  return ['D3', 'Signature Tri-Ply Clad', 'OLED evo'].includes(entry.line) ? 'Alta gama documentada' : 'Línea especializada documentada';
}
