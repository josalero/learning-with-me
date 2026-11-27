import { useEffect, useState } from 'react';
import { ArrowRight, Boxes, ClipboardCheck, Images, ListChecks, MapPin } from 'lucide-react';
import { budgetQuoteJobs, primaryBudgetTier, quotesForTier } from '@shared/budgetProfiles';
import { BUDGET_TIERS, type BudgetQuote, type BudgetTier, type CatalogItem, type FurnishingProject, type QuoteActivity } from '@shared/types';
import AirbnbReview from './AirbnbReview';
import AgentReview from './AgentReview';
import BudgetSettings from './BudgetSettings';
import Inventory, { QuoteDialog, type BulkScope } from './Inventory';
import { unassignedLabel } from './ItemLocation';
import { ProjectAtlas, projectRooms, unassignedRoomId } from './ProjectAtlas';
import { inventoryPath, Link, navigate, projectPath, type ProjectView } from './navigation';
import References from './References';
import { QuoteProgress } from './QuoteProgress';
import { streamPost } from './streamApi';
import { api, ApiError } from './ui';
import { Workspace, type Account } from './Workspace';

const pageTitles = { resumen: 'Resumen', agentes: 'Agentes', inventario: 'Inventario', referencias: 'Referencias', airbnb: 'Revisión para Airbnb' };
const overviewLinks = [
  { view: 'airbnb', title: 'Revisión para Airbnb', description: 'Contrasta el plano con los requisitos del anuncio.', icon: ClipboardCheck },
  { view: 'agentes', title: 'Actividad de los agentes', description: 'Ejecuta la revisión y aprueba sus etapas.', icon: ListChecks },
  { view: 'inventario', title: 'Equipamiento por espacio', description: 'Revisa artículos, cantidades y cotizaciones.', icon: Boxes },
  { view: 'referencias', title: 'Referencias y análisis visual', description: 'Sube imágenes y detecta artículos adicionales.', icon: Images },
] as const;

type ActionNotice = { text: string; tone: 'error' | 'success' };

