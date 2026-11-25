# Agent instructions — Natarundi presupuesto (apartamento)

Presupuesto Airbnb para **Condominio Natarundi, Portegolpe**. Solo estimación: sin compras, pagos ni carrito.

## Lectura rápida

| Necesitas | Archivo |
| --- | --- |
| Levantar con Docker | `docker-compose.local.yml`, `env`, `CLAUDE.md` |
| Grafo LangGraph | `src/server/agents/furnishingGraph.ts` |
| Modelos (OpenRouter) | `src/server/agents/modelRouter.ts`, `env` → `AI_*`, `OPENROUTER_*` |
| Cotización bajo demanda | `src/server/services/quoteOnDemand.ts`, API en `src/server/routes/api.ts` |
| Catálogo + demo quotes | `src/domain/natarundi-catalog.ts`, `src/domain/demo-quotes.ts` |
| Scrapers | `src/server/scrapers/` (JSON-LD, SSRF, probe tiendas) |

## Comandos

```bash
docker compose -f docker-compose.local.yml up --build -d
# UI: http://localhost:8095 (puerto desde LOCAL_APP_PORT en env)
npm test
npm run build
```

## Dos flujos (no confundir)

### 1. Cotizar on demand (UI)

- **Cotizar** / **Actualizar** por ítem: `POST /api/projects/:projectId/items/:itemId/quote` (URL opcional con JSON-LD).
- **Cotizar pendientes**: `POST /api/projects/:projectId/quote-pending`.
- Sin URL: catálogo regional de referencia; con estimado para ítems bloqueados (secadora, escritorio D.3, flete).
- Con URL pública: `fetchJsonLdOffers` + guardrail SSRF.

### 2. Pipeline multi-agente (LangGraph + HITL)

Botón **Ejecutar agentes** → `POST /api/workflow/start`. Aprobaciones → `POST /api/workflow/approve`.

| Nodo (código) | Rol (diseño) | Qué hace hoy |
| --- | --- | --- |
| `orchestrator` | FurnishingOrchestrator | Arranque; nota si `AI_OFFLINE_STUB=1` |
| `visual_space` | VisualSpaceAgent | **Gate 1** `zone_layout` — layout 9 zonas |
| `market_scrape` | RegionalStoreScout + MarketScraperAgent | Nota de mercado; scrape masivo aún no enlazado |
| `quality_critic` | AirbnbQualityCriticAgent | Cuenta ítems sin cotización (menaje, climatización) |
| `budget_quoter` | BudgetQuoterAgent | **Gate 2** `final_budget` — antes de Excel |

Flujo: `START → orchestrator → visual_space → [Gate1] → market_scrape → quality_critic → budget_quoter → [Gate2] → END`.

Los gates usan `interrupt()` de LangGraph. Hasta aprobar, el grafo pausa; el estado del hilo vive en memoria del servidor (`threadMemory` en `api.ts`), no en Postgres checkpoints aún (tablas `langgraph_*` ya migradas para uso futuro).

## Model routing (OpenRouter)

`modelRouter.ts` define **ECONOMY / BALANCED / FRONTIER** vía:

- `AI_ROUTING_MODE`
- `OPENROUTER_MODEL_ECONOMY`, `OPENROUTER_MODEL_BALANCED`, `OPENROUTER_MODEL_FRONTIER`
- `OPENROUTER_API_KEY`

Si `AI_OFFLINE_STUB=1` o falta API key → `isAiOffline()` y el grafo no llama modelos (heurísticas / catálogo).

Convención env local (`env`): OpenRouter en `env`; compose solo fuerza `DATABASE_URL` interna (`db:5432`).

## Fuentes de precio

| Fuente | Código | Uso |
| --- | --- | --- |
| MisterPrecios / AhorraYa | JSON-LD en `jsonld-offer.ts`, `fetchPage.ts` | URL de producto on demand |
| Tiendas CR | `stores-seed.ts`, probe `store-probe.ts` | Registro regional |
| Catálogo demo | `demo-quotes.ts` | Seed + cotizar sin URL |
| Manual / estimado | `quoteOnDemand.ts` | Ítems con `unquotedReason` |

CLI: `npm run prices:sync -- <url>` (una página, stdout JSON).

## Alcance agente / humano

- **Humano:** aprobar Gate 1 (zonas) y Gate 2 (presupuesto total), pegar URL de producto, exportar Excel.
- **No hacer:** simular compras, guardar tarjetas, automatizar checkout.
- **PII:** no loguear credenciales de demo ni datos de huéspedes en prompts.

## Próximos enganches (plan, no bloquean UI)

- Vision sobre `public/assets/` (plano, renders) en `visual_space` con modelo `vision` de `resolveModelIds`.
- `market_scrape` enlazado a sitemap/sync masivo MisterPrecios y AhorraYa.
- `PostgresSaver` para reanudar hilos tras reinicio del contenedor.
- Critic con LLM cuando `AI_OFFLINE_STUB=0`.

## Tests relevantes

- `tests/furnishing-graph.test.ts` — grafo con ambos gates aprobados.
- `tests/quote-on-demand.test.ts` — cotización catálogo / estimado.
- `tests/jsonld-offer.test.ts`, `tests/ssrf-guard.test.ts` — parsers y seguridad.