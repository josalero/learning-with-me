# Samples

This folder contains demo infrastructure used by the gateway and integrations.
These applications and catalogs are test fixtures, not production templates.
Run commands from the repository root.

| Sample | Purpose |
|---|---|
| [Recruiting API](backends/recruiting-api/README.md) | OpenAPI downstream with sensitive fields and failure controls |
| [Protected API](backends/protected-api/README.md) | OAuth2 client-credentials downstream |
| [External catalog](catalogs/external/README.md) | Mounted YAML that adds a tool without rebuilding the gateway |

Start the complete default stack:

```bash
docker compose up --build -d --wait
```

Applications that consume the gateway are under
[`integrations`](../integrations/README.md). Connector libraries and their
sample are under [`connectors`](../connectors/README.md).
