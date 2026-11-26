import { normalizeSearch } from '../shared/search.js';
import type { AirbnbAssessment, AirbnbCheck, AirbnbObservation, AirbnbVisualTopic, FurnishingProject, ProjectReference } from '../shared/types.js';

type AuditReference = Pick<ProjectReference, 'id' | 'title'> & { assetName: string | null };
const airbnb = 'https://es.airbnb.com/help/article/';
const plan = 'render-sala-cocina.jpg';
const overhead = 'mapa-3d-cenital.png';

// Observations from the original project drawings. Only the server-assigned seed asset identifies them.
const drawings: Record<string, Partial<Record<AirbnbVisualTopic, string>>> = {
  [plan]: {
    sleeping: 'El plano dibuja dos camas. El recinto superior izquierdo aparece sin cama.',
    bathrooms: 'El plano sitúa dos baños contiguos, con acceso desde lados distintos.',
    kitchen: 'Se distinguen la cocina con isla, el comedor y la sala.',
    terrace: 'La terraza figura junto a la sala, con cotas aproximadas de 7,38 × 2,55 m.',
  },
  [overhead]: {
    sleeping: 'La vista cenital muestra dos camas y un vestidor con estantes en el recinto superior izquierdo.',
    bathrooms: 'La vista cenital representa dos inodoros, dos lavamanos y dos zonas de ducha.',
    terrace: 'La vista cenital muestra mobiliario exterior y un cerramiento en el borde de la terraza.',
  },
  'plano-medidas.jpg': { kitchen: 'El render representa la isla, la cocina, el comedor y la sala en un espacio abierto.' },
};

export function buildAirbnbAssessment(project: FurnishingProject, references: AuditReference[], observations: AirbnbObservation[] = []): AirbnbAssessment {
  function space(id: AirbnbVisualTopic, title: string, article: number, action: string): AirbnbCheck {
    const evidence = references.flatMap(({ id: referenceId, title: referenceTitle, assetName }) => {
      const reference = { id: referenceId, title: referenceTitle };
      const reviewed = assetName && drawings[assetName]?.[id];
      if (reviewed) return [{ text: reviewed, reference }];
      return observations.filter((o) => o.referenceId === referenceId && o.topic === id)
        .map((o) => ({ text: 'Lectura de la IA: ' + o.detail, reference }));
    });
    return { id, title, policy: 'Anuncio y espacio', sourceUrl: airbnb + article, status: evidence.length ? 'documented' : 'pending', evidence, action };
  }

  const sleeping = space('sleeping', 'Dormitorios, camas y capacidad', 2895,
    'Fotografía cada cama instalada y confirma su tamaño y la ocupación segura antes de fijar el número de huéspedes.');
  const thirdBed = project.items.find((item) => item.zoneId === 'z-br3' && /\b(cama|litera)\b/.test(normalizeSearch(item.name)) && item.qty > 0);
  if (thirdBed && references.some(({ assetName }) => assetName === plan || assetName === overhead)) {
    sleeping.status = 'discrepancy';
    sleeping.evidence.push({ text: 'El presupuesto prevé «' + thirdBed.name + '» en el antiguo vestidor.' });
    sleeping.action = 'Comprueba si se terminó la conversión del vestidor y si puede destinarse a dormitorio. Anuncia la tercera habitación y sus plazas solo después de verificarlo.';
  }
  const bathrooms = space('bathrooms', 'Baños y duchas', 2804,
    'Confirma quién puede usar cada baño y si tiene privacidad. Prueba la descarga, el lavamanos y el agua fría y caliente de cada ducha.');
  const kitchen = space('kitchen', 'Cocina y espacios comunes', 2895,
    'Comprueba qué equipos están instalados y funcionan, y qué espacios son de uso privado o compartido.');
  const terrace = space('terrace', 'Terraza y riesgos de caída', 2904,
    'Si hay terraza, inspecciona su acceso, desniveles y protecciones. Corrige los peligros e informa de los riesgos inherentes en el anuncio.');
  const basics = [
    { label: 'sábanas', pattern: /\bsabanas?\b/ },
    { label: 'toallas', pattern: /\btoallas?\b/ },
    { label: 'almohadas', pattern: /\balmohadas?\b/ },
    { label: 'papel higiénico', pattern: /\bpapel higienico\b/ },
    { label: 'jabón de manos', pattern: /\bjabon(?:es)?\b.*\bmanos\b/ },
    { label: 'jabón corporal', pattern: /\bjabon(?:es)?\b.*\b(cuerpo|corporal|bano)\b/ },
  ];
  const names = project.items.filter((item) => item.qty > 0).map((item) => normalizeSearch(item.name));
  const planned = basics.filter(({ pattern }) => names.some((name) => pattern.test(name)));
  const missing = basics.filter((entry) => !planned.includes(entry));
  const essentials: AirbnbCheck = {
    id: 'essentials', title: 'Servicios básicos por huésped', policy: 'Si anuncias servicios básicos', sourceUrl: airbnb + '2343', status: 'pending',
    evidence: [
      ...(planned.length ? [{ text: 'Incluidos en el presupuesto: ' + planned.map((item) => item.label).join(', ') + '.' }] : []),
      ...(missing.length ? [{ text: 'Sin partida identificada por nombre: ' + missing.map((item) => item.label).join(', ') + '.' }] : []),
    ],
    action: 'Cuenta una toalla y una almohada por huésped, sábanas para cada cama, papel higiénico y jabón de manos y cuerpo. Verifica las piezas disponibles dentro de cada juego.',
  };
  const pending = (id: string, title: string, policy: string, sourceUrl: string, action: string): AirbnbCheck =>
    ({ id, title, policy, sourceUrl, action, status: 'pending', evidence: [] });
  return { policyDate: '2026-11-25', checks: [
    sleeping, bathrooms, kitchen, terrace, essentials,
    pending('entry', 'Entrada y salida segura', 'Seguridad', airbnb + '2904',
      'Prueba la llave o el código desde la entrada principal y recorre la salida con el mobiliario instalado para verificar que esté despejada.'),
    pending('alarms', 'Detectores de humo y monóxido de carbono', 'Seguridad', airbnb + '2904',
      'Declara si hay cada tipo de detector. Airbnb recomienda instalarlos. Si están presentes, comprueba su funcionamiento y pruébalos periódicamente.'),
    pending('privacy', 'Cámaras y privacidad', 'Privacidad', airbnb + '3061',
      'Inspecciona el apartamento. Airbnb prohíbe cámaras que vigilen el interior, incluso apagadas. Declara la ubicación de las exteriores permitidas.'),
    pending('cleaning', 'Limpieza entre estancias', 'Preparación de la estancia', airbnb + '2895',
      'Verifica la limpieza de baños, cocina y textiles entre huéspedes y corrige cualquier indicio de plagas o moho.'),
    pending('access', 'Prestaciones de accesibilidad', 'Si anuncias estas prestaciones', airbnb + '1961',
      'Documenta con fotos el trayecto, los umbrales y la ducha. Para anunciar una puerta ancha, fotografía el ancho libre real con cinta métrica.'),
    pending('registration', 'Registro de hospedaje en Costa Rica', 'Trámite local', 'https://ict.go.cr/es/servicios-institucionales/registro-de-hospedaje-no-tradicional.html',
      'Comprueba la inscripción del prestatario ante el ICT para operar como hospedaje no tradicional. La constancia se verifica por separado.'),
  ] };
}
