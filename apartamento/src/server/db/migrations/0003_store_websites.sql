UPDATE regional_stores AS store
SET website_url = replacement.website_url, last_probe = NULL, updated_at = NOW()
FROM (VALUES
  ('siman-cr', 'https://www.siman.com/', 'https://cr.siman.com/'),
  ('aliss', 'https://www.aliss.cr/', 'https://aliss.cr/'),
  ('epa', 'https://epa.cr/', 'https://cr.epaenlinea.com/es-cr'),
  ('fantini-liberia', 'https://fantini.cr/', 'https://fantini.cr/es-cr'),
  ('mister-precios', 'https://www.misterprecios.cr/', 'https://misterprecios.com/'),
  ('ahorra-ya', 'https://www.ahorraya.cr/', 'https://www.ahorraya.app/ahorraya-costa-rica')
) AS replacement(id, old_website_url, website_url)
WHERE store.id = replacement.id AND store.website_url = replacement.old_website_url;
