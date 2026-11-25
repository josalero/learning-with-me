export const REGIONS = ['guanacaste', 'limon', 'san-jose', 'alajuela'] as const;
export type RegionId = (typeof REGIONS)[number];

export const BUDGET_TIERS = ['economy', 'balanced', 'premium'] as const;
export type BudgetTier = (typeof BUDGET_TIERS)[number];

export const QUOTE_SOURCES = [
  'mister_precios',
  'ahorra_ya',
  'store_direct',
  'manual',
  'agent_estimate',
] as const;
export type QuoteSource = (typeof QUOTE_SOURCES)[number];

export type MoneyCrc = { amountCrc: number; source: QuoteSource; capturedAt: string };

export type StoreRef = {
  id: string;
  name: string;
  region: RegionId;
  websiteUrl: string;
  probeKind: string;
};

export type LineItemQuote = {
  id: string;
  storeName: string;
  storeUrl: string;
  productUrl: string;
  title: string;
  priceCrc: number;
  priceUsd?: number;
  availability: 'in_stock' | 'out_of_stock' | 'unknown';
  source: QuoteSource;
  basis?: 'published' | 'catalog' | 'estimate';
  capturedAt: string;
};

export type CatalogItem = {
  id: string;
  zoneId: string;
  name: string;
  category: string;
  qty: number;
  notes?: string;
  dimsM?: { w?: number; h?: number; d?: number };
  quotes: LineItemQuote[];
  unquotedReason?: string;
};

export type ProjectZone = {
  id: string;
  slug: string;
  labelEs: string;
  areaM2?: number;
  notes?: string;
};

export type FurnishingProject = {
  id: string;
  workspaceId: string;
  name: string;
  address: string;
  region: RegionId;
  zones: ProjectZone[];
  items: CatalogItem[];
  usdCrcRate: number;
};

export type ProjectSummary = Pick<FurnishingProject, 'id' | 'name' | 'address' | 'region' | 'usdCrcRate'> & {
  coverReferenceId?: string;
  updatedAt: string;
  itemCount: number;
  quotedCount: number;
  totalCrc: number;
};

export const REFERENCE_MAX_BYTES = 5 * 1024 * 1024;
export const REFERENCE_MAX_ANALYSIS_IMAGES = 4;
export const REFERENCE_MAX_ANALYSIS_BYTES = 12 * 1024 * 1024;

export type ProjectReference = {
  id: string;
  title: string;
  mediaType: 'image/jpeg' | 'image/png' | 'image/webp';
  createdAt: string;
};

export type ReferenceSuggestion = {
  id: string;
  name: string;
  zoneId: string | null;
  qty: number;
  reason: string;
  referenceId: string;
};

export type ReferenceAnalysis = {
  createdAt: string;
  suggestions: ReferenceSuggestion[];
};

export type AgentStage =
  | 'idle'
  | 'visual_space'
  | 'store_scout'
  | 'market_scrape'
  | 'quality_critic'
  | 'budget_quoter'
  | 'completed';

export type HitlGate = 'zone_layout' | 'final_budget';

export type ReviewStep = 'orchestrator' | 'visual_space' | 'market_scrape' | 'quality_critic' | 'budget_quoter';

export type ReviewActivity = {
  step: ReviewStep;
  status: 'running' | 'completed' | 'waiting' | 'failed';
  detail: string;
};

export type ReviewResult = {
  threadId: string;
  stage: ReviewStep | 'idle' | 'completed';
  pendingGate?: HitlGate;
  notes: string[];
  activity: ReviewActivity[];
};

export type ReviewHistoryEvent =
  | ({ type: 'step'; at: string } & ReviewActivity)
  | { type: 'approval'; at: string; gate: HitlGate };

export type ReviewSnapshot = ReviewResult & {
  startedAt?: string;
  updatedAt: string;
  approvedGates: HitlGate[];
  events: ReviewHistoryEvent[];
  failed: boolean;
};

export type ReviewHistoryPage = { runs: ReviewSnapshot[]; nextCursor?: string };

export const QUOTE_CONCURRENCY = 3;

export type QuoteActivity = {
  itemId: string;
  itemName: string;
  stage: 'queued' | 'searching' | 'verifying' | 'catalog' | 'saving' | 'completed' | 'failed';
  detail: string;
};

export type ProgressEvent<Activity, Result> =
  | { type: 'activity'; activity: Activity }
  | { type: 'result'; result: Result }
  | { type: 'error'; status?: number; message?: string };

export type WorkflowState = {
  projectId: string;
  threadId: string;
  stage: AgentStage;
  pendingGate?: HitlGate;
  summary?: string;
  errors: string[];
};
