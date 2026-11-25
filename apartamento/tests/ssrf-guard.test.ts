import { describe, expect, it } from 'vitest';
import { assertPublicHttpUrl, SsrfBlockedError } from '../src/server/scrapers/ssrf-guard.js';

describe('assertPublicHttpUrl', () => {
  it('allows public https URLs', () => {
    const u = assertPublicHttpUrl('https://www.misterprecios.cr/');
    expect(u.hostname).toBe('www.misterprecios.cr');
  });

  it('blocks localhost', () => {
    expect(() => assertPublicHttpUrl('http://127.0.0.1/')).toThrow(SsrfBlockedError);
  });
});