# Arquitectura

## Propósito

Aplicación full-stack que mantiene un **inventario maestro** por zonas del apartamento, cotiza líneas en CRC/USD, ejecuta un **pipeline multi-agente** con dos puntos human-in-the-loop (HITL) y exporta Excel. No integra checkout ni pagos.

## Vista de contenedores

```mermaid
flowchart TB
  subgraph client["Cliente"]
    browser["Navegador<br/>React 19 + Vite"]
  end

  subgraph runtime["Contenedor app Node 22"]
    fastify["Fastify 5<br/>API + estáticos"]
    graph["LangGraph<br/>furnishingGraph"]
    agents["pricingAgent<br/>modelRouter"]
    scrape["fetchPage / JSON-LD<br/>SSRF guard"]
  end

  subgraph data["Datos"]
    pg[("PostgreSQL 18")]
  end

  subgraph external["Externos"]
    bccr["Tipo cambio<br/>bccr-proxy / …"]
    openrouter["OpenRouter<br/>LLM opcional"]
    shops["Tiendas CR<br/>HTTP público"]
  end

  browser -->|HTTPS| fastify
  fastify --> pg
  fastify --> graph
  fastify --> scrape
  scrape --> shops
  agents --> openrouter
  fastify --> bccr
```

En **Dokploy**, Traefik termina TLS y enruta al servicio `app:8080`; PostgreSQL vive en el servicio `db` de la misma red interna. Ver [deployment-dokploy.md](./deployment-dokploy.md).

## Capas lógicas

| Capa | Ruta | Responsabilidad |
| --- | --- | --- |
| **Presentación** | `src/client/` | SPA: proyectos, inventario, agentes, referencias, export |
| **API** | `src/server/routes/` | HTTP JSON, cookies de sesión, streaming de progreso (SSE) |
| **Dominio** | `src/domain/` | Catálogo Natarundi, demo quotes, referencias de plano |
| **Aplicación** | `src/server/services/` | Proyectos, cotización, FX, Excel, auth, visión/referencias |
| **Agentes** | `src/server/agents/` | Grafo LangGraph, routing OpenRouter, búsqueda de productos |
| **Integración** | `src/server/scrapers/` | Fetch HTML, JSON-LD Offer, probe tiendas, SSRF |
| **Persistencia** | `src/server/db/` | Drizzle ORM, migraciones SQL, seed demo |
| **Contratos** | `src/shared/` | Tipos TypeScript compartidos cliente/servidor |

## Modelo de despliegue

| Entorno | Compose | Imagen |
| --- | --- | --- |
| Local | `docker-compose.local.yml` | `build` + volumen PG en host |
| Producción | `docker-compose.dokploy.yml` | `APP_IMAGE` desde GHCR |

Arranque del contenedor: `docker-entrypoint.sh` → `migrate.js` → (`seed` si `APP_SEED_DEMO=1`) → `node dist/server/server/index.js`.

## Seguridad y aislamiento

- **Multi-tenant ligero:** cada `user` pertenece a un `workspace`; consultas de proyecto filtran por `workspace_id`.
- **Sesión:** cookie firmada (`SESSION_SECRET`), fila en `sessions`, TTL 14 días (`auth.ts`).
- **SSRF:** `assertPublicHttpUrl` antes de fetch a URLs de producto; comparación de host con tiendas registradas cuando aplica.
- **Sin PII en logs:** no registrar credenciales ni datos de huéspedes en prompts o telemetría.

## Estado del grafo vs. base de datos

- **Hoy:** hilos de workflow activos se retoman en memoria del proceso (`threadMemory` en `api.ts`) más snapshot en `workflow_runs`.
- **Preparado:** tablas `langgraph_checkpoints` / `langgraph_writes` para `PostgresSaver` (reanudación tras reinicio).

## Decisiones relevantes

1. **Proyecto documento en JSONB:** zonas e ítems viven en `projects.payload` para evitar normalizar ~20 líneas × N cotizaciones en tablas relaciones; el ORM usa `FurnishingProject` como forma canónica.
2. **Dos flujos de precio:** cotización bajo demanda (UI/API) independiente del grafo de revisión HITL.
3. **FX:** tasa `usd_crc_rate` por proyecto, sincronizada al arranque y vía `POST /api/fx/refresh`.