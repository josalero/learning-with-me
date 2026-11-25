import { useEffect, useState } from 'react';
import { ArrowRight, Boxes, Images, ListChecks, MapPin } from 'lucide-react';
import { budgetQuoteJobs, primaryBudgetTier, quotesForTier, selectedBudgetTiers } from '@shared/budgetProfiles';
import type { BudgetQuote, BudgetTier, CatalogItem, FurnishingProject, QuoteActivity } from '@shared/types';
import AgentReview from './AgentReview';
import BudgetSettings from './BudgetSettings';
import Inventory, { QuoteDialog } from './Inventory';
import { Link, projectPath, type ProjectView } from './navigation';
import References from './References';
import { QuoteProgress } from './QuoteProgress';
import { streamPost } from './streamApi';
import { ApiError } from './ui';
import { Workspace, type Account } from './Workspace';

const pageTitles = { resumen: 'Resumen', agentes: 'Agentes', inventario: 'Inventario', referencias: 'Referencias' };
const overviewLinks = [
  { view: 'agentes', title: 'Actividad de los agentes', description: 'Ejecuta la revisión y aprueba sus etapas.', icon: ListChecks },
  { view: 'inventario', title: 'Inventario por ambiente', description: 'Consulta, agrega cotizaciones y revisa pendientes.', icon: Boxes },
  { view: 'referencias', title: 'Referencias y análisis visual', description: 'Sube imágenes y detecta artículos adicionales.', icon: Images },
] as const;

type ActionNotice = { text: string; tone: 'error' | 'success' };

export default function Dashboard({ initialProject, view, ...account }: Account & { initialProject: FurnishingProject; view: ProjectView }) {
  const [project, setProject] = useState(initialProject);
  const [tierPreference, setTierPreference] = useState<BudgetTier>(() => primaryBudgetTier(initialProject));
  const [savingProfiles, setSavingProfiles] = useState(false);
  const [quoteItem, setQuoteItem] = useState<CatalogItem | null>(null);
  const [updating, setUpdating] = useState(false);
  const [updateActivity, setUpdateActivity] = useState<QuoteActivity | null>(null);
  const [bulkScope, setBulkScope] = useState<'pending' | 'all' | null>(null);
  const [bulkActivity, setBulkActivity] = useState<Record<string, QuoteActivity>>({});
  const [inventoryNotice, setInventoryNotice] = useState<ActionNotice | null>(null);
  useEffect(() => { if (view !== 'inventario') setQuoteItem(null); }, [view]);
  const tiers = selectedBudgetTiers(project);
  const tier = tiers.includes(tierPreference) ? tierPreference : primaryBudgetTier(project);
  const pending = budgetQuoteJobs(project);

  async function openQuote(item: CatalogItem) {
    setInventoryNotice(null);
    setUpdateActivity(null);
    if (!quotesForTier(item, tier).length) {
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
      }>(`/api/projects/${project.id}/items/${item.id}/quote`, { tier }, setUpdateActivity);
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

  async function quoteBatch(scope: 'pending' | 'all') {
    setInventoryNotice(null);
    setUpdateActivity(null);
    setBulkScope(scope);
    setBulkActivity(Object.fromEntries(budgetQuoteJobs(project, scope).map(({ item, tier: jobTier }) => [`${item.id}:${jobTier}`, {
      itemId: item.id, tier: jobTier, itemName: item.name, stage: 'queued', detail: 'En espera de cotización.',
    } satisfies QuoteActivity])));
    try {
      const result = await streamPost<QuoteActivity, { project: FurnishingProject; saved: BudgetQuote[]; failed: BudgetQuote[] }>(
        `/api/projects/${project.id}/quote-pending`, { scope },
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
    <Workspace {...account} project={project} view={view}>
      <div className="page-heading">
        <h1 tabIndex={-1}>{view === 'resumen' ? project.name : pageTitles[view]}</h1>
        {view === 'resumen' ? <p>{project.address && <><MapPin size={16} aria-hidden="true" /> {project.address}</>}</p> : (
          view === 'referencias' && <p>Administra los planos y renders para detectar artículos que faltan por cotizar.</p>
        )}
      </div>
      {view === 'resumen' && <>
        <BudgetSettings project={project} locked={bulkScope !== null || updating || savingProfiles} onSaving={setSavingProfiles} onSaved={setProject} />
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
      <div hidden={view !== 'agentes'}><AgentReview projectId={project.id} active={view === 'agentes'} /></div>
      <div hidden={view !== 'inventario'}><Inventory
        project={project} tier={tier} tiers={tiers} onTierChange={setTierPreference} active={view === 'inventario'} pendingCount={pending.length}
        saving={bulkScope !== null || updating || savingProfiles || quoteItem !== null} bulkScope={bulkScope}
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
      {quoteItem && <QuoteDialog projectId={project.id} item={quoteItem} tier={tier} onDismiss={() => setQuoteItem(null)} onSuccess={(updatedProject, message) => {
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
