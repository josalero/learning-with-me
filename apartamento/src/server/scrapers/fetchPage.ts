import { lookup } from 'node:dns/promises';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { BlockList, type LookupFunction } from 'node:net';
import { assertPublicHttpUrl, matchesStoreWebsite } from './ssrf-guard.js';
import { parseJsonLdOffers, parseProductReviews } from './jsonld-offer.js';

const MAX_BODY_BYTES = 2 * 1024 * 1024;
const nonPublic = new BlockList();
for (const [address, prefix] of [
  ['0.0.0.0', 8], ['10.0.0.0', 8], ['100.64.0.0', 10], ['127.0.0.0', 8], ['169.254.0.0', 16],
  ['172.16.0.0', 12], ['192.0.0.0', 24], ['192.0.2.0', 24], ['192.168.0.0', 16], ['198.18.0.0', 15],
  ['198.51.100.0', 24], ['203.0.113.0', 24], ['224.0.0.0', 4], ['240.0.0.0', 4],
] as const) nonPublic.addSubnet(address, prefix, 'ipv4');
for (const [address, prefix] of [['2001::', 32], ['2001:db8::', 32], ['2002::', 16]] as const) {
  nonPublic.addSubnet(address, prefix, 'ipv6');
}
const publicV6 = new BlockList();
publicV6.addSubnet('2000::', 3, 'ipv6');

async function publicDestination(url: URL): Promise<LookupFunction> {
  const hostname = url.hostname.replace(/^\[|\]$/g, '');
  const addresses = await lookup(hostname, { all: true });
  const publicAddresses = addresses.filter(({ address, family }) => family === 4
    ? !nonPublic.check(address, 'ipv4')
    : publicV6.check(address, 'ipv6') && !nonPublic.check(address, 'ipv6'));
  const first = publicAddresses[0];
  if (!first || publicAddresses.length !== addresses.length || url.username || url.password) throw new Error('Invalid product host');

  // Reuse the validated addresses for the connection and keep the original hostname for TLS.
  return (_host, options, done) => {
    const matching = options.family ? publicAddresses.filter(({ family }) => family === options.family) : publicAddresses;
    const address = matching[0] ?? first;
    if (options.all) done(null, matching.length ? matching : [address]);
    else done(null, address.address, address.family);
  };
}

async function readPage(url: URL, signal: AbortSignal): Promise<{ redirect: string } | { html: string }> {
  const verifiedLookup = await publicDestination(url);
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const request = (url.protocol === 'https:' ? httpsRequest : httpRequest)(url, {
      signal, lookup: verifiedLookup, agent: false,
      headers: { 'user-agent': 'NatarundiBudgetBot/0.1', accept: 'text/html,application/xhtml+xml' },
    }, (response) => {
      const status = response.statusCode ?? 0;
      const location = response.headers.location;
      if (location && [301, 302, 303, 307, 308].includes(status)) {
        response.destroy();
        resolve({ redirect: location });
        return;
      }
      if (status < 200 || status >= 300) {
        response.destroy();
        reject(new Error(`HTTP ${status}`));
        return;
      }
      const chunks: Buffer[] = [];
      let size = 0;
      response.on('error', reject);
      response.on('data', (chunk: Buffer) => {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) response.destroy(new Error('Product page too large'));
        else chunks.push(chunk);
      });
      response.on('end', () => resolve({ html: Buffer.concat(chunks).toString('utf8') }));
    });
    request.on('error', reject);
    request.end();
  });
}

async function fetchProductPage(pageUrl: string, website?: URL) {
  const signal = AbortSignal.timeout(15_000);
  let url = assertPublicHttpUrl(pageUrl);
  for (let redirects = 0; redirects <= 3; redirects++) {
    if (website && !matchesStoreWebsite(url, website)) throw new Error('Product page is outside the store website');
    const response = await readPage(url, signal);
    if ('html' in response) return { html: response.html, url: url.href };
    url = assertPublicHttpUrl(new URL(response.redirect, url).toString());
  }
  throw new Error('Too many redirects');
}

export async function fetchJsonLdOffers(pageUrl: string, website?: URL) {
  const { html, url } = await fetchProductPage(pageUrl, website);
  return parseJsonLdOffers(html, url);
}

export async function fetchProductReviews(pageUrl: string) {
  const { html, url } = await fetchProductPage(pageUrl);
  return parseProductReviews(html, url);
}
