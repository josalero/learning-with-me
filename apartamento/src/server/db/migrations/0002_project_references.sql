CREATE TABLE project_references (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  media_type TEXT NOT NULL,
  content BYTEA,
  asset_name TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK ((content IS NULL) <> (asset_name IS NULL))
);
CREATE INDEX project_references_project_created_idx ON project_references(project_id, created_at);

CREATE TABLE project_reference_analyses (
  project_id UUID PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  result JSONB NOT NULL
);

INSERT INTO project_references (project_id, title, media_type, asset_name, created_at)
SELECT p.id, ref.title, ref.media_type, ref.asset_name, NOW() + ref.position * INTERVAL '1 millisecond'
FROM projects AS p
CROSS JOIN (VALUES
  ('Render de sala y cocina', 'image/jpeg', 'plano-medidas.jpg', 0),
  ('Plano con medidas', 'image/jpeg', 'render-sala-cocina.jpg', 1),
  ('Vista 3D cenital', 'image/png', 'mapa-3d-cenital.png', 2)
) AS ref(title, media_type, asset_name, position)
WHERE p.name = 'Condominio Natarundi — Portegolpe'
  AND p.address = 'Condominio Natarundi, Portegolpe, Guanacaste';
