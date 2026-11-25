#!/bin/sh
set -e
echo "Running database migration..."
node dist/server/server/db/migrate.js
if [ "${APP_SEED_DEMO}" != "0" ]; then
  echo "Running demo seed..."
  node dist/server/server/db/seed.js
fi
exec node dist/server/server/index.js