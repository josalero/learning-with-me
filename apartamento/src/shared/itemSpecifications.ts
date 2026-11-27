import { isFlexibleEnclosure } from './showerEnclosure.js';
import { normalizeSearch } from './search.js';
import type { BudgetTier, CatalogItem } from './types.js';

export const MAX_SPECIFICATIONS = 12;
export const MAX_SPECIFICATION_LENGTH = 180;

const catalogSpecifications: Record<string, string[]> = {
  'i-refri': ['Tipo: refrigeradora con congelador para el nicho de la cocina.', 'Instalación: comprobar ventilación y apertura de puerta.'],
  'i-micro': ['Tipo: microondas para colocar sobre la encimera.', 'Capacidad y potencia: según el modelo.', 'Instalación: comprobar espacio libre y ventilación.'],
  'i-cookware': ['Incluye: ollas, sartenes y utensilios.', 'Compatibilidad: confirmar el tipo de cocina.', 'Material: preferir superficies fáciles de limpiar.'],
  'i-island-stools': ['Tipo: taburete para isla de cocina, cotizado por unidad.', 'Altura del asiento: medir con respecto al borde inferior de la isla.', 'Acabado: preferir una superficie fácil de limpiar.'],
  'i-fan-kitchen': ['Montaje: ventilador de techo para cocina.', 'Diámetro y altura: comprobar separación respecto a paredes y luminarias.', 'Control y potencia: según el modelo.'],
  'i-laundry-tower': ['Formato: lavadora y secadora eléctrica en un solo equipo vertical, con dos tambores.', 'Capacidad: comparar lavado y secado para cargas de sábanas y toallas.', 'Medidas máximas: por confirmar. Medir ancho, alto, fondo y holgura para mangueras, ventilación y apertura.'],
  'i-dining-set': ['Incluye: mesa y sillas en un solo juego.', 'Capacidad: de 4 a 6 personas.', 'Medidas: confirmar que quede espacio para retirar las sillas y circular.'],
  'i-sofa': ['Formato: sofá cama o conjunto de sofá y futón.', 'Uso: asientos durante el día y cama auxiliar por la noche.', 'Medidas: verificar ancho y fondo, tanto cerrado como desplegado.'],
  'i-tv': ['Pantalla: de 43 a 50 pulgadas.', 'Tipo: Smart TV para la sala.', 'Instalación: confirmar si irá sobre mueble o en soporte de pared.'],
  'i-fan-living': ['Montaje: ventilador de techo para sala.', 'Diámetro y caudal: elegir según el tamaño de la sala.', 'Control e iluminación: confirmar qué incluye el modelo.'],
  'i-master-bed': ['Tamaño: queen.', 'Incluye: base y colchón.', 'Preferencia: estructura de madera con respaldo alto.', 'Medidas exteriores: confirmar ancho, largo y altura del respaldo.'],
  'i-fan-master': ['Montaje: ventilador de techo para dormitorio.', 'Uso: preferir funcionamiento silencioso y control de velocidad.', 'Diámetro y altura de montaje: por confirmar.'],
  'i-br2-bed': ['Tamaño: full o queen, según el espacio disponible.', 'Incluye: base y colchón.', 'Material y respaldo: por definir.', 'Medidas exteriores: comprobar el paso alrededor de la cama.'],
  'i-br3-bed': ['Formato: cama individual o litera compacta.', 'Medidas: medir el espacio disponible y el paso de entrada.', 'Si se lleva litera: comprobar altura libre y protecciones.'],
  'i-br3-desk': ['Formato: escritorio plegable o repisa para espacio compacto.', 'Montaje: verificar anclaje y capacidad de carga.', 'Medidas: confirmar ancho y profundidad después de la obra.'],
  'i-linens': ['Incluye: juegos de sábanas y toallas.', 'Tallas: comprobar el tamaño de cada colchón.', 'Lavado: preferir textiles aptos para uso frecuente.'],
  'i-bath-sets': ['Uso: accesorios para los dos baños.', 'Piezas previstas: cabeza de ducha, jabonera y cierre de ducha.', 'Instalación: comprobar las medidas y el tipo de montaje en cada baño.'],
  'i-terrace-set': ['Incluye: mesa y asientos para exterior.', 'Material: apto para exposición a sol y humedad.', 'Medidas: comprobar el paso libre en la terraza.'],
  'i-freight': ['Servicio: traslado de equipamiento a Portegolpe.', 'Carga: confirmar volumen, peso y artículos frágiles.', 'Alcance: solicitar precio con carga, descarga y entrega en el apartamento.'],
  'i-cookware-ollas': ['Formato: juego de ollas con tapa.', 'Capacidades: preferir más de un tamaño.', 'Compatibilidad: comprobar el tipo de cocina.'],
  'i-cookware-sarten': ['Formato: sartén por unidad.', 'Superficie: antiadherente.', 'Diámetro y compatibilidad: confirmar para la cocina del apartamento.'],
  'i-cookware-utensilios': ['Piezas mínimas: espátula, cucharón y pinzas.', 'Material: apto para usar con el sartén antiadherente.', 'Compra: juego completo.'],
  'i-cookware-vajilla': ['Piezas previstas: platos y tazas.', 'Servicio: confirmar piezas por comensal según la capacidad del apartamento.', 'Uso: verificar compatibilidad con microondas y lavado.'],
  'i-cookware-cubiertos': ['Incluye: cucharas, tenedores, cuchillos y cucharillas.', 'Material preferido: acero inoxidable.', 'Cantidad de piezas: verificar el servicio completo por comensal.'],
  'i-linens-sabanas-queen': ['Tamaño: queen.', 'Piezas: sábana ajustable, sábana plana y fundas.', 'Ajuste: confirmar la altura del colchón y la composición del tejido.'],
  'i-linens-sabanas-dormitorio-2': ['Tamaño: full o queen, según la cama elegida.', 'Piezas: sábana ajustable, sábana plana y fundas.', 'Tejido y ajuste: confirmar en la ficha del juego.'],
  'i-linens-sabanas-individual': ['Tamaño: individual o twin.', 'Piezas: sábana ajustable, sábana plana y funda.', 'Ajuste: comprobar medidas y altura del colchón.'],
  'i-linens-toallas': ['Incluye: toallas de baño y de manos.', 'Material preferido: tejido absorbente apto para lavado frecuente.', 'Confirmar medidas y número de piezas del juego.'],
  'i-bath-showerhead': ['Formato: cabeza de ducha por unidad.', 'Conexión: comprobar la rosca compatible.', 'Acabado y caudal: revisar en la ficha del modelo.'],
  'i-bath-soapdish': ['Formato: jabonera por unidad.', 'Uso: apta para humedad y con drenaje.', 'Montaje: comprobar si requiere perforación o adhesivo.'],
};

