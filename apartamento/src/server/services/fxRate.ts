import { assertPublicHttpUrl } from '../scrapers/ssrf-guard.js';

export type FxField = 'compra' | 'venta';
export type FxProvider = 'bccr-proxy' | 'bccr-sdde' | 'exchangerate-api' | 'static';

export type FxSnapshot = {
  rate: number;
  field: FxField;
  provider: FxProvider;
  sourceDate: string | null;
  fetchedAt: string;
  fallback: boolean;
};

const FETCH_TIMEOUT_MS = 12_000;
let cached: { snap: FxSnapshot; expiresAt: number } | undefined;

function fallbackRate(): number {
  const raw = process.env.DEFAULT_USD_CRC_RATE ?? '520';
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : 520;
}

function ttlMs(): number {
  const minutes = Number(process.env.FX_CACHE_TTL_MINUTES ?? '360');
  return Math.max(5, minutes) * 60_000;
}

function field(): FxField {
  return process.env.FX_USE_FIELD === 'compra' ? 'compra' : 'venta';
}

function provider(): FxProvider {
  const p = process.env.FX_PROVIDER ?? 'bccr-proxy';
  if (p === 'bccr-sdde' || p === 'exchangerate-api' || p === 'static') return p;
  return 'bccr-proxy';
}

async function fetchJson(url: string): Promise<unknown> {
  const parsed = assertPublicHttpUrl(url);
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(parsed.toString(), {
      signal: ctrl.signal,
      headers: {
        accept: 'application/json',
        'user-agent': 'NatarundiBudgetBot/0.1 (fx-rate)',
      },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

async function fromBccrProxy(useField: FxField): Promise<{ rate: number; sourceDate: string | null }> {
  const data = (await fetchJson('https://tipodecambio.paginasweb.cr/api')) as {
    compra?: number;
    venta?: number;
    fecha?: string;
  };
  const rate = useField === 'compra' ? data.compra : data.venta;
  if (!rate || !Number.isFinite(rate)) throw new Error('Invalid BCCR proxy payload');
  return { rate, sourceDate: data.fecha ?? null };
}

async function fromExchangeRateApi(): Promise<{ rate: number; sourceDate: string | null }> {
  const data = (await fetchJson('https://api.exchangerate-api.com/v4/latest/USD')) as {
    date?: string;
    rates?: { CRC?: number };
  };
  const rate = data.rates?.CRC;
  if (!rate || !Number.isFinite(rate)) throw new Error('Invalid exchangerate-api payload');
  return { rate, sourceDate: data.date ?? null };
}

function costaRicaDateIso(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Costa_Rica',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const y = parts.find((p) => p.type === 'year')?.value;
  const m = parts.find((p) => p.type === 'month')?.value;
  const d = parts.find((p) => p.type === 'day')?.value;
  return `${y}-${m}-${d}`;
}

async function fromBccrSdde(useField: FxField): Promise<{ rate: number; sourceDate: string | null }> {
  const token = process.env.BCCR_API_TOKEN;
  if (!token) throw new Error('BCCR_API_TOKEN is required for bccr-sdde');
  const code = useField === 'compra' ? '317' : '318';
  const day = costaRicaDateIso();
  const base = 'https://apim.bccr.fi.cr/SDDE/api/Bccr.Ge.SDDE.Publico.Indicadores.API';
  const url = `${base}/indicadoresEconomicos/${code}/series?fechaInicio=${day}&fechaFin=${day}&idioma=ES`;
  const parsed = assertPublicHttpUrl(url);
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(parsed.toString(), {
      signal: ctrl.signal,
      headers: {
        accept: 'application/json',
        authorization: `Bearer ${token}`,
        'user-agent': 'NatarundiBudgetBot/0.1 (fx-rate)',
      },
    });
    if (!res.ok) throw new Error(`BCCR SDDE HTTP ${res.status}`);
    const body = (await res.json()) as {
      datos?: Array<{ series?: Array<{ fecha?: string; valorDatoPorPeriodo?: number }> }>;
    };
    const series = body.datos?.[0]?.series ?? [];
    const last = series[series.length - 1];
    const rate = last?.valorDatoPorPeriodo;
    if (!rate || !Number.isFinite(rate)) throw new Error('No BCCR SDDE rate for date');
    return { rate, sourceDate: last.fecha ?? day };
  } finally {
    clearTimeout(timer);
  }
}

export function getCachedFxSnapshot(): FxSnapshot | null {
  if (!cached || cached.expiresAt < Date.now()) return null;
  return cached.snap;
}

export async function fetchUsdCrcRate(force = false): Promise<FxSnapshot> {
  if (!force) {
    const hit = getCachedFxSnapshot();
    if (hit) return hit;
  }

  const useField = field();
  const prov = provider();
  const fetchedAt = new Date().toISOString();

  if (prov === 'static') {
    const snap: FxSnapshot = {
      rate: fallbackRate(),
      field: useField,
      provider: prov,
      sourceDate: null,
      fetchedAt,
      fallback: true,
    };
    cached = { snap, expiresAt: Date.now() + ttlMs() };
    return snap;
  }

  try {
    const parsed =
      prov === 'exchangerate-api'
        ? await fromExchangeRateApi()
        : prov === 'bccr-sdde'
          ? await fromBccrSdde(useField)
          : await fromBccrProxy(useField);
    const snap: FxSnapshot = {
      rate: Math.round(parsed.rate * 100) / 100,
      field: useField,
      provider: prov,
      sourceDate: parsed.sourceDate,
      fetchedAt,
      fallback: false,
    };
    cached = { snap, expiresAt: Date.now() + ttlMs() };
    return snap;
  } catch {
    const snap: FxSnapshot = {
      rate: fallbackRate(),
      field: useField,
      provider: prov,
      sourceDate: null,
      fetchedAt,
      fallback: true,
    };
    cached = { snap, expiresAt: Date.now() + 60_000 };
    return snap;
  }
}