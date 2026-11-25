# Natarundi — deploy en Dokploy (GHCR + GitHub Actions)

Flujo: **push a `main`** en `learning-with-me` (cambios bajo `apartamento/**`) → CI (`npm test` + build) → imagen a **GHCR** → `POST /api/compose.deploy` en Dokploy (si está configurado).

## 1. Crear el stack en Dokploy

1. **Compose** conectado al repo `josalero/learning-with-me`, rama `feat/apartamento-dokploy-ci` (hasta merge) o `main`.
2. **Compose path:** `apartamento/docker-compose.dokploy.yml`
3. **Root directory / working directory:** `apartamento` (donde vive el compose).
4. Copia variables de [`env.dokploy.example`](../env.dokploy.example) al panel **Environment** (secretos reales, nunca en Git).
5. Obligatorio en Environment: **`APP_IMAGE=ghcr.io/josalero/learning-with-me/apartamento:latest`** (o `…:sha-<commit>` para fijar versión).
6. Dominio HTTPS → servicio **`app`**, puerto contenedor **`8080`**.

## 2. GHCR

- Imagen: `ghcr.io/josalero/learning-with-me/apartamento`
- Tags en **`main`/`master`:** `latest` y `sha-<full-git-sha>`.
- Tags en **`feat/apartamento-dokploy-ci`:** `sha-<sha>` y `feat-apartamento-dokploy-ci` (no pisa `:latest`).
- Si el paquete es **privado**, en Dokploy añade credenciales de registry (`ghcr.io`, usuario GitHub, PAT con `read:packages`) o haz el paquete público en GitHub → Packages.

Repo → **Settings → Actions → General → Workflow permissions:** **Read and write** (para que `GITHUB_TOKEN` pueda publicar en GHCR).

## 3. GitHub — secrets y variables

| Nombre | Tipo | Uso |
| --- | --- | --- |
| `DOKPLOY_URL` | Secret | Origen del panel, p. ej. `https://dokploy.tudominio.com` (sin barra final) |
| `DOKPLOY_API_KEY` | Secret | Dokploy → Profile → API/CLI |
| `DOKPLOY_COMPOSE_ID` | Variable | ID del compose en Dokploy (no es secreto) |

Obtener `composeId` (sustituye URL y API key):

```bash
curl -sS -H "x-api-key: YOUR_API_KEY" "https://YOUR_DOKPLOY/api/project.all" | jq .
```

Si **`DOKPLOY_COMPOSE_ID`** no está definido, el workflow **solo publica la imagen** y muestra un aviso; puedes pulsar **Deploy** en Dokploy a mano.

## 4. Validación local del compose

Exporta las variables del ejemplo (mismas claves que en Dokploy) y valida interpolación:

```bash
cd apartamento
set -a && source env.dokploy.example && set +a
docker compose -f docker-compose.dokploy.yml config
```

Usa contraseñas de prueba solo en local; no las subas a Git.

Tras desplegar:

```bash
curl -fsS https://TU_DOMINIO/api/health
```

## 5. Rollback

1. En Dokploy Environment, cambia `APP_IMAGE` a `ghcr.io/josalero/learning-with-me/apartamento:sha-<commit-anterior>`.
2. Vuelve a desplegar el compose (UI o API).

## 6. Troubleshooting — `404 page not found` (texto plano)

Ese mensaje en **texto plano** (`content-type: text/plain`) lo devuelve **Traefik** del host Dokploy, no la app Natarundi. Fastify respondería JSON en `/api/*` o el HTML del dashboard en `/`.

Checklist:

1. **Dominio en el compose correcto:** Proyecto → tu stack Compose → **Domains** → `cotiza.my-tech-profile.dev` (o el tuyo) asociado al servicio **`app`**, puerto contenedor **8080**, HTTPS activo. Sin esto, Traefik no enruta al contenedor.
2. **Contenedor `app` en ejecución:** Logs del servicio `app`. Si falla migrate/seed por `DATABASE_URL`, `SESSION_SECRET` o credenciales PG, el contenedor reinicia y no hay backend.
3. **Registry GHCR:** Si el pull de `APP_IMAGE` falla (paquete privado sin login), el servicio `app` no arranca.
4. **Rama y ruta Git en Dokploy:** Rama `feat/apartamento-dokploy-ci`, compose `apartamento/docker-compose.dokploy.yml`, directorio raíz `apartamento`.
5. **Comprobación externa** (cuando el routing esté bien):

```bash
curl -fsS https://cotiza.my-tech-profile.dev/api/health
```

Debe devolver JSON con `ok: true`, no `404 page not found`.