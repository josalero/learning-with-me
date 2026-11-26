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

  it('uses the exact product page and reads that product’s aggregate reviews and dimensions', () => {
    const html = `<script type="application/ld+json">
      {"@type":"Product","url":"https://example.com/model-a","name":"Modelo A",
       "aggregateRating":{"ratingValue":4.6,"reviewCount":30},
       "depth":{"@type":"QuantitativeValue","value":70.9,"unitCode":"CMT"},
       "offers":{"@type":"Offer","price":280000,"priceCurrency":"CRC"}}
      </script>`;
    expect(parseJsonLdOffers(html, 'https://example.com/refrigeradoras')).toEqual([]);
    const [product] = parseJsonLdOffers(html, 'https://example.com/model-a');
    expect(product?.review).toEqual({ rating: 4.6, count: 30, sourceUrl: 'https://example.com/model-a' });
    expect(product?.dimensions?.d).toBeCloseTo(.709);
  });
});

it('keeps variant links and the price published for the exact shower door width', () => {
  const base = 'https://glassmarcr.com/products/ducha';
  const offers = ['11', '22'].map((variant, index) => ({
    '@type':'Offer', url:base+'?variant='+variant, price:155000+index*13300, priceCurrency:'CRC',
  }));
  const html = '<script type="application/ld+json">'+JSON.stringify({'@type':'Product',name:'Puerta Atlantis',offers})+'</script>';
  expect(parseJsonLdOffers(html,base+'?variant=22')).toMatchObject([{productUrl:base+'?variant=22',priceCrc:168300}]);
  expect(parseJsonLdOffers(html,base+'?variant=33')).toEqual([]);
});
