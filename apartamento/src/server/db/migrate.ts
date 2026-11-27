import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { applyNatarundiLaundryPrices, applyNatarundiOpeningPrices } from '../../domain/natarundi-opening.js';
import type { FurnishingProject } from '../../shared/types.js';
import { getPool } from './client.js';

const migrations = fileURLToPath(new URL('./migrations/', import.meta.url));

async function main(): Promise<void> {
  const pool = getPool();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('SELECT pg_advisory_xact_lock(30912, 1)');
    await client.query('CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY)');
    const applied = await client.query<{ name: string }>('SELECT name FROM schema_migrations');
    const known = new Set(applied.rows.map(({ name }) => name));
    const files = readdirSync(migrations).filter((file) => /^\d{4}_.*\.sql$/.test(file)).sort();
    for (const file of files) {
      if (known.has(file)) continue;
      await client.query(readFileSync(path.join(migrations, file), 'utf8'));
      const attachPrices = file === '0006_natarundi_inventory.sql' ? applyNatarundiOpeningPrices
        : file === '0008_natarundi_laundry.sql' ? applyNatarundiLaundryPrices : undefined;
      if (attachPrices) {
        const rows = await client.query<{ id: string; payload: FurnishingProject }>(
          'SELECT id, payload FROM projects WHERE name = $1 AND address = $2 FOR UPDATE',
          ['Condominio Natarundi — Portegolpe', 'Condominio Natarundi, Portegolpe, Guanacaste']);
        for (const { id, payload } of rows.rows) {
          await client.query('UPDATE projects SET payload = $1, updated_at = now() WHERE id = $2',
            [attachPrices(payload), id]);
        }
      }
      await client.query('INSERT INTO schema_migrations (name) VALUES ($1)', [file]);
      console.log(`Migration ${file} applied`);
    }
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