export default function Dashboard({ initialProject, view, roomId, ...account }: Account & { initialProject: FurnishingProject; view: ProjectView; roomId?: string }) {
  const [project, setProject] = useState(initialProject);
  const tierKey = `natarundi-budget:${initialProject.id}`;
  const [tier, setTier] = useState<BudgetTier>(() => {
    try {
      const saved = localStorage.getItem(tierKey);
      return BUDGET_TIERS.find((candidate) => candidate === saved) ?? primaryBudgetTier(initialProject);
    } catch { return primaryBudgetTier(initialProject); }
  });
  const [quoteItem, setQuoteItem] = useState<CatalogItem | null>(null);
  const [updating, setUpdating] = useState(false);
  const [updateActivity, setUpdateActivity] = useState<QuoteActivity | null>(null);
  const [bulkScope, setBulkScope] = useState<BulkScope | null>(null);
  const [bulkActivity, setBulkActivity] = useState<Record<string, QuoteActivity>>({});
  const [inventoryNotice, setInventoryNotice] = useState<ActionNotice | null>(null);
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  useEffect(() => { setQuoteItem(null); }, [view, roomId]);
  const pending = budgetQuoteJobs(project, 'pending', tier);
  const roomLabel = roomId === unassignedRoomId ? unassignedLabel : project.zones.find(({ id }) => id === roomId)?.labelEs;

  function chooseTier(next: BudgetTier) {
    setTier(next);
    if (next !== tier) {
      setInventoryNotice(null);
      setBulkActivity({});
    }
    try { localStorage.setItem(tierKey, next); } catch { /* The current tab still keeps the selection. */ }
  }

  async function editItem(itemId: string, changes: { qty: number } | { zoneId: string }) {
    setSavingItemId(itemId);
    try {
      const updated = await api<Pick<CatalogItem, 'qty' | 'zoneId'>>(`/api/projects/${project.id}/items/${encodeURIComponent(itemId)}`, {
        method: 'PATCH', headers: { 'content-type': 'application/json' }, body: JSON.stringify(changes),
      });
      setProject((current) => ({ ...current, items: current.items.map((item) => item.id === itemId ? { ...item, ...updated } : item) }));
    } finally {
      setSavingItemId(null);
    }
  }

  function storeSearch(event: QuoteActivity) {
    if (!event.search) return;
    setProject((current) => ({ ...current, items: current.items.map((item) => item.id === event.itemId
      ? { ...item, lastSearch: { ...item.lastSearch, [event.tier]: event.search } } : item) }));
  }

  async function openQuote(item: CatalogItem, productUrl?: string) {
    setInventoryNotice(null);
    setUpdateActivity(null);
    if (!productUrl && !quotesForTier(item, tier).length) {
      setQuoteItem(item);
      return;
    }

    setUpdating(true);
    setUpdateActivity({ itemId: item.id, tier, itemName: item.name, stage: 'queued', detail: 'Iniciando la búsqueda en tiendas.' });
    try {
      const { project: updated, quote, alternatives = [] } = await streamPost<QuoteActivity, {
        project: FurnishingProject;
        quote: CatalogItem['quotes'][number];
        alternatives?: CatalogItem['quotes'];
      }>(`/api/projects/${project.id}/items/${item.id}/quote`, { tier, ...(productUrl ? { productUrl } : {}) }, (event) => { storeSearch(event); setUpdateActivity(event); });
      setProject(updated);
      const saved = alternatives.length === 1 ? ' Se guardó 1 alternativa.'
        : alternatives.length > 1 ? ` Se guardaron ${alternatives.length} alternativas.` : '';
      const detail = `Precio verificado en ${quote.storeName}.${saved}`;
      setUpdateActivity({ itemId: item.id, tier, itemName: item.name, stage: 'completed', detail });
    } catch (error) {
      const detail = error instanceof ApiError && [409, 422].includes(error.status) && error.message ? error.message
        : 'La actualización se interrumpió. Inténtalo de nuevo.';
      setUpdateActivity({ itemId: item.id, tier, itemName: item.name, stage: 'failed', detail });
    } finally {
      setUpdating(false);
    }
  }

  async function quoteBatch(action: BulkScope, roomItems?: CatalogItem[], zoneId?: string | null) {
    const scope = action === 'all' ? 'all' : 'pending';
    setInventoryNotice(null);
    setUpdateActivity(null);
    setBulkScope(action);
    setBulkActivity(Object.fromEntries(budgetQuoteJobs(roomItems ? { ...project, items: roomItems } : project, scope, tier).map(({ item, tier: jobTier }) => [`${item.id}:${jobTier}`, {
      itemId: item.id, tier: jobTier, itemName: item.name, stage: 'queued', detail: 'En espera de cotización.',
    } satisfies QuoteActivity])));
    try {
      const result = await streamPost<QuoteActivity, { project: FurnishingProject; saved: BudgetQuote[]; failed: BudgetQuote[] }>(
        `/api/projects/${project.id}/quote-pending`, { scope, zoneId, tier },
        (event) => setBulkActivity((current) => ({ ...current, [`${event.itemId}:${event.tier}`]: event })),
      );
      setProject(result.project);
      const saved = result.saved.length === 1 ? 'Se guardó 1 cotización.' : `Se guardaron ${result.saved.length} cotizaciones.`;
      const missing = scope === 'all'
        ? `No se pudieron actualizar ${result.failed.length === 1 ? '1 cotización' : `${result.failed.length} cotizaciones`}. Se conservaron los precios anteriores cuando estaban disponibles.`
        : result.failed.length === 1 ? 'Queda 1 cotización pendiente.' : `Quedan ${result.failed.length} cotizaciones pendientes.`;
      setInventoryNotice(result.failed.length
        ? { tone: 'error', text: result.saved.length ? `${saved} ${missing}` : missing }
        : { tone: 'success', text: saved });
    } catch {
      setBulkActivity((current) => Object.fromEntries(Object.entries(current).map(([id, job]) => [id,
        job.stage === 'completed' || job.stage === 'failed' ? job : { ...job, stage: 'failed', detail: 'La actualización de este artículo se interrumpió.' },
      ])));
      setInventoryNotice({ tone: 'error', text: 'La cotización se interrumpió. Inténtalo de nuevo.' });
    } finally {
      setBulkScope(null);
    }
  }

  return (
    <Workspace {...account} project={project} view={view} roomLabel={roomLabel} exportTier={view === 'inventario' ? tier : undefined}>
      {view !== 'inventario' && <div className="page-heading">
        <h1 tabIndex={-1}>{view === 'resumen' ? project.name : pageTitles[view]}</h1>
        {view === 'resumen' ? <p>{project.address && <><MapPin size={16} aria-hidden="true" /> {project.address}</>}</p> : (
          view === 'referencias' && <p>Administra los planos y renders para detectar artículos que faltan por cotizar.</p>
        )}
      </div>}
      {view === 'resumen' && <>
        <div className="atlas-overview">
          <ProjectAtlas project={project} rooms={projectRooms(project)} />
          <BudgetSettings project={project} locked={bulkScope !== null || updating || quoteItem !== null} onChoose={(next) => {
            chooseTier(next);
            navigate(inventoryPath(project.id));
          }} />
        </div>
        <section className="panel overview-panel" aria-labelledby="overview-title">
          <div className="panel-header"><h2 id="overview-title">Explorar el proyecto</h2></div>
          <div className="overview-links">
            {overviewLinks.map(({ view: section, title, description, icon: Icon }) => <Link key={section} href={projectPath(project.id, section)}>
              <span className="state-icon"><Icon size={20} aria-hidden="true" /></span>
              <span><strong>{title}</strong><small>{description}</small></span>
              <ArrowRight size={18} aria-hidden="true" />
            </Link>)}
          </div>
        </section>
      </>}
      {view === 'airbnb' && <AirbnbReview projectId={project.id} />}
      <div hidden={view !== 'agentes'}><AgentReview projectId={project.id} active={view === 'agentes'} /></div>
      <div hidden={view !== 'inventario'}><Inventory
        project={project} roomId={roomId} tier={tier} onTierChange={chooseTier} active={view === 'inventario'} pendingCount={pending.length}
        saving={bulkScope !== null || updating || quoteItem !== null || savingItemId !== null} bulkScope={bulkScope}
        savingItemId={savingItemId} onSaveQuantity={(itemId, qty) => editItem(itemId, { qty })} onEnterQuoteUrl={setQuoteItem}
        onMoveItem={(itemId, zoneId) => editItem(itemId, { zoneId })}
        onItemMoved={(item, zoneId) => {
          const label = project.zones.find(({ id }) => id === zoneId)?.labelEs ?? unassignedLabel;
          setInventoryNotice({ tone: 'success', text: `Se movió ${item.name} a ${label}.` });
        }}
        onItemAdded={(updated, item) => {
          setProject(updated);
          setInventoryNotice({ tone: 'success', text: `${item.name} se agregó a los artículos pendientes.` });
        }}
        onItemSplit={(updated, item) => {
          setProject(updated);
          setInventoryNotice({ tone: 'success', text: `Se desglosó ${item.name} en artículos por cotizar.` });
        }}
        activity={Object.keys(bulkActivity).length > 0 && <QuoteProgress jobs={Object.values(bulkActivity)} busy={bulkScope !== null} onDismiss={() => setBulkActivity({})} />}
        notice={inventoryNotice} onOpenQuote={openQuote} onBulkQuote={quoteBatch} updateActivity={updateActivity}
      /></div>
      <div hidden={view !== 'referencias'}><References project={project} active={view === 'referencias'} onProjectChange={setProject} /></div>
      {quoteItem && <QuoteDialog projectId={project.id} item={quoteItem} tier={tier} onSearch={storeSearch} onDismiss={() => setQuoteItem(null)} onSuccess={(updatedProject, message) => {
        setProject(updatedProject);
        setInventoryNotice({ tone: 'success', text: message });
        setQuoteItem(null);
        requestAnimationFrame(() => {
          if (document.activeElement === document.body) document.getElementById('inventory-title')?.focus({ preventScroll: true });
        });
      }} />}
    </Workspace>
  );
}
