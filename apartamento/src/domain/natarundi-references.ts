import type { ProjectReference } from '../shared/types.js';

export const NATARUNDI_REFERENCES = [
  { title: 'Render de sala y cocina', mediaType: 'image/jpeg', assetName: 'plano-medidas.jpg' },
  { title: 'Plano con medidas', mediaType: 'image/jpeg', assetName: 'render-sala-cocina.jpg' },
  { title: 'Vista 3D cenital', mediaType: 'image/png', assetName: 'mapa-3d-cenital.png' },
] satisfies Array<Pick<ProjectReference, 'title' | 'mediaType'> & { assetName: string }>;
