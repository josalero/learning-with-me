import {
  pgTable,
  text,
  timestamp,
  uuid,
  jsonb,
  boolean,
  real,
  integer,
  customType,
  index,
  uniqueIndex,
} from 'drizzle-orm/pg-core';
import type { ProjectReference, ReferenceAnalysis } from '../../shared/types.js';

export const workspaces = pgTable('workspaces', {
  id: uuid('id').primaryKey().defaultRandom(),
  name: text('name').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const users = pgTable(
  'users',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    workspaceId: uuid('workspace_id')
      .notNull()
      .references(() => workspaces.id, { onDelete: 'cascade' }),
    email: text('email').notNull(),
    passwordHash: text('password_hash').notNull(),
    isDemo: boolean('is_demo').default(false).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  },
  (t) => [uniqueIndex('users_email_uidx').on(t.email)],
);

export const sessions = pgTable('sessions', {
  id: text('id').primaryKey(),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const projects = pgTable('projects', {
  id: uuid('id').primaryKey().defaultRandom(),
  workspaceId: uuid('workspace_id')
    .notNull()
    .references(() => workspaces.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  address: text('address').notNull(),
  region: text('region').notNull(),
  payload: jsonb('payload').notNull(),
  usdCrcRate: real('usd_crc_rate').default(520).notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull(),
});

const bytea = customType<{ data: Buffer; driverData: Buffer }>({ dataType: () => 'bytea' });

export const projectReferences = pgTable('project_references', {
  id: uuid('id').primaryKey().defaultRandom(),
  projectId: uuid('project_id').notNull().references(() => projects.id, { onDelete: 'cascade' }),
  title: text('title').notNull(),
  mediaType: text('media_type').$type<ProjectReference['mediaType']>().notNull(),
  content: bytea('content'),
  assetName: text('asset_name'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (table) => [index('project_references_project_created_idx').on(table.projectId, table.createdAt)]);

export const projectReferenceAnalyses = pgTable('project_reference_analyses', {
  projectId: uuid('project_id').primaryKey().references(() => projects.id, { onDelete: 'cascade' }),
  result: jsonb('result').$type<ReferenceAnalysis>().notNull(),
});

export const regionalStores = pgTable('regional_stores', {
  id: text('id').primaryKey(),
  workspaceId: uuid('workspace_id')
    .notNull()
    .references(() => workspaces.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  region: text('region').notNull(),
  websiteUrl: text('website_url').notNull(),
  probeKind: text('probe_kind').notNull(),
  lastProbe: jsonb('last_probe'),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull(),
});

export const workflowRuns = pgTable('workflow_runs', {
  id: uuid('id').primaryKey().defaultRandom(),
  projectId: uuid('project_id')
    .notNull()
    .references(() => projects.id, { onDelete: 'cascade' }),
  threadId: text('thread_id').notNull(),
  stage: text('stage').notNull(),
  pendingGate: text('pending_gate'),
  state: jsonb('state').notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull(),
});

export const priceCache = pgTable('price_cache', {
  id: uuid('id').primaryKey().defaultRandom(),
  workspaceId: uuid('workspace_id').notNull(),
  source: text('source').notNull(),
  url: text('url').notNull(),
  title: text('title').notNull(),
  priceCrc: integer('price_crc'),
  payload: jsonb('payload'),
  fetchedAt: timestamp('fetched_at', { withTimezone: true }).defaultNow().notNull(),
});
