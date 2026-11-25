import { normalizeSearch } from './search.js';
import type { ProjectZone } from './types.js';

const commonAreas = [
  { id: 'sala', label: 'Sala', pattern: /\b(sala|living)\b/ },
  { id: 'comedor', label: 'Comedor', pattern: /\bcomedor\b/ },
  { id: 'cocina', label: 'Cocina', pattern: /\bcocina\b/ },
  { id: 'cuartos', label: 'Cuartos', pattern: /\b(dormitorios?|cuartos?|recamaras?|habitacion(?:es)?)\b/ },
  { id: 'banos', label: 'Baños', pattern: /\bbanos?\b/ },
  { id: 'lavanderia', label: 'Lavandería', pattern: /\blavanderia\b/ },
  { id: 'terraza', label: 'Terraza', pattern: /\bterrazas?\b/ },
];

export function projectAreas(zones: ProjectZone[]) {
  const groups = new Map(commonAreas.map(({ id, label }) => [id, { id, label, zones: [] as ProjectZone[] }]));
  for (const zone of zones) {
    const name = normalizeSearch(zone.labelEs);
    const match = commonAreas.find(({ pattern }) => pattern.test(name))
      ?? commonAreas.find(({ pattern }) => pattern.test(normalizeSearch(zone.slug.replaceAll('-', ' '))));
    if (match) groups.get(match.id)!.zones.push(zone);
    else groups.set(`zone:${zone.id}`, { id: `zone:${zone.id}`, label: zone.labelEs, zones: [zone] });
  }
  return [...groups.values()].filter((group) => group.zones.length > 0);
}
