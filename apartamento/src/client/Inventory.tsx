import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { ArrowUpRight, ChevronDown, Fan, Lamp, LayoutGrid, Link2, PackageOpen, Plus, Search, Sofa, Sparkles, Tv, WashingMachine } from 'lucide-react';
import { quoteBasisLabel, quoteLink } from '@shared/quoteBasis';
import { normalizeSearch } from '@shared/search';
import type { CatalogItem, FurnishingProject, LineItemQuote, QuoteActivity } from '@shared/types';
import { QuoteJob } from './QuoteProgress';
import { streamPost } from './streamApi';
import { ApiError, Modal, money, Notice, post, Spinner } from './ui';

const categoryIcons: Record<string, typeof Sofa> = {
  mobiliario: Sofa,
  electrodomesticos: WashingMachine,
  electronica: Tv,
  climatizacion: Fan,
  exterior: Lamp,
};

function QuoteStoreLink({ quote, itemName }: { quote: LineItemQuote; itemName: string }) {
  const link = quoteLink(quote);
  if (!link) return <span>{quote.storeName}</span>;
  const label = `${link.label} en ${quote.storeName}`;
  return <a href={link.url} target="_blank" rel="noreferrer" aria-label={`${label} para ${itemName} (abre en otra pestaña)`}>
    {label} <ArrowUpRight size={12} aria-hidden="true" />
  </a>;
}

