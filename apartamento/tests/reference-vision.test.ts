import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { analyzeProjectImages } from '../src/server/services/referenceVision.js';

const referenceId = 'abcd1234-abcd-4234-8234-abcd1234abcd';
const otherReference = '1234abcd-1234-4abc-8abc-1234abcd1234';

afterEach(() => vi.unstubAllGlobals());

describe('vision suggestions', () => {
  it('sends the selected image and excludes duplicate items or references from other analyses', async () => {
    const project = buildNatarundiProject('workspace', 'project');
    const finding = { name: 'Mampara de ducha', zoneId: 'z-baths', qty: 2, reason: 'El plano muestra dos mamparas.', referenceId };
    const fakeFetch = vi.fn(async () => Response.json({ choices: [{ message: { content: JSON.stringify({
      observations: [
        { topic: 'bathrooms', referenceId, detail: 'Se distinguen dos duchas.' },
        { topic: 'bathrooms', referenceId: otherReference, detail: 'Otra referencia.' },
      ],
      suggestions: [
        finding,
        { ...finding, name: 'MAMPARA de ducha' },
        { ...finding, name: 'Refrigeradora (nicho 1.10 m)', zoneId: 'z-kitchen' },
        { ...finding, name: 'Mesa auxiliar', referenceId: otherReference },
        { ...finding, name: 'Repisa', zoneId: 'zona-errónea' },
      ],
    }) } }] }));
    vi.stubGlobal('fetch', fakeFetch);
    const findings = await analyzeProjectImages(project, [{ id: referenceId, title: 'Plano', mediaType: 'image/png', content: Buffer.from('image') }]);
    expect(findings.observations).toEqual([{ topic: 'bathrooms', referenceId, detail: 'Se distinguen dos duchas.' }]);
    expect(findings.suggestions).toMatchObject([finding, { name: 'Repisa', zoneId: null }]);
    expect(fakeFetch).toHaveBeenCalledWith('https://openrouter.ai/api/v1/chat/completions', expect.objectContaining({
      body: expect.stringContaining('data:image/png;base64,aW1hZ2U='),
    }));
  });
});
