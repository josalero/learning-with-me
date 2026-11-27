INSERT INTO regional_stores (id, workspace_id, name, region, website_url, probe_kind)
SELECT store.id || '-' || workspace.id, workspace.id, store.name, 'san-jose', store.website_url, store.probe_kind
FROM workspaces AS workspace
CROSS JOIN (VALUES
  ('cemaco-cr', 'Cemaco Costa Rica', 'https://www.cemaco.co.cr/', 'json_ld'),
  ('crate-barrel-cr', 'Crate & Barrel Costa Rica', 'https://www.crateandbarrel.co.cr/', 'shopify')
) AS store(id, name, website_url, probe_kind)
WHERE NOT EXISTS (
  SELECT 1
  FROM regional_stores AS registered
  WHERE registered.workspace_id = workspace.id
    AND regexp_replace(lower(split_part(registered.website_url, '/', 3)), '^www\.', '')
      = regexp_replace(split_part(store.website_url, '/', 3), '^www\.', '')
)
ON CONFLICT DO NOTHING;
