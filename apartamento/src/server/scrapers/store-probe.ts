import { assertPublicHttpUrl } from './ssrf-guard.js';

export type ProbeResult = {
  ok: boolean;
  kind: 'shopify' | 'vtex' | 'woocommerce' | 'json_ld' | 'ssr_price' | 'unknown';
  sample?: string;
  error?: string;
};

const FETCH_TIMEOUT_MS = 12_000;

async function fetchWithTimeout(url: string): Promise<string> {
  const parsed = assertPublicHttpUrl(url);
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(parsed.toString(), {
      signal: ctrl.signal,
      headers: { 'user-agent': 'NatarundiBudgetBot/0.1 (+local; price-estimate)' },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } finally {
    clearTimeout(timer);
  }
}

export async function probeStoreHomepage(homeUrl: string): Promise<ProbeResult> {
  try {
    const html = await fetchWithTimeout(homeUrl);
    if (html.includes('cdn.shopify.com') || html.includes('Shopify.theme')) {
      return { ok: true, kind: 'shopify', sample: 'Shopify CDN detected' };
    }
    if (html.includes('vtex') || html.includes('VTEX')) {
      return { ok: true, kind: 'vtex', sample: 'VTEX markers' };
    }
    if (html.includes('wp-content') || html.includes('woocommerce')) {
      return { ok: true, kind: 'woocommerce', sample: 'WooCommerce markers' };
    }
    if (html.includes('application/ld+json')) {
      return { ok: true, kind: 'json_ld', sample: 'JSON-LD scripts' };
    }
    if (html.includes('data-price-amount') || html.includes('product-price')) {
      return { ok: true, kind: 'ssr_price', sample: 'SSR price attributes' };
    }
    return { ok: true, kind: 'unknown' };
  } catch (e) {
    const message = e instanceof Error ? e.message : 'probe failed';
    return { ok: false, kind: 'unknown', error: message };
  }
}