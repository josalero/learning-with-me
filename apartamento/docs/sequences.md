# Diagramas de secuencia

Convenciones: el cliente usa `fetch` vía `api()` / `post()` en `ui.tsx`. Errores 401 redirigen a login.

## Sesión y acceso a proyecto

```mermaid
sequenceDiagram
  participant U as Usuario
  participant UI as App.tsx
  participant API as Fastify /api
  participant DB as PostgreSQL

  U->>UI: Abre la app
  UI->>API: GET /api/auth/me (cookie)
  alt sin cookie válida
    API-->>UI: 401
    UI->>U: Formulario login
    U->>UI: email + password
    UI->>API: POST /api/auth/login
    API->>DB: users + sessions
    API-->>UI: Set-Cookie natarundi_sid
  else sesión OK
    API-->>UI: { email, isDemo }
  end
  UI->>API: GET /api/projects
  API->>DB: projects por workspace
  API-->>UI: lista ProjectSummary
```

## Cotizar un ítem (on demand)

```mermaid
sequenceDiagram
  participant U as Usuario
  participant Inv as Inventory.tsx
  participant API as api.ts
  participant Q as quoteOnDemand.ts
  participant S as Tiendas y fichas de producto
  participant DB as PostgreSQL

  U->>Inv: Cotizar (URL opcional)
  Inv->>API: POST .../items/:id/quote
  API->>DB: load project payload
  alt productUrl
    API->>Q: quoteItemOnDemand (url)
    Q->>S: fetchJsonLdOffers + SSRF check
    S-->>Q: ParsedOffer[]
  else sin URL
    Q->>S: Buscar en las tiendas registradas y verificar cada ficha
  end
  Q-->>API: Cotización elegida u ofertas con evidencia insuficiente
  API->>DB: save payload
  API-->>Inv: proyecto actualizado
```

Cotización masiva: `POST /api/projects/:id/quote-pending` recorre ítems sin precio con concurrencia `QUOTE_CONCURRENCY` y puede emitir eventos de progreso (SSE) vía `respondWithProgress`.

## Pipeline de agentes (HITL)

```mermaid
sequenceDiagram
  participant U as Usuario
  participant AR as AgentReview.tsx
  participant API as api.ts
  participant G as furnishingGraph
  participant DB as PostgreSQL

  U->>AR: Ejecutar agentes
  AR->>API: POST /api/workflow/start
  API->>G: invoke(initialState, thread_id)
  G-->>API: stage visual_space, gate zone_layout
  API->>DB: upsert workflow_runs
  API-->>AR: ReviewResult + pendingGate

  U->>AR: Aprobar distribución
  AR->>API: POST /api/workflow/approve { gate: zone_layout }
  API->>G: invoke con approvedGates
  Note over G: orchestrator → visual_space → market_scrape → quality_critic → budget_quoter
  G-->>API: gate final_budget
  API-->>AR: espera segunda aprobación

  U->>AR: Aprobar presupuesto
  AR->>API: POST /api/workflow/approve { gate: final_budget }
  API->>G: invoke
  G-->>API: stage completed
```

Grafo compilado en `furnishingGraph.ts`: nodos `orchestrator`, `visual_space`, `market_scrape`, `quality_critic`, `budget_quoter`. Los gates pausan con `gate` en estado hasta que `approvedGates` incluye el id.

## Tipo de cambio USD/CRC

```mermaid
sequenceDiagram
  participant S as index.ts startup
  participant FX as fxRate.ts
  participant Sync as fxProjectSync.ts
  participant DB as PostgreSQL
  participant Ext as bccr-proxy / etc.

  S->>Sync: syncAllProjectsFxRate()
  Sync->>FX: fetchUsdCrcRate()
  FX->>Ext: HTTP JSON
  Ext-->>FX: compra/venta
  Sync->>DB: UPDATE projects.usd_crc_rate
```

`GET /api/fx` devuelve caché; `POST /api/fx/refresh` fuerza recarga (`FX_PROVIDER`, `FX_CACHE_TTL_MINUTES`).

## Exportación Excel

```mermaid
sequenceDiagram
  participant U as Usuario
  participant D as Dashboard / UI
  participant API as api.ts
  participant X as excelExport.ts

  U->>D: Descargar Excel
  D->>API: GET /api/projects/:id/export.xlsx
  API->>X: buildBudgetWorkbook(project)
  X-->>API: Buffer
  API-->>D: application/vnd.openxmlformats...
```

## CI/CD (referencia)

```mermaid
sequenceDiagram
  participant GH as GitHub Actions
  participant GHCR as ghcr.io
  participant DK as Dokploy

  GH->>GH: npm test + build
  GH->>GHCR: docker push apartamento:tag
  GH->>DK: POST deploy webhook (ref = rama)
  DK->>GHCR: pull APP_IMAGE
  DK->>DK: compose up (app + db)
```

Detalle en [deployment-dokploy.md](./deployment-dokploy.md).