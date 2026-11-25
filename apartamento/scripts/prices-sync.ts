/**
 * CLI: optional live sync for a single comparator URL (JSON-LD).
 * Usage: npx tsx scripts/prices-sync.ts <https-url>
 */
import { fetchJsonLdOffers } from '../src/server/scrapers/fetchPage.js';

const url = process.argv[2];
if (!url) {
  console.error('Usage: npx tsx scripts/prices-sync.ts <product-url>');
  process.exit(1);
}

const offers = await fetchJsonLdOffers(url);
console.log(JSON.stringify(offers, null, 2));