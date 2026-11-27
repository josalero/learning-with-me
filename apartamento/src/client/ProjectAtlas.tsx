import { useState, type MouseEvent, type ReactNode } from 'react';
import { Armchair, Bath, BedDouble, CookingPot, House, PackageOpen, Sofa, Truck, Utensils, WashingMachine } from 'lucide-react';
import { projectAreas } from '@shared/projectAreas';
import type { CatalogItem, FurnishingProject } from '@shared/types';
import { unassignedLabel } from './ItemLocation';
import { inventoryPath, Link } from './navigation';

export const unassignedRoomId = 'sin-asignar';

const icons: Record<string, typeof Sofa> = {
  sala: Sofa, comedor: Utensils, cocina: CookingPot, cuartos: BedDouble, banos: Bath,
  lavanderia: WashingMachine, terraza: Armchair, 'zone:z-logistics': Truck,
};

const markers: Record<string, { x: number; y: number; name: string; short?: string }> = {
  'z-terrace': { x: 79, y: 13, name: 'Terraza' },
  'z-living': { x: 81, y: 33, name: 'Sala' },
  'z-dining': { x: 82, y: 61, name: 'Comedor' },
  'z-kitchen': { x: 77, y: 81, name: 'Cocina' },
  'z-laundry': { x: 56, y: 91, name: 'Lavandería', short: 'Lav.' },
  'z-master': { x: 24, y: 60, name: 'Principal' },
  'z-br2': { x: 58, y: 30, name: 'Dorm. 2', short: 'D2' },
  'z-br3': { x: 39, y: 27, name: 'Dorm. 3', short: 'D3' },
  'z-baths': { x: 56, y: 63, name: 'Baños' },
};

type Room = { id: string; label: string; icon: typeof Sofa; zoneId: string | null; items: CatalogItem[] };

export function projectRooms(project: FurnishingProject): Room[] {
  const rooms = projectAreas(project.zones).flatMap(({ id, zones }) => zones.map((zone): Room => ({
    id: zone.id, label: zone.labelEs, icon: icons[id] ?? House, zoneId: zone.id, items: [],
  })));
  const byZone = new Map(rooms.map((room) => [room.zoneId, room]));
  const unassigned: Room = { id: unassignedRoomId, label: unassignedLabel, icon: PackageOpen, zoneId: null, items: [] };
  for (const item of project.items) (byZone.get(item.zoneId) ?? unassigned).items.push(item);
  if (unassigned.items.length || !rooms.length) rooms.push(unassigned);
  return rooms;
}

export function lineCount(count: number) {
  return `${count} ${count === 1 ? 'artículo' : 'artículos'}`;
}

function revealRoom(event: MouseEvent<HTMLAnchorElement>) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey
    || !window.matchMedia('(max-width: 53.99rem)').matches) return;
  requestAnimationFrame(() => document.getElementById('detalle-espacio')?.scrollIntoView());
}

export function ProjectAtlas({ project, rooms, activeRoom, children, id }: {
  project: FurnishingProject;
  rooms: Room[];
  activeRoom?: string;
  children?: ReactNode;
  id?: string;
}) {
  const [failed, setFailed] = useState(false);
  const mapped = project.zones.some((zone) => zone.id === 'z-kitchen' && zone.slug === 'cocina-isla')
    && project.zones.some((zone) => zone.id === 'z-terrace' && zone.slug === 'terraza');
  const showPlan = mapped && !failed;
  const spaces = rooms.filter(({ id: roomId }) => roomId !== 'z-logistics' && roomId !== unassignedRoomId);
  const other = rooms.filter(({ id: roomId }) => roomId === 'z-logistics' || roomId === unassignedRoomId);

  function links(entries: Room[]) {
    return <ul className="planner-room-links" role="list">{entries.map(({ id: roomId, icon: Icon, label, items }) => (
      <li key={roomId}><Link href={inventoryPath(project.id, roomId)} aria-current={activeRoom === roomId ? 'page' : undefined} onClick={revealRoom}>
        <Icon size={16} aria-hidden="true" /><span>{label}</span><small aria-label={lineCount(items.length)}>{items.length}</small>
      </Link></li>
    ))}</ul>;
  }

  return <section id={id} className="project-atlas" aria-label={showPlan ? 'Plano vivo' : 'Espacios del proyecto'}>
    <div className="atlas-title">
      <p>{showPlan ? 'Plano vivo' : 'Equipamiento'}</p>
      <h2>{showPlan ? 'Recorré la casa' : 'Espacios del proyecto'}</h2>
    </div>
    {showPlan && <div className="atlas-map">
      <img src="/assets/mapa-3d-cenital.png" alt="Vista cenital de la distribución de Natarundi" onError={() => setFailed(true)} />
      {rooms.map(({ id: roomId, label, icon: Icon }) => {
        const marker = markers[roomId];
        if (!marker) return null;
        return <Link className="atlas-pin" key={roomId} href={inventoryPath(project.id, roomId)} aria-label={`Ver ${label} en el plano`}
          aria-current={activeRoom === roomId ? 'page' : undefined} style={{ left: marker.x + '%', top: marker.y + '%' }} onClick={revealRoom}>
          <Icon size={15} aria-hidden="true" />
          <span className={marker.short ? 'atlas-pin-full' : undefined}>{marker.name}</span>
          {marker.short && <span className="atlas-pin-short">{marker.short}</span>}
        </Link>;
      })}
    </div>}
    {spaces.length > 0 && <nav className="atlas-index" aria-label="Espacios de la casa">{links(spaces)}</nav>}
    {other.length > 0 && <nav className="atlas-index atlas-index-other" aria-label="Partidas generales">{links(other)}</nav>}
    {children && <div className="atlas-footer">{children}</div>}
  </section>;
}
