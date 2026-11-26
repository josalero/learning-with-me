-- HTTP verification happens at capture time. Enforce the evidence and URL on every write, including direct SQL writes.
CREATE FUNCTION apartment_valid_quote(q jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
  SELECT COALESCE(
    jsonb_typeof(q) = 'object' AND q->>'basis' = 'published'
    AND jsonb_typeof(q->'priceCrc') = 'number' AND (q->'priceCrc') > '0'::jsonb
    AND jsonb_typeof(q->'productUrl') = 'string'
    AND (q->>'productUrl') ~* '^https?://([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?/[^/?#[:space:]][^[:space:]]*$'
    AND (q->>'productUrl') !~* '^https?://[^/]+/([a-z]{2}(-[a-z]{2})?/)?(collections|categor(y|ia|ies)|product-category|catalogsearch|buscar|search|c)(/|[?#]|$)'
    AND (q->>'productUrl') !~* '^https?://[^/]+/[a-z]{2}(-[a-z]{2})?/?([?#].*)?$'
    AND split_part(rtrim(q->>'productUrl','/'), '#', 1) IS DISTINCT FROM rtrim(q->>'storeUrl','/'),
    false);
$$;

CREATE TABLE archived_apartment_quotes (
  project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  item_id text NOT NULL,
  item_collection text NOT NULL,
  quote jsonb NOT NULL,
  archived_at timestamptz NOT NULL DEFAULT now()
);

WITH items AS (
  SELECT p.id, k, i, pos
  FROM projects p CROSS JOIN (VALUES ('items'), ('itemizedBundles')) AS kinds(k)
  CROSS JOIN LATERAL jsonb_array_elements(p.payload->k) WITH ORDINALITY AS entries(i, pos)
), archived AS (
  INSERT INTO archived_apartment_quotes (project_id, item_id, item_collection, quote)
    SELECT id, i->>'id', k, q FROM items CROSS JOIN LATERAL jsonb_array_elements(i->'quotes') q
    WHERE NOT apartment_valid_quote(q)
  RETURNING project_id
), rebuilt AS (
  SELECT id, k, jsonb_agg(jsonb_set(i, '{quotes}', COALESCE((
    SELECT jsonb_agg(q ORDER BY n) FROM jsonb_array_elements(i->'quotes') WITH ORDINALITY AS quotes(q,n)
    WHERE apartment_valid_quote(q)
  ), '[]'::jsonb)) ORDER BY pos) AS entries
  FROM items WHERE id IN (SELECT project_id FROM archived) GROUP BY id,k
)
UPDATE projects p SET payload = p.payload || changes.fields, updated_at = now()
FROM (SELECT id, jsonb_object_agg(k,entries) AS fields FROM rebuilt GROUP BY id) changes WHERE changes.id=p.id;

CREATE FUNCTION apartment_item_prices_valid(payload jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item jsonb; attempts jsonb; attempt jsonb; q jsonb;
BEGIN
  IF jsonb_typeof(payload->'items') IS DISTINCT FROM 'array'
    OR jsonb_typeof(COALESCE(payload->'itemizedBundles','[]')) <> 'array' THEN RETURN false; END IF;
  FOR item IN SELECT value FROM jsonb_array_elements((payload->'items') || COALESCE(payload->'itemizedBundles','[]')) LOOP
    IF jsonb_typeof(item->'quotes') IS DISTINCT FROM 'array' THEN RETURN false; END IF;
    FOR q IN SELECT value FROM jsonb_array_elements(item->'quotes') LOOP
      IF NOT apartment_valid_quote(q) THEN RETURN false; END IF;
    END LOOP;
    attempts := COALESCE(item->'lastSearch','{}');
    IF jsonb_typeof(attempts) <> 'object' THEN RETURN false; END IF;
    FOR attempt IN SELECT value FROM jsonb_each(attempts) LOOP
      IF jsonb_typeof(attempt->'offers') IS DISTINCT FROM 'array' THEN RETURN false; END IF;
      FOR q IN SELECT value FROM jsonb_array_elements(attempt->'offers') LOOP
        IF NOT apartment_valid_quote(q) THEN RETURN false; END IF;
      END LOOP;
    END LOOP;
  END LOOP;
  RETURN true;
END; $$;

ALTER TABLE projects ADD CONSTRAINT apartment_prices_require_product_url CHECK (apartment_item_prices_valid(payload));