const enclosureSpecifications: Record<BudgetTier, string[]> = {
  economy: ['Formato: cortina de ducha lavable.', 'Medidas: confirmar ancho y caída.', 'Accesorios: confirmar si incluye barra y anillas.'],
  balanced: ['Formato: puerta o mampara de vidrio templado.', 'Medidas: comprobar el vano y el espacio de apertura.', 'Montaje: confirmar herrajes e instalación.'],
  premium: ['Formato: puerta o mampara de vidrio templado.', 'Mejora a documentar: mayor espesor, acabado antical, cierre suave o herrajes de acero inoxidable.', 'Medidas e instalación: confirmar por baño.'],
};

export function itemSpecifications(item: CatalogItem, tier: BudgetTier): string[] {
  const saved = item.specificationsByTier?.[tier] ?? item.specifications;
  if (saved?.length) return saved;
  if (isFlexibleEnclosure(item)) return enclosureSpecifications[tier];
  const specs = catalogSpecifications[item.id];
  if (specs) {
    const limit = formatDimensions(item.dimsM);
    return limit ? [...specs, `Medidas máximas del equipo: ${limit}.`] : specs;
  }
  if (item.parentItemId === 'i-tv' && /\b(televisor|tv|pantalla)\b/.test(normalizeSearch(item.name))) {
    return ['Uso: televisor para dormitorio, cotizado por separado.', 'Pantalla: confirmar tamaño según la distancia de visualización.', 'Instalación: comprobar el soporte y las conexiones.'];
  }
  return item.notes ? [item.notes] : [];
}

export function formatDimensions(dimensions?: CatalogItem['dimsM']) {
  const labels = { w: 'ancho', d: 'fondo', h: 'alto' } as const;
  return (['w', 'd', 'h'] as const).flatMap((axis) => {
    const value = dimensions?.[axis];
    return value && Number.isFinite(value) && value > 0
      ? [`${(value * 100).toLocaleString('es-CR', { maximumFractionDigits: 1 })} cm de ${labels[axis]}`] : [];
  }).join(' · ');
}
