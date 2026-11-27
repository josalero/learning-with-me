WITH inventory AS (
  SELECT p.id,
    jsonb_agg(entry.item ORDER BY entry.position) FILTER (WHERE NOT entry.legacy) AS active_items,
    jsonb_agg(entry.item ORDER BY entry.position) FILTER (WHERE entry.legacy) AS archived_items,
    bool_or(entry.item->>'id' = 'i-laundry-tower') AS has_tower
  FROM projects p
  CROSS JOIN LATERAL (
    SELECT item, position, coalesce(item->>'zoneId' = 'z-laundry' AND item->>'category' = 'electrodomesticos'
      AND item->>'id' IN ('i-washer', 'i-dryer'), false) AS legacy
    FROM jsonb_array_elements(p.payload->'items') WITH ORDINALITY AS stored(item, position)
  ) entry
  WHERE p.name = 'Condominio Natarundi — Portegolpe'
    AND p.address = 'Condominio Natarundi, Portegolpe, Guanacaste'
  GROUP BY p.id
  HAVING bool_or(entry.legacy)
)
UPDATE projects p
SET payload = p.payload || jsonb_build_object(
  'items', coalesce(inventory.active_items, '[]'::jsonb) || CASE WHEN inventory.has_tower THEN '[]'::jsonb ELSE
    '[{"id":"i-laundry-tower","zoneId":"z-laundry","name":"Centro de lavado eléctrico vertical","category":"electrodomesticos","qty":1,"notes":"Confirmar las medidas libres del nicho, toma eléctrica, ventilación y costo de instalación y entrega en Portegolpe.","quotes":[]}]'::jsonb END,
  'itemizedBundles', coalesce(p.payload->'itemizedBundles', '[]'::jsonb) || inventory.archived_items
), updated_at = now()
FROM inventory
WHERE p.id = inventory.id;
