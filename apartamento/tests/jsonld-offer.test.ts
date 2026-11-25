import { describe, expect, it } from 'vitest';
import { parseJsonLdOffers } from '../src/server/scrapers/jsonld-offer.js';

describe('parseJsonLdOffers', () => {
  it('extracts AggregateOffer lowPrice in CRC', () => {
    const html = `<html><head><title>X</title>
      <script type="application/ld+json">
      {"@type":"Product","name":"Abanico","offers":{"@type":"AggregateOffer","lowPrice":"89900","priceCurrency":"CRC"}}
      </script></head><body></body></html>`;
    const offers = parseJsonLdOffers(html, 'https://example.com/p');
    expect(offers).toHaveLength(1);
    expect(offers[0]?.priceCrc).toBe(89_900);
    expect(offers[0]?.title).toBe('Abanico');
  });

  it('does not convert category-card prices into a quote for the category URL', () => {
    const html = `<script type="application/ld+json">
      {"@type":"ItemList","itemListElement":[
        {"@type":"ListItem","item":{"@type":"Product","name":"Abanico","offers":{"@type":"Offer","price":89900,"priceCurrency":"CRC"}}}
      ]}
      </script>`;
    expect(parseJsonLdOffers(html, 'https://example.com/abanicos')).toEqual([]);
  });

  it('uses the local tax-inclusive price shown on EPA product pages', () => {
    const html = `<main><p data-epa-price-type class="tax-inclusive-price">¢68.950</p></main>
      <script type="application/ld+json">
      {"@type":"Product","name":"Abanico","offers":{"@type":"AggregateOffer","price":"61.018","priceCurrency":"CRC"}}
      </script>`;
    expect(parseJsonLdOffers(html, 'https://cr.epaenlinea.com/es-cr/products/abanico')[0]?.priceCrc).toBe(68_950);
  });
});
