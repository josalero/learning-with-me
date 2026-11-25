WITH templates(id, category, parts) AS (
  VALUES
    ('i-cookware', 'menaje', '[
      {"id":"ollas","name":"Juego de ollas con tapa","purchaseUnit":"set"},
      {"id":"sarten","name":"Sartén antiadherente","purchaseUnit":"unit"},
      {"id":"utensilios","name":"Juego de utensilios de cocina","purchaseUnit":"set","notes":"Espátula, cucharón y pinzas."},
      {"id":"vajilla","name":"Juego de vajilla","purchaseUnit":"set","notes":"Platos y tazas para la capacidad del apartamento."},
      {"id":"cubiertos","name":"Juego de cubiertos","purchaseUnit":"set","notes":"Cucharas, tenedores, cuchillos y cucharillas."}
    ]'::jsonb),
    ('i-linens', 'textil', '[
      {"id":"sabanas-queen","name":"Juego de sábanas queen","purchaseUnit":"set","qty":1,"zoneId":"z-master"},
      {"id":"sabanas-dormitorio-2","name":"Juego de sábanas para dormitorio 2","purchaseUnit":"set","qty":1,"zoneId":"z-br2","notes":"Confirmar la talla de la cama entre full y queen."},
      {"id":"sabanas-individual","name":"Juego de sábanas individual","purchaseUnit":"set","qty":1,"zoneId":"z-br3"},
      {"id":"toallas","name":"Juego de toallas","purchaseUnit":"set","zoneId":"z-baths"}
    ]'::jsonb)
), entries AS (
  SELECT p.id AS project_id, entry.item, entry.position, template.parts, p.payload->'zones' AS zones
  FROM projects p
  CROSS JOIN LATERAL jsonb_array_elements(p.payload->'items') WITH ORDINALITY AS entry(item, position)
  LEFT JOIN templates template
    ON entry.item->>'id' = template.id AND entry.item->>'category' = template.category
    AND NOT jsonb_path_exists(COALESCE(entry.item->'quotes', '[]'::jsonb), '$[*] ? (@.basis == "published")')
), archive AS (
  SELECT project_id, jsonb_agg(item ORDER BY position) AS bundles
  FROM entries WHERE parts IS NOT NULL GROUP BY project_id
), expanded AS (
  SELECT entry.project_id, jsonb_agg(next.item ORDER BY entry.position, next.position) AS items
  FROM entries entry JOIN archive USING (project_id)
  CROSS JOIN LATERAL (
    SELECT entry.item, 0 AS position WHERE entry.parts IS NULL
    UNION ALL
    SELECT part.value || jsonb_build_object(
      'id', (entry.item->>'id') || '-' || (part.value->>'id'),
      'qty', COALESCE(part.value->'qty', entry.item->'qty'),
      'zoneId', target.zone_id,
      'category', entry.item->'category',
      'parentItemId', entry.item->'id',
      'quotes', '[]'::jsonb,
      'unquotedReason', 'Pendiente de cotizar individualmente.'
    ), part.position
    FROM jsonb_array_elements(entry.parts) WITH ORDINALITY AS part(value, position)
    CROSS JOIN LATERAL (
      SELECT COALESCE((
        SELECT zone->>'id' FROM jsonb_array_elements(entry.zones) zone
        WHERE zone->>'id' = part.value->>'zoneId' LIMIT 1
      ), entry.item->>'zoneId') AS zone_id
    ) target
    WHERE NOT EXISTS (
      SELECT 1 FROM entries existing
      WHERE existing.project_id = entry.project_id
        AND (
          existing.item->>'id' = (entry.item->>'id') || '-' || (part.value->>'id')
          OR (existing.item->>'zoneId' = target.zone_id AND lower(existing.item->>'name') = lower(part.value->>'name'))
        )
    )
  ) next
  GROUP BY entry.project_id
)
UPDATE projects p
SET payload = jsonb_set(
  jsonb_set(p.payload, '{items}', expanded.items),
  '{itemizedBundles}', COALESCE(p.payload->'itemizedBundles', '[]'::jsonb) || archive.bundles
), updated_at = NOW()
FROM expanded JOIN archive ON archive.project_id = expanded.project_id
WHERE p.id = expanded.project_id;
