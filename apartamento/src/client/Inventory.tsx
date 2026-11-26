import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { ArrowUpRight, ChevronDown, Fan, Lamp, LayoutGrid, Link2, PackageOpen, Plus, RefreshCw, Search, Sofa, Sparkles, Tv, WashingMachine } from 'lucide-react';
import { budgetProfiles, quotesForTier } from '@shared/budgetProfiles';
import { brandLabel, productClass, productProvenance } from '@shared/brands';
import { enclosureAdvice, isFlexibleEnclosure, purchaseName } from '@shared/showerEnclosure';
import { BrandGuide } from './BrandGuide';
import { projectAreas } from '@shared/projectAreas';
import { projectStats } from '@shared/projectStats';
import { isPurchaseSet, purchaseQuantity } from '@shared/purchaseUnit';
import { quoteBasisLabel, quoteLink } from '@shared/quoteBasis';
import { normalizeSearch } from '@shared/search';
import type { BudgetTier, CatalogItem, FurnishingProject, LineItemQuote, QuoteActivity, QuoteSearch } from '@shared/types';
import { Link, projectPath } from './navigation';
import { QuoteJob } from './QuoteProgress';
import SplitItemDialog from './SplitItemDialog';
import { streamPost } from './streamApi';
import { ApiError, Modal, money, Notice, post, Spinner } from './ui';

const categoryIcons: Record<string, typeof Sofa> = {
  mobiliario: Sofa,
  electrodomesticos: WashingMachine,
  electronica: Tv,
  climatizacion: Fan,
  exterior: Lamp,
};
const quoteDate = new Intl.DateTimeFormat('es-CR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Costa_Rica' });

function QuoteStoreLink({ quote, itemName }: { quote: LineItemQuote; itemName: string }) {
  const link = quoteLink(quote);
  if (!link) return <span>Pendiente de verificar el enlace en {quote.storeName}</span>;
  const label = `${link.label} en ${quote.storeName}`;
  return <div className="product-link"><a href={link.url} target="_blank" rel="noreferrer" aria-label={`${label} para ${itemName} (abre en otra pestaña)`}>
    {label} <ArrowUpRight size={14} aria-hidden="true" />
  </a><details className="product-address"><summary>Ver URL</summary><span>{link.url}</span></details></div>;
}

function ReviewLink({ quote }: { quote: LineItemQuote }) {
  const review = quote.review;
  if (!review) return quote.basis === 'published' ? <span>Sin valoraciones verificadas</span> : null;
  const score = review.rating.toLocaleString('es-CR', { maximumFractionDigits: 2 });
  const count = review.count.toLocaleString('es-CR');
  const label = `${score}/5 · ${count} ${review.count === 1 ? 'valoración' : 'valoraciones'}`;
  return <a href={review.sourceUrl} target="_blank" rel="noreferrer" aria-label={`${label} de ${quote.title} (abre la fuente en otra pestaña)`}>
    {label} <ArrowUpRight size={14} aria-hidden="true" />
  </a>;
}

function SearchFindings({ search, onUse, busy }: { search: QuoteSearch; onUse: (url: string) => void; busy: boolean }) {
  function offerRow(offer: LineItemQuote) {
    return <div key={offer.productUrl} className="candidate-offer">
      <strong>{offer.title} · {money.format(offer.priceCrc)}</strong>
      <QuoteStoreLink quote={offer} itemName={offer.title} />
      <ReviewLink quote={offer} />
      <button type="button" className="button button-secondary button-small" disabled={busy} onClick={() => onUse(offer.productUrl)}
        aria-label={`Usar este precio para ${offer.title} en ${offer.storeName}`}>Usar este precio</button>
    </div>;
  }
  const [first, ...more] = search.offers;
  return <div className="search-findings">
    <strong>{first ? 'Precios publicados para revisar' : 'Última búsqueda'}</strong>
    <time dateTime={search.checkedAt}>{quoteDate.format(new Date(search.checkedAt))}</time>
    <p>{search.detail}</p>
    {first && offerRow(first)}
    {more.length > 0 && <details className="quote-alternatives"><summary>Ver {more.length === 1 ? 'otra oferta' : `otras ${more.length} ofertas`}</summary>
      {more.map(offerRow)}
    </details>}
  </div>;
}