export default function Inventory({ project, active, pendingCount, saving, bulkBusy, onOpenQuote, onBulkQuote, onItemAdded, notice, activity, updateActivity }: {
  project: FurnishingProject;
  active: boolean;
  pendingCount: number;
  saving: boolean;
  bulkBusy: boolean;
  onOpenQuote: (item: CatalogItem) => void;
  onBulkQuote: () => void;
  onItemAdded: (project: FurnishingProject, item: CatalogItem) => void;
  notice: { text: string; tone: 'error' | 'success' } | null;
  activity?: ReactNode;
  updateActivity: QuoteActivity | null;
}) {
  const [showManualForm, setShowManualForm] = useState(false);
  const [search, setSearch] = useState('');
  const [zoneId, setZoneId] = useState('');
  const [filter, setFilter] = useState<'all' | 'pending' | 'quoted'>('all');
  useEffect(() => { if (!active) setShowManualForm(false); }, [active]);
  const query = normalizeSearch(search);
  const zones = new Map(project.zones.map((zone) => [zone.id, zone.labelEs]));
  const groups = new Map<string, CatalogItem[]>();

  for (const item of project.items) {
    if (zoneId && item.zoneId !== zoneId) continue;
    if (filter === 'pending' && item.quotes.length > 0) continue;
    if (filter === 'quoted' && !item.quotes.length) continue;
    if (query && !normalizeSearch(`${item.name} ${zones.get(item.zoneId) ?? ''} ${item.quotes.map(({ storeName }) => storeName).join(' ')}`).includes(query)) continue;
    const items = groups.get(item.zoneId);
    if (items) items.push(item);
    else groups.set(item.zoneId, [item]);
  }

  const displayZones = [...new Set([...zones.keys(), ...groups.keys()])];
  const resultCount = [...groups.values()].reduce((count, items) => count + items.length, 0);
  const hasFilters = Boolean(search || zoneId || filter !== 'all');

  function clearFilters() {
    setSearch('');
    setZoneId('');
    setFilter('all');
  }

  return (
    <section id="inventario" className="panel inventory" aria-labelledby="inventory-title">
      <div className="panel-header inventory-header">
        <div>
          <h2 id="inventory-title" tabIndex={-1}>Inventario por zona</h2>
        </div>
        <div className="button-row">
          {pendingCount > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={onBulkQuote}>
            {bulkBusy ? <><Spinner /> Cotizando…</> : <><Sparkles size={17} aria-hidden="true" /> Cotizar pendientes</>}
          </button>}
          <button type="button" className="button button-primary" disabled={saving} onClick={() => setShowManualForm(true)}><Plus size={18} aria-hidden="true" /> Agregar artículo</button>
        </div>
      </div>
      <div className="inventory-controls">
        <div className="inventory-fields">
          <label className="search-field">
            <Search size={17} aria-hidden="true" />
            <span className="sr-only">Buscar artículos</span>
            <input type="search" placeholder="Buscar artículo…" value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
          <label className="select-field">
            <span className="sr-only">Filtrar por zona</span>
            <select value={zoneId} onChange={(event) => setZoneId(event.target.value)}>
              <option value="">Todas las zonas</option>
              {project.zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.labelEs}</option>)}
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
      {displayZones.map((id) => {
        const items = groups.get(id);
        if (!items) return null;
        const subtotal = items.reduce((sum, item) => sum + (item.quotes[0]?.priceCrc ?? 0) * item.qty, 0);
        return (
          <div className="inventory-zone" key={id}>
            <div className="zone-heading">
              <h3><LayoutGrid size={15} aria-hidden="true" /> {zones.get(id) ?? 'Sin zona asignada'}</h3>
              {items.some((item) => item.quotes.length > 0) && <span><span className="sr-only">Subtotal de artículos visibles </span>{money.format(subtotal)}</span>}
            </div>
            <ul className="item-list" role="list">
              {items.map((item) => {
                const [quote, ...alternatives] = item.quotes;
                const Icon = categoryIcons[item.category] ?? PackageOpen;
                return (
                  <li key={item.id} className="item-row">
                    <div className="item-name">
                      <span className="item-icon"><Icon size={18} aria-hidden="true" /></span>
                      <div>
                        <strong>{item.name}</strong>
                        {item.qty > 1 && <span>{item.qty} unidades</span>}
                        {item.notes && <span>{item.notes}</span>}
                        {!quote && item.unquotedReason && <span>{item.unquotedReason}</span>}
                      </div>
                    </div>
                    <div className="item-price">
                      {quote ? (
                        <>
                          <strong>{money.format(quote.priceCrc * item.qty)}</strong>
                          {item.qty > 1 && <span>{money.format(quote.priceCrc)} / unidad</span>}
                          <QuoteStoreLink quote={quote} itemName={item.name} />
                          {quoteBasisLabel(quote) && <span>{quoteBasisLabel(quote)}</span>}
                          {alternatives.length > 0 && <details className="quote-alternatives">
                            <summary>{alternatives.length === 1 ? '1 alternativa' : `${alternatives.length} alternativas`}</summary>
                            <ul>
                              {alternatives.map((alternative) => <li key={alternative.id}>
                                <span>{money.format(alternative.priceCrc * item.qty)}</span>
                                <QuoteStoreLink quote={alternative} itemName={item.name} />
                              </li>)}
                            </ul>
                          </details>}
                        </>
                      ) : <span className="pending-label">Sin cotizar</span>}
                    </div>
                    <button type="button" className="button button-small button-secondary" disabled={saving} onClick={() => onOpenQuote(item)} aria-label={`${quote ? 'Actualizar' : 'Cotizar'} ${item.name}`}>
                      {quote ? 'Actualizar' : 'Cotizar'}
                    </button>
                    {updateActivity?.itemId === item.id && <div className="quote-single-progress quote-row-progress" role="status">
                      <QuoteJob activity={updateActivity} single />
                    </div>}
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
      {showManualForm && <ManualItemDialog project={project} defaultZone={zoneId} onDismiss={() => setShowManualForm(false)} onSuccess={(updated, item) => {
        clearFilters();
        onItemAdded(updated, item);
        setShowManualForm(false);
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

export function QuoteDialog({ projectId, item, onDismiss, onSuccess }: {
  projectId: string;
  item: CatalogItem;
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
    setActivity({ itemId: item.id, itemName: item.name, stage: 'queued', detail: 'Iniciando la cotización.' });
    try {
      const result = await streamPost<QuoteActivity, { project: FurnishingProject; mode: 'url' | 'agent' | 'catalog' | 'estimate' }>(
        `/api/projects/${projectId}/items/${item.id}/quote`,
        productUrl ? { productUrl } : {},
        setActivity,
      );
      const modeLabel = { url: 'Precio verificado', agent: 'Precio verificado por el agente', catalog: 'Precio de catálogo guardado', estimate: 'Estimación guardada' };
      onSuccess(result.project, `${modeLabel[result.mode]} para ${item.name}.`);
    } catch (cause) {
      let message = 'No se pudo guardar la cotización. Inténtalo de nuevo.';
      if (cause instanceof ApiError && cause.status === 400) message = 'Usa un enlace público y válido de la tienda.';
      if (cause instanceof ApiError && cause.status === 422 && cause.message) message = cause.message;
      setActivity(null);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Cotizar artículo" onDismiss={onDismiss} busy={busy}>
      <p className="quote-item-title">{item.name}</p>
      {activity && <div className="quote-single-progress" role="status"><QuoteJob activity={activity} single /></div>}
      {error && <Notice>{error}</Notice>}
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
