# Natarundi — deploy en Dokploy (GHCR + GitHub Actions)

Flujo: push → tests → imagen **GHCR** → **POST** al webhook de deploy del compose (misma URL que en el panel; con `ref` de la rama).

## 1. Crear el stack en Dokploy

1. **Compose** conectado al repo `josalero/learning-with-me`, rama `feat/apartamento-dokploy-ci` (hasta merge) o `main`.
2. **Compose path:** `apartamento/docker-compose.dokploy.yml`
3. **Root directory / working directory:** `apartamento` (donde vive el compose).
4. Copia variables de [`env.dokploy.example`](../env.dokploy.example) al panel **Environment** (secretos reales, nunca en Git).
5. Obligatorio en Environment: **`APP_IMAGE=ghcr.io/josalero/learning-with-me/apartamento:latest`** (o `…:sha-<commit>` para fijar versión).
6. Dominio HTTPS → servicio **`app`**, puerto contenedor **`8080`**. Tras crear o editar el dominio, pulsa **Deploy** otra vez (Traefik actualiza labels).
7. El compose enlaza **`app`** a la red externa **`dokploy-network`** (requerido para que Traefik vea el contenedor). **`db`** solo usa red interna.

## 2. GHCR

- Imagen: `ghcr.io/josalero/learning-with-me/apartamento`
- Tags en **`main`/`master`:** `latest` y `sha-<full-git-sha>`.
- Tags en **`feat/apartamento-dokploy-ci`:** `sha-<sha>` y `feat-apartamento-dokploy-ci` (no pisa `:latest`).
- Si el paquete es **privado**, en Dokploy añade credenciales de registry (`ghcr.io`, usuario GitHub, PAT con `read:packages`) o haz el paquete público en GitHub → Packages.

Repo → **Settings → Actions → General → Workflow permissions:** **Read and write** (para que `GITHUB_TOKEN` pueda publicar en GHCR).

## 3. GitHub — secrets y variables

| Nombre | Tipo | Uso |
| --- | --- | --- |
| **`DOKPLOY_COMPOSE_DEPLOY_WEBHOOK_URL`** | **Secret** | URL en Dokploy (Compose → deployments). El workflow hace **`POST`** con cuerpo estilo GitHub (`ref: refs/heads/<rama>`). |

**Branch Not Match:** la rama en Dokploy (compose → Provider → Branch) debe ser **exactamente** la del push, p. ej. `feat/apartamento-dokploy-ci` (no `main` si el workflow corre en la feature branch). Activa **Auto Deploy** en el compose.

La URL suele verse en Dokploy → tu compose → pestaña de despliegues (copiar y pegar en el secret).

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

1. **Dominio en el compose correcto:** Proyecto → tu stack Compose → **Domains** → `cotiza.my-tech-profile.dev` (o el tuyo) asociado al servicio **`app`**, puerto contenedor **8080**, HTTPS activo. Sin esto, Traefik no enruta al contenedor. **Redeploy** después de tocar dominios.
2. **Red `dokploy-network`:** El servicio `app` debe estar en esa red (ya va en `docker-compose.dokploy.yml`). Si el 404 sigue, en el servidor: `docker network connect dokploy-network <nombre-contenedor-app>` y redeploy limpio.
3. **Contenedor `app` en ejecución:** Logs del servicio `app`. Si falla migrate/seed por `DATABASE_URL`, `SESSION_SECRET` o credenciales PG, el contenedor reinicia y no hay backend.
4. **Registry GHCR:** Si el pull de `APP_IMAGE` falla (paquete privado sin login), el servicio `app` no arranca.
5. **Rama y ruta Git en Dokploy:** Rama `feat/apartamento-dokploy-ci`, compose `apartamento/docker-compose.dokploy.yml`, directorio raíz `apartamento`.
6. **Comprobación externa** (cuando el routing esté bien):

```bash
curl -fsS https://cotiza.my-tech-profile.dev/api/health
```

Debe devolver JSON con `ok: true`, no `404 page not found`.