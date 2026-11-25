# Componentes y módulos

No hay clases OOP en el sentido clásico: el servidor es **funciones + Fastify**; el cliente **componentes React**. Los tipos de dominio son **TypeScript types** en `src/shared/types.ts`.

## Cliente (`src/client/`)

| Componente | Archivo | Rol |
| --- | --- | --- |
| **App** | `App.tsx` | Raíz: sesión, login, enrutamiento a proyectos |
| **Workspace** | `Workspace.tsx` | Layout con sidebar (Resumen, Agentes, Inventario, Referencias) |
| **ProjectsPage** | `ProjectsPage.tsx` | Listado y creación de proyectos |
| **Dashboard** | `Dashboard.tsx` | KPIs, totales CRC/USD, accesos rápidos |
| **Inventory** | `Inventory.tsx` | Tabla por zonas, cotizar, tiers economy/balanced/premium |
| **QuoteProgress** | `QuoteProgress.tsx` | UI de jobs de cotización (`QuoteJob`, `QuoteProgress`) |
| **AgentReview** | `AgentReview.tsx` | Ejecutar grafo, aprobar gates, historial |
| **ReviewHistory** | `ReviewHistory.tsx` | Snapshots en `workflow_runs` |
| **References** | `References.tsx` | Subida de imágenes de referencia, análisis |
| **ui** | `ui.tsx` | `api`, `post`, `Brand`, formularios compartidos |
| **navigation** | `navigation.tsx` | Rutas sin React Router (`readRoute`, `navigate`) |
| **streamApi** | `streamApi.ts` | Consumo SSE de progreso |

### Rutas de UI (path-based)

| Path | Vista |
| --- | --- |
| `/` | Proyectos (si hay sesión) o login |
| `/proyectos` | Lista de proyectos |
| `/proyectos/:id` | Dashboard (resumen) |
| `/proyectos/:id/agentes` | AgentReview |
| `/proyectos/:id/inventario` | Inventory |
| `/proyectos/:id/referencias` | References |

## Servidor — rutas

| Módulo | Prefijo / rutas principales |
| --- | --- |
| `registerApiRoutes` | `/api/auth/*`, `/api/projects/*`, `/api/workflow/*`, `/api/fx/*`, `/api/stores/*` |
| `registerReferenceRoutes` | `/api/projects/:id/references/*`, estáticos `/public/assets/*` |

Autenticación: `requireUser` en handlers protegidos; cookie `natarundi_sid` → tabla `sessions`.

## Servidor — servicios

| Servicio | Archivo | Responsabilidad |
| --- | --- | --- |
| **auth** | `services/auth.ts` | scrypt passwords, crear/validar sesión, cookies |
| **projects** | `services/projects.ts` | `projectFromRow`, `loadOwnedProject`, `saveProjectPayload` |
| **quoteOnDemand** | `services/quoteOnDemand.ts` | Cotizar ítem/proyecto: URL, catálogo, estimado |
| **fxRate** | `services/fxRate.ts` | Proveedores BCCR proxy, exchangerate-api, static |
| **fxProjectSync** | `services/fxProjectSync.ts` | Propagar tasa a filas `projects` |
| **excelExport** | `services/excelExport.ts` | Workbook ExcelJS por zonas y tiers |
| **progress** | `services/progress.ts` | SSE, `ProgressError`, errores 404 en stream |
| **referenceVision** | `services/referenceVision.ts` | Sugerencias desde imágenes (LLM cuando online) |
| **reviewRanking** | `services/reviewRanking.ts` | Ordenar productos revisados (agente) |

## Agentes y LLM

| Módulo | Archivo | Rol |
| --- | --- | --- |
| **furnishingGraph** | `agents/furnishingGraph.ts` | `StateGraph` LangGraph, 5 nodos, 2 HITL gates |
| **modelRouter** | `agents/modelRouter.ts` | `AI_ROUTING_MODE` → modelos OpenRouter ECONOMY/BALANCED/FRONTIER |
| **pricingAgent** | `agents/pricingAgent.ts` | Búsqueda de páginas de producto/reseñas (LLM + herramientas) |

`isAiOffline()` cuando `AI_OFFLINE_STUB=1` o falta `OPENROUTER_API_KEY`: el grafo usa heurísticas sin llamadas al modelo.

## Scrapers e integraciones HTTP

| Módulo | Archivo | Rol |
| --- | --- | --- |
| **ssrf-guard** | `scrapers/ssrf-guard.ts` | `assertPublicHttpUrl`, `SsrfBlockedError` |
| **fetchPage** | `scrapers/fetchPage.ts` | GET con timeout, user-agent, parse HTML |
| **jsonld-offer** | `scrapers/jsonld-offer.ts` | `parseJsonLdOffers`, precio CRC desde schema.org |
| **store-probe** | `scrapers/store-probe.ts` | Probe de homepage de tienda |
| **stores-seed** | `scrapers/stores-seed.ts` | Catálogo fijo de tiendas CR |

## Dominio (datos maestros)

| Módulo | Archivo | Rol |
| --- | --- | --- |
| **natarundi-catalog** | `domain/natarundi-catalog.ts` | 9 zonas, ~20 ítems, medidas del plano |
| **demo-quotes** | `domain/demo-quotes.ts` | Precios demo/estimados para seed y cotizar sin URL |
| **natarundi-references** | `domain/natarundi-references.ts` | Metadatos de assets públicos |

## Shared (`src/shared/`)

Tipos centrales: `FurnishingProject`, `CatalogItem`, `LineItemQuote`, `ReviewResult`, `HitlGate`, `BudgetTier`.

Utilidades: `projectStats.ts`, `quoteBasis.ts`, `search.ts`, `budgetProfiles.ts` (tiers y jobs de cotización).

## Infraestructura de build

| Artefacto | Salida |
| --- | --- |
| Vite | `dist/client/` (SPA) |
| `tsc` server | `dist/server/server/` |
| Docker | Imagen runtime Node 22, migraciones copiadas bajo `dist/.../db/migrations` |

## Mapa mental agente ↔ producto

Los nombres de diseño en AGENTS.md mapean a nodos del grafo:

| Diseño (AGENTS.md) | Nodo LangGraph |
| --- | --- |
| FurnishingOrchestrator | `orchestrator` |
| VisualSpaceAgent | `visual_space` |
| RegionalStoreScout / MarketScraperAgent | `market_scrape` |
| AirbnbQualityCriticAgent | `quality_critic` |
| BudgetQuoterAgent | `budget_quoter` |