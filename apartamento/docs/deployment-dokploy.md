# Natarundi — deploy en Dokploy (GHCR + GitHub Actions)

Flujo: **push a `main`** en `learning-with-me` (cambios bajo `apartamento/**`) → CI (`npm test` + build) → imagen a **GHCR** → `POST /api/compose.deploy` en Dokploy (si está configurado).

## 1. Crear el stack en Dokploy

1. **Compose** conectado al repo `josalero/learning-with-me`, rama `main`.
2. **Compose path:** `apartamento/docker-compose.dokploy.yml`
3. **Root directory / working directory:** `apartamento` (donde vive el compose).
4. Copia variables de [`env.dokploy.example`](../env.dokploy.example) al panel **Environment** (secretos reales, nunca en Git).
5. Obligatorio en Environment: **`APP_IMAGE=ghcr.io/josalero/learning-with-me/apartamento:latest`** (o `…:sha-<commit>` para fijar versión).
6. Dominio HTTPS → servicio **`app`**, puerto contenedor **`8080`**.

## 2. GHCR

- Imagen: `ghcr.io/josalero/learning-with-me/apartamento`
- Tags: `latest` y `sha-<full-git-sha>` en cada deploy desde `main`.
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