export default function Inventory({ project, tier, tiers, onTierChange, active, pendingCount, saving, bulkScope, onOpenQuote, onBulkQuote, onItemAdded, onItemSplit, notice, activity, updateActivity }: {
  project: FurnishingProject;
  tier: BudgetTier;
  tiers: BudgetTier[];
  onTierChange: (tier: BudgetTier) => void;
  active: boolean;
  pendingCount: number;
  saving: boolean;
  bulkScope: 'pending' | 'all' | null;
  onOpenQuote: (item: CatalogItem, productUrl?: string) => void;
  onBulkQuote: (scope: 'pending' | 'all') => void;
  onItemAdded: (project: FurnishingProject, item: CatalogItem) => void;
  onItemSplit: (project: FurnishingProject, item: CatalogItem) => void;
  notice: { text: string; tone: 'error' | 'success' } | null;
  activity?: ReactNode;
  updateActivity: QuoteActivity | null;
}) {
  const [showManualForm, setShowManualForm] = useState(false);
  const [splitItem, setSplitItem] = useState<CatalogItem | null>(null);
  const [closed, setClosed] = useState<string[]>(() => {
    try {
      const stored: unknown = JSON.parse(localStorage.getItem('closed-rooms:' + project.id) ?? '[]');
      return Array.isArray(stored) ? stored.filter((v): v is string => typeof v === 'string') : [];
    } catch { return []; }
  });
  const [filterRooms, setFilterRooms] = useState({ key: '', closed: [] as string[] });
  const [search, setSearch] = useState('');
  const [areaId, setAreaId] = useState('');
  const [groupBy, setGroupBy] = useState<'area' | 'brand' | 'class'>('area');
  const [filter, setFilter] = useState<'all' | 'pending' | 'quoted'>('all');
  useEffect(() => { if (!active) { setShowManualForm(false); setSplitItem(null); } }, [active]);
  const query = normalizeSearch(search);
  const areas = [...projectAreas(project.zones), { id: 'unassigned', label: 'Sin zona asignada', zones: [] }];
  const zones = new Map(areas.flatMap((area) => area.zones.map((zone) => [zone.id, { area, name: zone.labelEs }] as const)));
  const defaultZones = areas.find(({ id }) => id === areaId)?.zones;
  const groups = new Map<string, CatalogItem[]>();

  for (const item of project.items) {
    const zone = zones.get(item.zoneId);
    const area = zone?.area.id ?? 'unassigned';
    if (areaId && area !== areaId) continue;
    const quotes = quotesForTier(item, tier);
    const key = groupBy === 'area' ? area : groupBy === 'brand' ? 'brand-' + brandLabel(quotes[0]) : 'class-' + productClass(quotes[0]);
    if (filter === 'pending' && quotes.length) continue;
    if (filter === 'quoted' && !quotes.length) continue;
    if (query && !normalizeSearch(`${brandLabel(quotes[0])} ${purchaseName(item, tier)} ${item.name} ${zone?.name ?? ''} ${zone?.area.label ?? ''} ${quotes.map(({ storeName, title }) => `${storeName} ${title}`).join(' ')}`).includes(query)) continue;
    const items = groups.get(key);
    if (items) items.push(item);
    else groups.set(key, [item]);
  }

  const sections = groupBy === 'area' ? areas : [...groups.keys()].sort((a,b) => a.localeCompare(b,'es')).map((id) => ({ id, label:id.slice(6), zones:[] }));
  const resultCount = [...groups.values()].reduce((count, items) => count + items.length, 0);
  const hasFilters = Boolean(search || areaId || filter !== 'all');

  const filterKey = JSON.stringify([query, areaId, filter, tier]);
  const collapsed = hasFilters ? (filterRooms.key === filterKey ? filterRooms.closed : []) : closed;
  function toggleRoom(id: string) {
    const next = collapsed.includes(id) ? collapsed.filter((room) => room !== id) : [...collapsed, id];
    if (hasFilters) setFilterRooms({ key: filterKey, closed: next });
    else {
      setClosed(next);
      try { localStorage.setItem('closed-rooms:' + project.id, JSON.stringify(next)); } catch { /* Storage is optional. */ }
    }
  }

  function clearFilters() {
    setSearch('');
    setAreaId('');
    setFilter('all');
  }

  return (
    <section id="inventario" className="panel inventory" aria-labelledby="inventory-title">
      <div className="panel-header inventory-header">
        <div>
          <h2 id="inventory-title" tabIndex={-1}>Inventario por ambiente</h2>
        </div>
        <div className="button-row">
          {project.items.length > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={() => onBulkQuote('all')}>
            {bulkScope === 'all' ? <><Spinner /> Actualizando…</> : <><RefreshCw size={17} aria-hidden="true" /> Actualizar todo</>}
          </button>}
          {pendingCount > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={() => onBulkQuote('pending')}>
            {bulkScope === 'pending' ? <><Spinner /> Cotizando…</> : <><Sparkles size={17} aria-hidden="true" /> Cotizar pendientes</>}
          </button>}
          <button type="button" className="button button-primary" disabled={saving} onClick={() => setShowManualForm(true)}><Plus size={18} aria-hidden="true" /> Agregar artículo</button>
        </div>
      </div>
      <div className="inventory-controls">
        <div className="inventory-budget">
          {tiers.length > 1 ? <div className="segmented" role="group" aria-label="Nivel de presupuesto visible">
            {tiers.map((option) => <button key={option} type="button" aria-pressed={option === tier} onClick={() => onTierChange(option)}>
              {budgetProfiles[option].label}
            </button>)}
          </div> : <strong>{budgetProfiles[tier].label}</strong>}
          <Link href={projectPath(project.id)}>Cambiar niveles</Link>
          {tiers.length > 1 && <p>Las acciones por lote buscan ofertas para todos los niveles seleccionados.</p>}
        </div>
        <BrandGuide />
        <div className="inventory-grouping"><span>Agrupar por</span><div className="segmented" role="group" aria-label="Agrupar inventario por">
          <button type="button" aria-pressed={groupBy === 'area'} onClick={() => setGroupBy('area')}>Ambiente</button>
          <button type="button" aria-pressed={groupBy === 'brand'} onClick={() => setGroupBy('brand')}>Marca</button>
          <button type="button" aria-pressed={groupBy === 'class'} onClick={() => setGroupBy('class')}>Gama y origen</button>
        </div></div>
        <div className="inventory-fields">
          <label className="search-field">
            <Search size={17} aria-hidden="true" />
            <span className="sr-only">Buscar artículos</span>
            <input type="search" placeholder="Buscar artículo…" value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
          <label className="select-field">
            <span className="sr-only">Filtrar por ambiente</span>
            <select value={areaId} onChange={(event) => setAreaId(event.target.value)}>
              <option value="">Todos los ambientes</option>
              {areas.map(({ id, label }) => <option key={id} value={id} hidden={id === 'unassigned' && !project.items.some((item) => !zones.has(item.zoneId))}>{label}</option>)}
            </select>
            <ChevronDown size={15} aria-hidden="true" />
          </label>
        </div>
        <div className="inventory-status">
          <div className="segmented" role="group" aria-label="Estado de cotización">
            <button type="button" aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>Todos</button>
            <button type="button" aria-pressed={filter === 'pending'} onClick={() => setFilter('pending')}>Pendientes</button>
            <button type="button" aria-pressed={filter === 'quoted'} onClick={() => setFilter('quoted')}>Con cotización</button>
          </div>
          <span className="result-count" role="status">{resultCount} {resultCount === 1 ? 'artículo' : 'artículos'}</span>
        </div>
        {notice && <Notice tone={notice.tone}>{notice.text}</Notice>}
        {activity}
      </div>
      {resultCount === 0 && (
        <div className="inventory-empty">
          <PackageOpen size={28} aria-hidden="true" />
          <p>{hasFilters ? 'No hay artículos que coincidan con los filtros.' : 'Todavía no hay artículos en el inventario.'}</p>
          {hasFilters && <button type="button" className="button button-secondary" onClick={clearFilters}>Limpiar filtros</button>}
        </div>
      )}
      {sections.map(({ id, label, zones: areaZones }) => {
        const items = groups.get(id);
        if (!items) return null;
        const { totalCrc, quotedCount } = projectStats(items, tier);
        return (
          <div className="inventory-zone" key={id}>
            <h3 className="room-heading"><button type="button" className="room-toggle" aria-expanded={!collapsed.includes(id)}
              aria-controls={'items-' + encodeURIComponent(id)} onClick={() => toggleRoom(id)}>
              <span className="room-name"><LayoutGrid size={17} aria-hidden="true" />{label}</span>
              <span className="room-count">{items.length} {items.length === 1 ? 'artículo' : 'artículos'} · {items.length - quotedCount} pendientes</span>
              <span className="room-subtotal">{quotedCount ? money.format(totalCrc) : 'Sin cotizar'}</span><ChevronDown size={17} aria-hidden="true" />
            </button></h3>
            <ul className="item-list" role="list" id={'items-' + encodeURIComponent(id)} hidden={collapsed.includes(id)}>
              {items.map((item) => {
                const [quote, ...alternatives] = quotesForTier(item, tier);
                const Icon = categoryIcons[item.category] ?? PackageOpen;
                return (
                  <li key={item.id} className="item-row">
                    <div className="item-name">
                      <span className="item-icon"><Icon size={18} aria-hidden="true" /></span>
                      <div>
                        <strong>{purchaseName(item, tier)}</strong>
                        {isFlexibleEnclosure(item) && <span>{enclosureAdvice(tier)}</span>}
                        {(areaZones.length > 1 || groupBy !== 'area') && <span>{zones.get(item.zoneId)?.name}</span>}
                        {(item.qty > 1 || isPurchaseSet(item)) && <span>{purchaseQuantity(item)}</span>}
                        {quote?.basis === 'published' && <><span>{quote.title}</span>
                          <span>Marca · {brandLabel(quote)}</span>
                          {productProvenance(quote.title, quote.brand) === 'replica' && <span>El vendedor lo anuncia como réplica.</span>}
                          {quote.modelIds?.length ? <details className="product-address"><summary>Identificación del producto</summary>
                            <span>Modelo o código comercial publicado · {quote.modelIds.join(', ')}</span><span>Autenticidad por confirmar con el fabricante.</span></details> : null}
                        </>}
                        {item.notes && <span>{item.notes}</span>}
                        {item.id === 'i-freight' && item.category === 'logistica' && !quote && <a href="https://todomudanzas.cr/fletes/" target="_blank" rel="noreferrer">Solicitar precio de flete</a>}
                        {!quote && item.unquotedReason && <span>{item.unquotedReason}</span>}
                      </div>
                    </div>
                    <div className="item-price">
                      {quote ? (
                        <>
                          <strong>{money.format(quote.priceCrc * item.qty)}</strong>
                          {item.qty > 1 && <span>{money.format(quote.priceCrc)} / {isPurchaseSet(item) ? 'juego' : 'unidad'}</span>}
                          <QuoteStoreLink quote={quote} itemName={item.name} />
                          <ReviewLink quote={quote} />
                          {quoteBasisLabel(quote) && <span>{quoteBasisLabel(quote)}</span>}
                          {quote.basis === 'published' && Number.isFinite(Date.parse(quote.capturedAt)) && <time className="quote-date" dateTime={quote.capturedAt}>
                            {quoteDate.format(new Date(quote.capturedAt))}
                          </time>}
                          {alternatives.length > 0 && <details className="quote-alternatives">
                            <summary>{alternatives.length === 1 ? '1 alternativa' : `${alternatives.length} alternativas`}</summary>
                            <ul>
                              {alternatives.map((alternative) => <li key={alternative.id}>
                                <span>{alternative.title} · {money.format(alternative.priceCrc * item.qty)}</span>
                                <QuoteStoreLink quote={alternative} itemName={item.name} />
                                <ReviewLink quote={alternative} />
                              </li>)}
                            </ul>
                          </details>}
                        </>
                      ) : <span className="pending-label">Sin cotizar</span>}
                    </div>
                    <div className="item-actions">
                      <button type="button" className="button button-small button-secondary" disabled={saving} onClick={() => onOpenQuote(item)} aria-label={`${quote ? 'Actualizar' : 'Cotizar'} ${item.name}`}>
                        {quote ? 'Actualizar' : 'Cotizar'}
                      </button>
                      <button type="button" className="button button-small item-split" disabled={saving} onClick={() => setSplitItem(item)} aria-label={`Dividir en artículos: ${item.name}`}>Dividir en artículos</button>
                    </div>
                    {item.lastSearch?.[tier] && <SearchFindings search={item.lastSearch[tier]!} busy={saving} onUse={(url) => onOpenQuote(item, url)} />}
                    {updateActivity?.itemId === item.id && updateActivity.tier === tier && !(updateActivity.stage === 'failed' && item.lastSearch?.[tier]?.detail === updateActivity.detail) && <div className="quote-single-progress quote-row-progress" role="status">
                      <QuoteJob activity={updateActivity} single />
                    </div>}
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
      {showManualForm && <ManualItemDialog project={project} defaultZone={defaultZones?.length === 1 ? defaultZones[0]!.id : ''} onDismiss={() => setShowManualForm(false)} onSuccess={(updated, item) => {
        clearFilters();
        onItemAdded(updated, item);
        setShowManualForm(false);
      }} />}
      {splitItem && <SplitItemDialog projectId={project.id} item={splitItem} onClose={() => setSplitItem(null)} onSaved={(updated) => {
        clearFilters();
        onItemSplit(updated, splitItem);
        setSplitItem(null);
        requestAnimationFrame(() => document.getElementById('inventory-title')?.focus({ preventScroll: true }));
      }} />}
    </section>
  );
}

function ManualItemDialog({ project, defaultZone, onDismiss, onSuccess }: {
  project: FurnishingProject;
  defaultZone: string;
  onDismiss: () => void;
  onSuccess: (updated: FurnishingProject, item: CatalogItem) => void;
}) {
  const [requestId] = useState(() => Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join(''));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const name = String(data.get('name')).trim();
    if (name.length < 2) {
      setError('Escribe un nombre de al menos dos caracteres.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await post<{ project: FurnishingProject; item: CatalogItem }>(`/api/projects/${project.id}/items`, {
        name, qty: Number(data.get('qty')), zoneId: String(data.get('zoneId') ?? ''),
        notes: String(data.get('notes')).trim(), requestId,
      });
      onSuccess(result.project, result.item);
    } catch (cause) {
      setError(cause instanceof ApiError && (cause.status === 400 || cause.status === 404) && cause.message
        ? cause.message : 'No se pudo guardar el artículo. Inténtalo de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  return <Modal title="Agregar artículo" onDismiss={onDismiss} busy={busy}>
    <form className="manual-form" onSubmit={submit}>
      <label htmlFor="item-name">Nombre</label>
      <input id="item-name" name="name" placeholder="Ej. Mamparas para ducha" minLength={2} maxLength={120} autoFocus required readOnly={busy} />
      <div className="manual-fields">
        <div>
          <label htmlFor="item-zone">Zona</label>
          <div className="select-field">
            <select id="item-zone" name="zoneId" defaultValue={defaultZone} disabled={busy}>
              <option value="">Sin zona asignada</option>
              {project.zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.labelEs}</option>)}
            </select>
            <ChevronDown size={15} aria-hidden="true" />
          </div>
        </div>
        <div>
          <label htmlFor="item-quantity">Cantidad</label>
          <input type="number" id="item-quantity" name="qty" min={1} max={999} step={1} defaultValue={1} required readOnly={busy} />
        </div>
      </div>
      <label htmlFor="item-notes">Notas <span>(opcional)</span></label>
      <textarea id="item-notes" name="notes" rows={3} maxLength={500} placeholder="Medidas, materiales u otros detalles" readOnly={busy} />
      {error && <Notice>{error}</Notice>}
      <div className="button-row modal-actions">
        <button type="button" className="button button-secondary" onClick={onDismiss} disabled={busy}>Cancelar</button>
        <button type="submit" className="button button-primary" disabled={busy}>{busy ? <><Spinner /> Guardando…</> : 'Agregar a pendientes'}</button>
      </div>
    </form>
  </Modal>;
}

export function QuoteDialog({ projectId, item, tier, onSearch, onDismiss, onSuccess }: {
  projectId: string;
  item: CatalogItem;
  tier: BudgetTier;
  onSearch: (event: QuoteActivity) => void;
  onDismiss: () => void;
  onSuccess: (project: FurnishingProject, message: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [activity, setActivity] = useState<QuoteActivity | null>(null);

  async function submit(productUrl = '') {
    if (productUrl && !/^https?:\/\//i.test(productUrl)) {
      setError('Usa un enlace que empiece con https:// o http://.');
      return;
    }
    setBusy(true);
    setError('');
    setActivity({ itemId: item.id, itemName: item.name, tier, stage: 'queued', detail: 'Iniciando la cotización.' });
    try {
      const result = await streamPost<QuoteActivity, { project: FurnishingProject; mode: 'url' | 'agent' | 'catalog' | 'estimate' }>(
        `/api/projects/${projectId}/items/${item.id}/quote`,
        productUrl ? { productUrl, tier } : { tier },
        (event) => { onSearch(event); setActivity(event); },
      );
      const modeLabel = { url: 'Precio verificado', agent: 'Precio verificado por el agente', catalog: 'Precio de catálogo guardado', estimate: 'Estimación guardada' };
      onSuccess(result.project, `${modeLabel[result.mode]} para ${item.name}.`);
    } catch (cause) {
      let message = 'No se pudo guardar la cotización. Inténtalo de nuevo.';
      if (cause instanceof ApiError && cause.status === 400) message = 'Usa un enlace público y válido de la tienda.';
      if (cause instanceof ApiError && [409, 422].includes(cause.status) && cause.message) message = cause.message;
      setActivity((event) => event?.search ? event : null);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Cotizar artículo" onDismiss={onDismiss} busy={busy}>
      <p className="quote-item-title">{purchaseName(item, tier)}</p>
      <p className="quote-tier-label">{budgetProfiles[tier].label}</p>
      {activity?.search ? <SearchFindings search={activity.search} busy={busy} onUse={submit} /> : activity && <div className="quote-single-progress" role="status"><QuoteJob activity={activity} single /></div>}
      {error && error !== activity?.search?.detail && <Notice>{error}</Notice>}
      <div className="button-row modal-actions">
        <button type="button" className="button button-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? <><Spinner /> Cotizando…</> : 'Buscar en tiendas'}
        </button>
      </div>
      <details className="quote-manual" hidden={busy}>
        <summary>Usar un enlace específico</summary>
        <form className="quote-form" onSubmit={(event) => {
          event.preventDefault();
          void submit(String(new FormData(event.currentTarget).get('productUrl')).trim());
        }}>
          <label htmlFor="product-url">Enlace del producto</label>
          <div className="search-field">
            <Link2 size={17} aria-hidden="true" />
            <input id="product-url" name="productUrl" type="url" placeholder="https://tienda.cr/producto" required />
          </div>
          <div className="button-row modal-actions">
            <button type="submit" className="button button-secondary">Verificar enlace</button>
          </div>
        </form>
      </details>
    </Modal>
  );
}
