# External catalog example

[`gateway-catalog.yml`](gateway-catalog.yml) adds
`search_candidates_external` by referencing the bundled `recruiting-api`
connection. [`docker-compose.catalog.yml`](../../../docker-compose.catalog.yml)
mounts the file and sets Spring’s additional configuration location.

## Run

Run from the repository root. Start the default stack, then recreate only the
gateway with the catalog override:

```bash
docker compose up --build -d --wait
docker compose -f docker-compose.yml -f docker-compose.catalog.yml \
  up -d --wait --force-recreate --no-deps gateway
```

Confirm that the external tool was added:

```bash
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog \
  | grep search_candidates_external
```

Restore the bundled-only catalog:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

The gateway image is not rebuilt when applying or removing the override.
External catalogs are loaded at startup, so configuration changes require a
gateway restart.

See the [catalog reference](../../../docs/reference/catalog.md) for field rules.
