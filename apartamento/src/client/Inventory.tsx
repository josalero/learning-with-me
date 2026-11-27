import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Armchair, ArrowUpRight, Bath, BedDouble, ChevronDown, CookingPot, Fan, House, Lamp, Link2, Minus, PackageOpen, Plus, RefreshCw, Search, Sofa, Sparkles, Truck, Tv, Utensils, WashingMachine } from 'lucide-react';
import { budgetProfiles, quotesForTier } from '@shared/budgetProfiles';
import { brandLabel, productClass, productProvenance } from '@shared/brands';
import { enclosureAdvice, isFlexibleEnclosure, purchaseName } from '@shared/showerEnclosure';
import { BrandGuide } from './BrandGuide';
import { projectAreas } from '@shared/projectAreas';
import { projectStats } from '@shared/projectStats';
import { isPurchaseSet } from '@shared/purchaseUnit';
import { quoteBasisLabel, quoteLink } from '@shared/quoteBasis';
import { normalizeSearch } from '@shared/search';
import { BUDGET_TIERS, type BudgetTier, type CatalogItem, type FurnishingProject, type LineItemQuote, type QuoteActivity, type QuoteSearch } from '@shared/types';
import { MoveItemDialog, unassignedLabel } from './ItemLocation';
import { inventoryPath, Link, navigate, projectPath } from './navigation';
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

