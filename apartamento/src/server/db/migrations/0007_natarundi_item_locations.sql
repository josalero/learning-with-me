WITH target_projects AS (
  SELECT id, payload
  FROM projects
  WHERE name = 'Condominio Natarundi — Portegolpe'
    AND address = 'Condominio Natarundi, Portegolpe, Guanacaste'
), tv_splits AS (
  SELECT p.id, original.item AS television, child.item AS incorrect_sala
  FROM target_projects p
  CROSS JOIN LATERAL (
    SELECT archived.item
    FROM jsonb_array_elements(p.payload->'itemizedBundles') WITH ORDINALITY AS archived(item, position)
    WHERE archived.item->>'id' = 'i-tv' AND archived.item->>'zoneId' = 'z-living' AND archived.item->>'category' = 'electronica'
    ORDER BY archived.position DESC LIMIT 1
  ) original
  CROSS JOIN LATERAL (
    SELECT active.item
    FROM jsonb_array_elements(p.payload->'items') WITH ORDINALITY AS active(item, position)
    WHERE active.item->>'parentItemId' = 'i-tv' AND active.item->>'zoneId' = 'z-living'
      AND lower(trim(active.item->>'name')) = 'sala'
    ORDER BY active.position LIMIT 1
  ) child
  WHERE NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements(p.payload->'items') AS candidate(item) WHERE candidate.item->>'id' = 'i-tv'
  ) AND EXISTS (
    SELECT 1 FROM jsonb_array_elements(p.payload->'items') AS candidate(item)
    WHERE candidate.item->>'parentItemId' = 'i-tv' AND lower(trim(candidate.item->>'name')) = 'dormitorio'
  )
), repaired AS (
  SELECT p.id, tv.incorrect_sala, entry.item, entry.position,
    CASE WHEN entry.item->>'zoneId' = 'z-living' THEN
      CASE
        WHEN entry.item->>'id' = 'i-freight' AND entry.item->>'category' = 'logistica'
          THEN 'z-logistics'
        WHEN NULLIF(entry.item->>'parentItemId', '') IS NOT NULL
          THEN CASE lower(trim(entry.item->>'name'))
            WHEN 'dormitorio' THEN ''
            WHEN 'dormitorio principal' THEN 'z-master'
            WHEN 'dormitorio 2' THEN 'z-br2'
            WHEN 'dormitorio 3' THEN 'z-br3'
          END
      END
    END AS destination,
    CASE
      WHEN entry.item->>'id' = tv.incorrect_sala->>'id'
        THEN tv.television || jsonb_build_object('qty', entry.item->'qty')
      WHEN tv.id IS NOT NULL AND entry.item->>'parentItemId' = 'i-tv'
        AND lower(trim(entry.item->>'name')) = 'dormitorio'
        AND jsonb_array_length(COALESCE(entry.item->'quotes', '[]'::jsonb)) = 0
        THEN (entry.item - 'lastSearch') || jsonb_build_object('name', 'Televisor para dormitorio')
      ELSE entry.item
    END AS corrected_item
  FROM target_projects p
  CROSS JOIN LATERAL jsonb_array_elements(p.payload->'items') WITH ORDINALITY AS entry(item, position)
  LEFT JOIN tv_splits tv ON tv.id = p.id
), located AS (
  SELECT repaired.*,
    CASE WHEN repaired.destination IN ('', 'z-logistics') OR EXISTS (
      SELECT 1 FROM jsonb_array_elements(p.payload->'zones') zone WHERE zone->>'id' = repaired.destination
    ) THEN repaired.destination END AS valid_destination
  FROM repaired JOIN target_projects p USING (id)
), changed AS (
  SELECT id, incorrect_sala, jsonb_agg(
    CASE WHEN valid_destination IS NULL THEN corrected_item
         ELSE jsonb_set(corrected_item, '{zoneId}', to_jsonb(valid_destination)) END
    ORDER BY position
  ) AS items, bool_or(valid_destination = 'z-logistics') AS needs_logistics
  FROM located
  GROUP BY id, incorrect_sala
  HAVING bool_or(valid_destination IS NOT NULL OR item IS DISTINCT FROM corrected_item)
)
UPDATE projects p
SET payload = jsonb_set(
  jsonb_set(
    jsonb_set(p.payload, '{items}', changed.items), '{zones}',
    CASE WHEN changed.needs_logistics AND NOT EXISTS (
      SELECT 1 FROM jsonb_array_elements(p.payload->'zones') zone WHERE zone->>'id' = 'z-logistics'
    ) THEN p.payload->'zones' || '[{"id":"z-logistics","slug":"logistica-del-proyecto","labelEs":"Logística del proyecto"}]'::jsonb
    ELSE p.payload->'zones' END
  ), '{itemizedBundles}',
  COALESCE(p.payload->'itemizedBundles', '[]'::jsonb) ||
    CASE WHEN changed.incorrect_sala IS NOT NULL THEN jsonb_build_array(changed.incorrect_sala) ELSE '[]'::jsonb END
), updated_at = now()
FROM changed
WHERE p.id = changed.id;
