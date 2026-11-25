import { URL } from 'node:url';

const BLOCKED_HOSTS = new Set(['localhost', '127.0.0.1', '0.0.0.0', '[::1]']);
const PRIVATE_IPV4 = /^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)/;

export class SsrfBlockedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SsrfBlockedError';
  }
}

export function assertPublicHttpUrl(raw: string): URL {
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new SsrfBlockedError('Invalid URL');
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new SsrfBlockedError('Only http/https URLs are allowed');
  }
  const host = parsed.hostname.toLowerCase();
  if (BLOCKED_HOSTS.has(host) || host.endsWith('.local')) {
    throw new SsrfBlockedError('Private or local hosts are not allowed');
  }
  if (PRIVATE_IPV4.test(host)) {
    throw new SsrfBlockedError('Private IP ranges are not allowed');
  }
  return parsed;
}

export function matchesStoreWebsite(url: URL, website: URL): boolean {
  const host = url.hostname.replace(/^www\./, '');
  const domain = website.hostname.replace(/^www\./, '');
  return url.protocol === 'https:' && !url.port && !url.username && !url.password
    && (host === domain || host.endsWith(`.${domain}`));
}
