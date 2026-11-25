import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchUsdCrcRate } from '../src/server/services/fxRate.js';

describe('fetchUsdCrcRate', () => {
  const env = { ...process.env };

  beforeEach(() => {
    process.env.FX_PROVIDER = 'bccr-proxy';
    process.env.FX_USE_FIELD = 'venta';
    process.env.DEFAULT_USD_CRC_RATE = '520';
    process.env.FX_CACHE_TTL_MINUTES = '60';
  });

  afterEach(() => {
    process.env = { ...env };
    vi.unstubAllGlobals();
  });

  it('parses venta from BCCR proxy', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: true,
        json: async () => ({ compra: 441.1, venta: 446.3, fecha: '25/11/2026' }),
      })),
    );
    const snap = await fetchUsdCrcRate(true);
    expect(snap.rate).toBe(446.3);
    expect(snap.fallback).toBe(false);
    expect(snap.provider).toBe('bccr-proxy');
  });

  it('falls back when provider fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 503 })));
    const snap = await fetchUsdCrcRate(true);
    expect(snap.rate).toBe(520);
    expect(snap.fallback).toBe(true);
  });
});