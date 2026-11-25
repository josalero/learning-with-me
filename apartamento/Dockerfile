FROM node:22-bookworm-slim AS deps
WORKDIR /app
ENV NPM_CONFIG_ALWAYS_AUTH=false
COPY package.json ./
RUN npm config delete always-auth 2>/dev/null || true; \
    npm install --registry=https://registry.npmjs.org/ --no-audit --no-fund --package-lock=false

FROM deps AS build
COPY . .
RUN npm run build

FROM node:22-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=deps /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/public ./public
COPY package.json ./
COPY src/server/db/migrations ./dist/server/server/db/migrations
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]