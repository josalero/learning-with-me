import { expect, it } from 'vitest';
import { buildAirbnbAssessment } from '../src/domain/airbnb-assessment.js';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { itemizeCatalogBundles } from '../src/domain/itemize-bundle.js';

const project = itemizeCatalogBundles(buildNatarundiProject('w', 'p'));
it('ties the third bed discrepancy to its actual drawing and keeps the essentials pending', () => {
  const { checks } = buildAirbnbAssessment(project, [{ id: 'r', title: 'Vista 3D cenital', assetName: 'mapa-3d-cenital.png' }]);
  expect(checks[0]).toMatchObject({ status: 'discrepancy', evidence: [ { reference: { id: 'r' } }, { text: expect.stringContaining('Cama individual') } ] });
  const basics = checks.find((check) => check.id === 'essentials')!;
  expect(basics.status).toBe('pending');
  expect(basics.evidence[0]?.text).toContain('sábanas, toallas');
  expect(basics.evidence[1]?.text).toContain('jabón de manos');
});
it('uses visual findings only for references still in the project, regardless of their filename', () => {
  const result = buildAirbnbAssessment(project, [{ id: 'new', title: 'mapa-3d-cenital.png', assetName: null }], [
    { referenceId: 'new', topic: 'kitchen', detail: 'Se distingue una isla.' },
    { referenceId: 'removed', topic: 'sleeping', detail: 'Una litera.' },
  ]);
  expect(result.checks[0]).toMatchObject({ status: 'pending', evidence: [] });
  expect(result.checks.find((c) => c.id === 'kitchen')).toMatchObject({ status: 'documented', evidence: [{ reference: { id: 'new' } }] });
});
