-- Register sources of the opening inventory for each existing Natarundi workspace.
-- migrate.ts then itemizes the bathrooms and fills missing price tiers in the same transaction.
INSERT INTO regional_stores (id, workspace_id, name, region, website_url, probe_kind)
SELECT DISTINCT s.code, p.workspace_id, s.name, 'san-jose', s.website, s.kind
FROM projects p CROSS JOIN (VALUES
  ('monge-cr', 'Monge', 'https://www.tiendamonge.com/', 'json_ld'),
  ('glassmar-cr', 'Glassmar', 'https://glassmarcr.com/', 'shopify')
) s(code, name, website, kind)
WHERE p.name = 'Condominio Natarundi — Portegolpe' AND p.address = 'Condominio Natarundi, Portegolpe, Guanacaste'
  AND NOT EXISTS (SELECT 1 FROM regional_stores r WHERE r.workspace_id = p.workspace_id AND r.name = s.name)
ON CONFLICT DO NOTHING;

-- Archive the specific false matches found in the earlier automatic results.
-- If the selected product was incompatible, keep its full comparison in history and reprice that tier.
WITH mismatches(item_id, product_url) AS (VALUES
  ('i-cookware-sarten', 'https://cr.siman.com/set-de-sarten-2-piezas-20-26-cm-104660318/p'),
  ('i-cookware-utensilios', 'https://cr.epaenlinea.com/es-cr/products/set-3-utensilios-de-parrilla-deluxe-acero-inoxidable'),
  ('i-cookware-cubiertos', 'https://cr.epaenlinea.com/es-cr/products/set-8-cubiertos-acero-inoxidable-y-madera-parawood-jim-beam'),
  ('i-island-stools', 'https://cr.epaenlinea.com/es-cr/products/silla-de-cipres-mediana-65-cm'),
  ('i-dining-set', 'https://cr.epaenlinea.com/es-cr/products/set-de-terraza-6-piezas-gris-basic-living'),
  ('i-master-bed', 'https://cr.epaenlinea.com/es-cr/products/cama-queen-kaia-conac-160-x-52-x-213-cm'),
  ('i-br2-bed', 'https://cr.epaenlinea.com/es-cr/products/cama-queen-kaia-conac-160-x-52-x-213-cm'),
  ('i-br3-bed', 'https://cr.epaenlinea.com/es-cr/products/cama-napoles-wengue-953-x-2208-x-1122-cm'),
  ('i-br3-desk', 'https://cr.epaenlinea.com/es-cr/products/mesa-auxiliar-plegable-de-madera-roma-negra-35-x-48-x-61-cm')
), observations AS (
  SELECT p.id, i->>'id' AS item_id, q, COALESCE(q->>'tier','balanced') AS tier,
    row_number() OVER (PARTITION BY p.id, i->>'id', COALESCE(q->>'tier','balanced') ORDER BY n) AS priority
  FROM projects p CROSS JOIN LATERAL jsonb_array_elements(p.payload->'items') i
  CROSS JOIN LATERAL jsonb_array_elements(i->'quotes') WITH ORDINALITY prices(q,n)
  WHERE p.name = 'Condominio Natarundi — Portegolpe' AND p.address = 'Condominio Natarundi, Portegolpe, Guanacaste'
), wrong AS (
  SELECT o.* FROM observations o JOIN mismatches m ON m.item_id=o.item_id AND m.product_url=o.q->>'productUrl'
), affected AS (
  SELECT o.* FROM observations o WHERE EXISTS (
    SELECT 1 FROM wrong w WHERE w.id=o.id AND w.item_id=o.item_id AND w.tier=o.tier AND (w.priority=1 OR w.q=o.q)
  )
), archive AS (
  INSERT INTO archived_apartment_quotes(project_id, item_id, item_collection, quote)
  SELECT id, item_id, 'items', q FROM affected RETURNING project_id, item_id, quote
)
UPDATE projects p SET payload = jsonb_set(payload, '{items}', (
  SELECT jsonb_agg(jsonb_set(i, '{quotes}', COALESCE((
    SELECT jsonb_agg(q ORDER BY pos) FROM jsonb_array_elements(i->'quotes') WITH ORDINALITY x(q,pos)
    WHERE NOT EXISTS (SELECT 1 FROM archive a WHERE a.project_id=p.id AND a.item_id=i->>'id' AND a.quote=q)
  ), '[]'::jsonb)) ORDER BY position)
  FROM jsonb_array_elements(payload->'items') WITH ORDINALITY items(i,position)
)) WHERE p.id IN (SELECT project_id FROM archive);