function QuoteStoreLink({ quote, itemName, showAddress = true }: { quote: LineItemQuote; itemName: string; showAddress?: boolean }) {
  const link = quoteLink(quote);
  if (!link) return <span>Pendiente de verificar el enlace en {quote.storeName}</span>;
  const label = `Ver en ${quote.storeName}`;
  return <div className="product-link"><a href={link.url} target="_blank" rel="noreferrer" aria-label={`${label} para ${itemName} (abre en otra pestaña)`}>
    {label} <ArrowUpRight size={14} aria-hidden="true" />
  </a>{showAddress && <details className="product-address"><summary>Ver URL</summary><span>{link.url}</span></details>}</div>;
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

export type BulkScope = 'pending' | 'all' | 'room';
export const unassignedRoomId = 'sin-asignar';

const areaIcons: Record<string, typeof Sofa> = {
  sala: Sofa, comedor: Utensils, cocina: CookingPot, cuartos: BedDouble, banos: Bath,
  lavanderia: WashingMachine, terraza: Armchair, 'zone:z-logistics': Truck,
};

type Room = { id: string; label: string; icon: typeof Sofa; zoneId: string | null; items: CatalogItem[] };
type InventoryFilters = { query: string; status: 'all' | 'pending' | 'quoted'; group: 'plan' | 'brand' | 'class' };
const defaultFilters: InventoryFilters = { query: '', status: 'all', group: 'plan' };

function projectRooms(project: FurnishingProject): Room[] {
  const rooms = projectAreas(project.zones).flatMap(({ id, zones }) => zones.map((zone): Room => ({
    id: zone.id, label: zone.labelEs, icon: areaIcons[id] ?? House, zoneId: zone.id, items: [],
  })));
  const byZone = new Map(rooms.map((room) => [room.zoneId, room]));
  const unassigned = { id: unassignedRoomId, label: unassignedLabel, icon: PackageOpen, zoneId: null, items: [] as CatalogItem[] };
  for (const item of project.items) (byZone.get(item.zoneId) ?? unassigned).items.push(item);
  if (unassigned.items.length || !rooms.length) rooms.push(unassigned);
  return rooms;
}

function lineCount(count: number) {
  return `${count} ${count === 1 ? 'artículo' : 'artículos'}`;
}

function quantity(value: string) {
  const number = Number(value);
  return /^\d+$/.test(value) && Number.isInteger(number) && number >= 1 && number <= 999 ? number : undefined;
}

function RoomLinks({ projectId, rooms, currentRoom }: { projectId: string; rooms: Room[]; currentRoom?: Room }) {
  return <ul className="planner-room-links" role="list">{rooms.map(({ id, icon: Icon, label, items }) => (
    <li key={id}>
      <Link href={inventoryPath(projectId, id)} aria-current={currentRoom?.id === id ? 'page' : undefined}>
        <Icon size={17} aria-hidden="true" /><span>{label}</span>
        <small aria-label={lineCount(items.length)}>{items.length}</small>
      </Link>
    </li>
  ))}</ul>;
}

function PlannerTotal({ label, items, tier, hasDraft }: { label: string; items: CatalogItem[]; tier: BudgetTier; hasDraft: boolean }) {
  const { totalCrc, quotedCount, itemCount } = projectStats(items, tier);
  return <div className="planner-total">
    <span>{label}</span>
    <strong aria-live="polite">{quotedCount ? money.format(totalCrc) : 'Por cotizar'}</strong>
    {quotedCount > 0 && quotedCount < itemCount && <small>Parcial · {quotedCount} de {itemCount} cotizados</small>}
    {hasDraft && <small>Vista previa · cantidades sin guardar</small>}
  </div>;
}

export default function Inventory({ project, roomId, tier, onTierChange, active, pendingCount, saving, bulkScope, savingItemId,
  onSaveQuantity, onMoveItem, onItemMoved, onOpenQuote, onEnterQuoteUrl, onBulkQuote, onItemAdded, onItemSplit, notice, activity, updateActivity }: {
  project: FurnishingProject;
  roomId?: string;
  tier: BudgetTier;
  onTierChange: (tier: BudgetTier) => void;
  active: boolean;
  pendingCount: number;
  saving: boolean;
  bulkScope: BulkScope | null;
  savingItemId: string | null;
  onSaveQuantity: (itemId: string, qty: number) => Promise<void>;
  onMoveItem: (itemId: string, zoneId: string) => Promise<void>;
  onItemMoved: (item: CatalogItem, zoneId: string) => void;
  onOpenQuote: (item: CatalogItem, productUrl?: string) => void;
  onEnterQuoteUrl: (item: CatalogItem) => void;
  onBulkQuote: (scope: BulkScope, roomItems?: CatalogItem[], zoneId?: string | null) => void;
  onItemAdded: (project: FurnishingProject, item: CatalogItem) => void;
  onItemSplit: (project: FurnishingProject, item: CatalogItem) => void;
  notice: { text: string; tone: 'error' | 'success' } | null;
  activity?: ReactNode;
  updateActivity: QuoteActivity | null;
}) {
  const [showManualForm, setShowManualForm] = useState(false);
  const [splitItem, setSplitItem] = useState<CatalogItem | null>(null);
  const [movingItem, setMovingItem] = useState<CatalogItem | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [filterState, setFilterState] = useState({ roomId, ...defaultFilters });
  useEffect(() => { setShowManualForm(false); setSplitItem(null); setMovingItem(null); }, [active, roomId]);

  const rooms = projectRooms(project);
  const room = roomId === undefined ? rooms[0] : rooms.find(({ id }) => id === roomId);
  const filters = filterState.roomId === roomId ? filterState : defaultFilters;
  const roomItems = room?.items ?? [];
  const roomPending = roomItems.filter((item) => !quotesForTier(item, tier).length).length;
  const query = normalizeSearch(filters.query);
  const visible = roomItems.filter((item) => {
    const quotes = quotesForTier(item, tier);
    if (filters.status === 'pending' && quotes.length) return false;
    if (filters.status === 'quoted' && !quotes.length) return false;
    return !query || normalizeSearch(`${purchaseName(item, tier)} ${brandLabel(quotes[0])} ${quotes.map(({ storeName, title }) => `${storeName} ${title}`).join(' ')}`).includes(query);
  });
  const groups = new Map<string, CatalogItem[]>();
  for (const item of visible) {
    const [quote] = quotesForTier(item, tier);
    const name = filters.group === 'brand' ? brandLabel(quote) : filters.group === 'class' ? productClass(quote) : '';
    const group = groups.get(name);
    if (group) group.push(item);
    else groups.set(name, [item]);
  }

  function preview(items: CatalogItem[]) {
    return items.map((item) => {
      const value = quantity(drafts[item.id] ?? '');
      return value === undefined || value === item.qty ? item : { ...item, qty: value };
    });
  }

  function setFilters(update: Partial<InventoryFilters>) {
    setFilterState({ ...filters, ...update, roomId });
  }

  function changeDraft(item: CatalogItem, value: string) {
    setDrafts((previous) => {
      const next = { ...previous };
      if (value === String(item.qty)) delete next[item.id];
      else next[item.id] = value;
      return next;
    });
  }

  async function save(item: CatalogItem, value: number) {
    await onSaveQuantity(item.id, value);
    changeDraft(item, String(item.qty));
  }

  const previewRoom = preview(roomItems);
  const previewProject = preview(project.items);
  const projectTotal = <PlannerTotal label="Total del proyecto" tier={tier} items={previewProject}
    hasDraft={previewProject.some((item, index) => item !== project.items[index])} />;

  return (
    <section id="inventario" className="planner-layout" aria-labelledby="inventory-title">
      <aside className="planner-sidebar panel">
        <nav aria-label="Ambientes de la casa"><h2>Ambientes</h2>
          <RoomLinks projectId={project.id} rooms={rooms} currentRoom={room} />
        </nav>
        {projectTotal}
      </aside>
      <div className="planner-workspace">
        <details className="planner-room-picker panel" key={room?.id ?? roomId}>
          <summary><House size={17} aria-hidden="true" /> Cambiar de ambiente <ChevronDown size={16} aria-hidden="true" /></summary>
          <nav aria-label="Ambientes de la casa"><RoomLinks projectId={project.id} rooms={rooms} currentRoom={room} /></nav>
        </details>
        <div className="planner-scenario panel">
          <fieldset disabled={saving}>
            <legend>Presupuesto activo</legend>
            <div className="segmented scenario-options">
              {BUDGET_TIERS.map((option) => <label key={option}>
                <input className="sr-only" type="radio" name={`scenario-${project.id}`} checked={option === tier} onChange={() => onTierChange(option)} />
                <span>{budgetProfiles[option].label}</span>
              </label>)}
            </div>
          </fieldset>
          {pendingCount > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={() => onBulkQuote('pending')}>
            {bulkScope === 'pending' ? <><Spinner /> Cotizando…</> : <><Sparkles size={16} aria-hidden="true" /> Cotizar proyecto</>}
          </button>}
        </div>
        <header className="planner-heading">
          <div className="page-heading">
            {room && <p><room.icon size={17} aria-hidden="true" /> Equipamiento por ambiente</p>}
            <h1 id="inventory-title" tabIndex={-1}>{room?.label ?? 'Ambiente no disponible'}</h1>
            {room && <p>{lineCount(roomItems.length)} · {roomPending} por cotizar</p>}
          </div>
          {room && <PlannerTotal label="Subtotal del ambiente" items={previewRoom} tier={tier}
            hasDraft={previewRoom.some((item, index) => item !== roomItems[index])} />}
        </header>
        {room ? <>
          <div className="planner-controls">
            <div className="segmented" role="group" aria-label="Estado de cotización">
              <button type="button" aria-pressed={filters.status === 'all'} onClick={() => setFilters({ status: 'all' })}>Todos</button>
              <button type="button" aria-pressed={filters.status === 'pending'} onClick={() => setFilters({ status: 'pending' })}>Por cotizar</button>
              <button type="button" aria-pressed={filters.status === 'quoted'} onClick={() => setFilters({ status: 'quoted' })}>Con precio</button>
            </div>
            <div className="button-row">
              {roomPending > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={() => onBulkQuote('room', room.items, room.zoneId)}>
                {bulkScope === 'room' ? <><Spinner /> Cotizando…</> : 'Cotizar ambiente'}
              </button>}
              <button type="button" className="button button-primary" disabled={saving} onClick={() => setShowManualForm(true)}>
                <Plus size={17} aria-hidden="true" /> Agregar artículo
              </button>
            </div>
          </div>
          <div className="planner-options">
            <label className="search-field">
              <Search size={17} aria-hidden="true" /><span className="sr-only">Buscar en este ambiente</span>
              <input type="search" placeholder="Buscar en este ambiente" value={filters.query} onChange={(event) => setFilters({ query: event.target.value })} />
            </label>
            <details className="planner-settings">
              <summary>Opciones del presupuesto <ChevronDown size={15} aria-hidden="true" /></summary>
              <div className="planner-settings-body panel">
                <Link className="text-action" href={projectPath(project.id)}>Comparar los tres presupuestos</Link>
                <div className="segmented" role="group" aria-label="Agrupar artículos">
                  <button type="button" aria-pressed={filters.group === 'plan'} onClick={() => setFilters({ group: 'plan' })}>Sin agrupar</button>
                  <button type="button" aria-pressed={filters.group === 'brand'} onClick={() => setFilters({ group: 'brand' })}>Por marca</button>
                  <button type="button" aria-pressed={filters.group === 'class'} onClick={() => setFilters({ group: 'class' })}>Por gama y origen</button>
                </div>
                <BrandGuide />
                <div className="button-row">
                  {project.items.length > 0 && <button type="button" className="button button-secondary" disabled={saving} onClick={() => onBulkQuote('all')}>
                    {bulkScope === 'all' ? <><Spinner /> Actualizando…</> : <><RefreshCw size={16} aria-hidden="true" /> Actualizar todo el proyecto</>}
                  </button>}
                </div>
              </div>
            </details>
          </div>
          {notice && <Notice tone={notice.tone}>{notice.text}</Notice>}
          {activity}
          <section className="planner-sheet panel" aria-label={`Artículos de ${room.label}`}>
            {visible.length > 0 ? <>
              {filters.group === 'plan' && <h2 className="sr-only">Artículos del ambiente</h2>}
              <div className="planner-sheet-head planner-columns" aria-hidden="true"><span>Artículo y ficha</span><span>Precio unitario</span><span>Cantidad</span><span>Subtotal</span></div>
              {[...groups.entries()].sort(([a], [b]) => a.localeCompare(b, 'es')).map(([groupName, items]) => (
                <div className="planner-group" key={groupName}>
                  {groupName && <h2>{groupName}</h2>}
                  <ul className="planner-items" role="list">{items.map((item) => (
                    <InventoryItem key={item.id} item={item} tier={tier} disabled={saving} saving={savingItemId === item.id}
                      draft={drafts[item.id]} onChange={(value) => changeDraft(item, value)} onSave={(value) => save(item, value)}
                      onQuote={(url) => onOpenQuote(item, url)} onEnterQuoteUrl={() => onEnterQuoteUrl(item)} onSplit={() => setSplitItem(item)}
                      onMove={() => setMovingItem(item)}
                      updateActivity={updateActivity?.itemId === item.id && updateActivity.tier === tier ? updateActivity : undefined} />
                  ))}</ul>
                </div>
              ))}
            </> : <div className="inventory-empty">
              <PackageOpen size={25} aria-hidden="true" />
              <p>{roomItems.length === 0 ? 'Este ambiente todavía no tiene artículos.' : query ? 'No hay artículos que coincidan con la búsqueda.'
                : filters.status === 'pending' ? 'Todos los artículos de este ambiente tienen precio.' : 'Aún no hay artículos con precio en este ambiente.'}</p>
              {(query || filters.status !== 'all') && <button type="button" className="button button-secondary" onClick={() => setFilters({ query: '', status: 'all' })}>Ver todos los artículos</button>}
            </div>}
          </section>
        </> : <div className="panel page-state"><p>Elegí un ambiente del proyecto para continuar.</p><Link className="button button-secondary" href={inventoryPath(project.id)}>Ver el primer ambiente</Link></div>}
        <div className="planner-mobile-total">{projectTotal}</div>
      </div>
      {showManualForm && <ManualItemDialog project={project} defaultZone={room?.zoneId ?? ''} onDismiss={() => setShowManualForm(false)} onSuccess={(updated, item) => {
        setFilters(defaultFilters);
        onItemAdded(updated, item);
        setShowManualForm(false);
        navigate(inventoryPath(project.id, item.zoneId || unassignedRoomId));
      }} />}
      {movingItem && <MoveItemDialog item={movingItem} zones={project.zones} onClose={() => setMovingItem(null)}
        onSave={(zoneId) => onMoveItem(movingItem.id, zoneId)} onMoved={(zoneId) => {
          onItemMoved(movingItem, zoneId);
          setFilterState({ ...defaultFilters, roomId: zoneId || unassignedRoomId });
          setMovingItem(null);
          navigate(inventoryPath(project.id, zoneId || unassignedRoomId));
        }} />}
      {splitItem && <SplitItemDialog projectId={project.id} item={splitItem} zones={project.zones} onClose={() => setSplitItem(null)} onSaved={(updated) => {
        setFilters(defaultFilters);
        changeDraft(splitItem, String(splitItem.qty));
        onItemSplit(updated, splitItem);
        setSplitItem(null);
        requestAnimationFrame(() => document.getElementById('inventory-title')?.focus({ preventScroll: true }));
      }} />}
    </section>
  );
}

function InventoryItem({ item, tier, draft, disabled, saving, onChange, onSave, onQuote, onEnterQuoteUrl, onSplit, onMove, updateActivity }: {
  item: CatalogItem;
  tier: BudgetTier;
  draft?: string;
  disabled: boolean;
  saving: boolean;
  onChange: (value: string) => void;
  onSave: (value: number) => Promise<void>;
  onQuote: (url?: string) => void;
  onEnterQuoteUrl: () => void;
  onSplit: () => void;
  onMove: () => void;
  updateActivity?: QuoteActivity;
}) {
  const [error, setError] = useState('');
  const [showDetails, setShowDetails] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const [quote, ...alternatives] = quotesForTier(item, tier);
  const name = purchaseName(item, tier);
  const Icon = categoryIcons[item.category] ?? PackageOpen;
  const count = draft === undefined ? item.qty : quantity(draft);
  const isSet = isPurchaseSet(item);
  const unit = isSet ? 'juego' : 'unidad';
  const dirty = draft !== undefined && count !== item.qty;
  const errorId = `qty-error-${item.id}`;
  const detailsId = `item-details-${item.id}`;
  const search = item.lastSearch?.[tier];
  const address = quote && quoteLink(quote)?.url;

  function change(value: string) {
    setError('');
    onChange(value);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || count === undefined || !dirty) return;
    setError('');
    try {
      await onSave(count);
      if (document.activeElement === document.body) input.current?.focus({ preventScroll: true });
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : 'No se pudo guardar la cantidad. Intentá de nuevo.');
    }
  }

  return <li className="planner-item">
    <div className="planner-item-main planner-columns">
      <div className="planner-item-info">
        <span className="item-icon"><Icon size={17} aria-hidden="true" /></span>
        <div>
          <h3>{name}</h3>
          {quote ? <><p>{quote.title}</p><QuoteStoreLink quote={quote} itemName={name} showAddress={false} /></>
            : <p>{item.unquotedReason || 'Sin oferta guardada'}</p>}
          {item.id === 'i-freight' && item.category === 'logistica' && !quote &&
            <a className="text-action" href="https://todomudanzas.cr/fletes/" target="_blank" rel="noreferrer">Solicitar precio de flete</a>}
        </div>
      </div>
      <div className="planner-price">
        <span className="planner-field-label">Precio unitario</span>
        {quote ? <><strong>{money.format(quote.priceCrc)}</strong><small>por {unit}</small>
          {quote.basis !== 'published' && <small>{quoteBasisLabel(quote)}</small>}
        </> : <span className="pending-label">Por cotizar</span>}
      </div>
      <form className="planner-quantity" onSubmit={submit} aria-busy={saving} noValidate>
        <span className="planner-field-label">Cantidad</span>
        <div className="planner-stepper">
          <button type="button" disabled={disabled || count === undefined || count <= 1} onClick={() => change(String((count ?? item.qty) - 1))} aria-label={`Restar ${unit} de ${name}`}><Minus size={15} aria-hidden="true" /></button>
          <input ref={input} type="number" inputMode="numeric" min={1} max={999} step={1} required
            value={draft ?? String(item.qty)} readOnly={disabled} aria-label={`Cantidad de ${name}`}
            aria-invalid={dirty && count === undefined}
            aria-describedby={count === undefined || error ? errorId : undefined}
            onChange={(event) => change(event.target.value)} />
          <button type="button" disabled={disabled || count === undefined || count >= 999} onClick={() => change(String((count ?? item.qty) + 1))} aria-label={`Sumar ${unit} de ${name}`}><Plus size={15} aria-hidden="true" /></button>
        </div>
        <small>{count === 1 ? unit : isSet ? 'juegos' : 'unidades'}</small>
        {dirty && <button type="submit" className="button button-secondary button-small planner-save" disabled={disabled || count === undefined} aria-label={`Guardar cantidad de ${name}`}>
          {saving ? <><Spinner /> Guardando</> : 'Guardar'}
        </button>}
        {(count === undefined || error) && <small id={errorId} className="planner-error" role="alert">{error || 'Ingresá un entero de 1 a 999.'}</small>}
      </form>
      <div className="planner-price planner-subtotal">
        <span className="planner-field-label">Subtotal</span>
        <strong>{quote && count !== undefined ? money.format(quote.priceCrc * count) : 'Pendiente'}</strong>
      </div>
      <div className="planner-item-actions">
        <button type="button" className={`button button-small ${quote ? 'button-secondary' : 'button-primary'}`} disabled={disabled} onClick={() => onQuote()} aria-label={`${quote ? 'Actualizar' : 'Cotizar'} ${name}`}>
          {quote ? 'Actualizar' : 'Cotizar'}
        </button>
        <button type="button" className="planner-item-more" aria-expanded={showDetails} aria-controls={detailsId}
          aria-label={`Detalles de ${name}`} onClick={() => setShowDetails(!showDetails)}>Detalles <ChevronDown size={13} aria-hidden="true" /></button>
      </div>
    </div>
    <div id={detailsId} className="planner-item-detail-body" hidden={!showDetails}>
      {item.notes && <p>{item.notes}</p>}
      {isFlexibleEnclosure(item) && <p>{enclosureAdvice(tier)}</p>}
      {quote && <>
        {address && <details className="product-address"><summary>Ver URL de la ficha</summary><span>{address}</span></details>}
        <p>Marca · {brandLabel(quote)}</p>
        {productProvenance(quote.title, quote.brand) === 'replica' && <p>El vendedor lo anuncia como réplica.</p>}
        {!!quote.modelIds?.length && <p>Modelo publicado · {quote.modelIds.join(', ')}. Autenticidad por confirmar con el fabricante.</p>}
        {quoteBasisLabel(quote) && <p>{quoteBasisLabel(quote)}</p>}
        <ReviewLink quote={quote} />
        {quote.basis === 'published' && Number.isFinite(Date.parse(quote.capturedAt)) &&
          <time className="quote-date" dateTime={quote.capturedAt}>{quoteDate.format(new Date(quote.capturedAt))}</time>}
        <button type="button" className="text-action" disabled={disabled} onClick={onEnterQuoteUrl}>Cambiar ficha del producto</button>
      </>}
      {alternatives.length > 0 && <ul className="planner-alternatives" role="list">{alternatives.map((alternative) => <li key={alternative.id}>
        <strong>{alternative.title} · {money.format(alternative.priceCrc)} por {unit}</strong>
        <QuoteStoreLink quote={alternative} itemName={name} /><ReviewLink quote={alternative} />
        <button type="button" className="text-action" disabled={disabled} onClick={() => onQuote(alternative.productUrl)}>Usar este precio</button>
      </li>)}</ul>}
      <button type="button" className="text-action" disabled={disabled} onClick={onMove} aria-label={`Mover a otro ambiente: ${name}`}>Mover a otro ambiente</button>
      <button type="button" className="text-action" disabled={disabled} onClick={onSplit} aria-label={`Dividir en artículos: ${name}`}>Dividir en artículos</button>
    </div>
    {search && <SearchFindings search={search} busy={disabled} onUse={onQuote} />}
    {updateActivity && !(updateActivity.stage === 'failed' && search?.detail === updateActivity.detail) &&
      <div className="quote-single-progress" role="status"><QuoteJob activity={updateActivity} single /></div>}
  </li>;
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
        notes: String(data.get('notes')).trim(), purchaseUnit: String(data.get('purchaseUnit')), requestId,
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
      <fieldset className="manual-purchase-unit">
        <legend>Cómo se cotiza</legend>
        <label><input type="radio" name="purchaseUnit" value="unit" defaultChecked disabled={busy} /> Por unidad</label>
        <label><input type="radio" name="purchaseUnit" value="set" disabled={busy} /> Por juego</label>
      </fieldset>
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
  const hasQuote = quotesForTier(item, tier).length > 0;

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
    <Modal title={hasQuote ? 'Cambiar ficha del producto' : 'Cotizar artículo'} onDismiss={onDismiss} busy={busy}>
      <p className="quote-item-title">{purchaseName(item, tier)} · Precio por {isPurchaseSet(item) ? 'juego' : 'unidad'}</p>
      <p className="quote-tier-label">{budgetProfiles[tier].label}</p>
      {activity?.search ? <SearchFindings search={activity.search} busy={busy} onUse={submit} /> : activity && <div className="quote-single-progress" role="status"><QuoteJob activity={activity} single /></div>}
      {error && error !== activity?.search?.detail && <Notice>{error}</Notice>}
      <div className="button-row modal-actions">
        <button type="button" className="button button-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? <><Spinner /> Cotizando…</> : 'Buscar en tiendas'}
        </button>
      </div>
      <details className="quote-manual" open={hasQuote || undefined} hidden={busy}>
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
