import { useEffect, useState } from 'react';
import { ArrowRight, Boxes, CircleCheck, Clock3, Images, ListChecks, MapPin, Wallet } from 'lucide-react';
import { projectStats } from '@shared/projectStats';
import type { CatalogItem, FurnishingProject, QuoteActivity } from '@shared/types';
import AgentReview from './AgentReview';
import Inventory, { QuoteDialog } from './Inventory';
import { Link, projectPath, type ProjectView } from './navigation';
import References from './References';
import { QuoteProgress } from './QuoteProgress';
import { streamPost } from './streamApi';
import { ApiError, money } from './ui';
import { Workspace, type Account } from './Workspace';

const pageTitles = { resumen: 'Resumen', agentes: 'Agentes', inventario: 'Inventario', referencias: 'Referencias' };
const overviewLinks = [
  { view: 'agentes', title: 'Actividad de los agentes', description: 'Ejecuta la revisión y aprueba sus etapas.', icon: ListChecks },
  { view: 'inventario', title: 'Inventario por zona', description: 'Consulta, agrega cotizaciones y revisa pendientes.', icon: Boxes },
  { view: 'referencias', title: 'Referencias y análisis visual', description: 'Sube imágenes y detecta artículos adicionales.', icon: Images },
] as const;

type ActionNotice = { text: string; tone: 'error' | 'success' };

export default function Dashboard({ initialProject, view, ...account }: Account & { initialProject: FurnishingProject; view: ProjectView }) {
  const [project, setProject] = useState(initialProject);
  const [quoteItem, setQuoteItem] = useState<CatalogItem | null>(null);
  const [updating, setUpdating] = useState(false);
  const [updateActivity, setUpdateActivity] = useState<QuoteActivity | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [bulkActivity, setBulkActivity] = useState<Record<string, QuoteActivity>>({});
  const [inventoryNotice, setInventoryNotice] = useState<ActionNotice | null>(null);
  useEffect(() => { if (view !== 'inventario') setQuoteItem(null); }, [view]);
  const { itemCount, quotedCount, totalCrc } = projectStats(project.items);
  const pendingCount = itemCount - quotedCount;
  const coverage = itemCount ? Math.round(100 * quotedCount / itemCount) : 0;

  async function openQuote(item: CatalogItem) {
    setInventoryNotice(null);
    setUpdateActivity(null);
    if (!item.quotes.length) {
      setQuoteItem(item);
      return;
    }

    setUpdating(true);
    setUpdateActivity({ itemId: item.id, itemName: item.name, stage: 'queued', detail: 'Iniciando la búsqueda en tiendas.' });
    try {
      const { project: updated, quote, alternatives = [] } = await streamPost<QuoteActivity, {
        project: FurnishingProject;
        quote: CatalogItem['quotes'][number];
        alternatives?: CatalogItem['quotes'];
      }>(`/api/projects/${project.id}/items/${item.id}/quote`, {}, setUpdateActivity);
      setProject(updated);
      const saved = alternatives.length === 1 ? ' Se guardó 1 alternativa.'
        : alternatives.length > 1 ? ` Se guardaron ${alternatives.length} alternativas.` : '';
      const detail = `Precio verificado en ${quote.storeName}.${saved}`;
      setUpdateActivity({ itemId: item.id, itemName: item.name, stage: 'completed', detail });
    } catch (error) {
      const detail = error instanceof ApiError && error.status === 422 && error.message ? error.message
        : 'La actualización se interrumpió. Inténtalo de nuevo.';
      setUpdateActivity({ itemId: item.id, itemName: item.name, stage: 'failed', detail });
    } finally {
      setUpdating(false);
    }
  }

  async function quotePending() {
    setInventoryNotice(null);
    setUpdateActivity(null);
    setBulkBusy(true);
    setBulkActivity(Object.fromEntries(project.items.filter((item) => !item.quotes.length).map((item) => [item.id, {
      itemId: item.id, itemName: item.name, stage: 'queued', detail: 'En espera de cotización.',
    } satisfies QuoteActivity])));
    try {
      const result = await streamPost<QuoteActivity, { project: FurnishingProject; quoted: string[]; failed: { itemId: string; reason: string }[] }>(
        `/api/projects/${project.id}/quote-pending`, {},
        (event) => setBulkActivity((current) => ({ ...current, [event.itemId]: event })),
      );
      setProject(result.project);
      const saved = result.quoted.length === 1 ? 'Se guardó 1 cotización.' : `Se guardaron ${result.quoted.length} cotizaciones.`;
      const missing = result.failed.length === 1 ? '1 artículo sigue pendiente.' : `${result.failed.length} artículos siguen pendientes.`;
      setInventoryNotice(result.failed.length
        ? { tone: 'error', text: result.quoted.length ? `${saved} ${missing}` : `No se encontraron precios. ${missing}` }
        : { tone: 'success', text: saved });
    } catch {
      setBulkActivity((current) => Object.fromEntries(Object.entries(current).map(([id, job]) => [id,
        job.stage === 'completed' || job.stage === 'failed' ? job : { ...job, stage: 'failed', detail: 'La actualización de este artículo se interrumpió.' },
      ])));
      setInventoryNotice({ tone: 'error', text: 'No se pudieron cotizar los artículos pendientes. Inténtalo de nuevo.' });
    } finally {
      setBulkBusy(false);
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
        <section className="stats" aria-label="Indicadores del presupuesto">
          <div className="stat-card stat-primary">
            <span className="stat-icon"><Wallet size={20} aria-hidden="true" /></span>
            <div className="stat-content">
              <h2>Total presupuestado</h2><p className="stat-value">{money.format(totalCrc)}</p>
              <p className="stat-detail">{account.session.isDemo ? 'Incluye precios de referencia' : 'En colones costarricenses'}</p>
            </div>
          </div>
          <div className="stat-card">
            <span className="stat-icon"><CircleCheck size={20} aria-hidden="true" /></span>
            <div className="stat-content">
              <h2>Con cotización</h2><p className="stat-value">{quotedCount} <span>de {itemCount}</span></p>
              {itemCount > 0 && <div className="coverage"><progress value={quotedCount} max={itemCount} aria-label="Cobertura de cotizaciones" /><span>{coverage}%</span></div>}
            </div>
          </div>
          <div className="stat-card">
            <span className="stat-icon stat-icon-amber"><Clock3 size={20} aria-hidden="true" /></span>
            <div className="stat-content">
              <h2>Por cotizar</h2><p className="stat-value">{pendingCount} <span>{pendingCount === 1 ? 'artículo' : 'artículos'}</span></p>
              <p className="stat-detail">{pendingCount ? 'Pendientes en el inventario' : 'Inventario al día'}</p>
            </div>
          </div>
        </section>
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
        project={project} active={view === 'inventario'} pendingCount={pendingCount} saving={bulkBusy || updating || quoteItem !== null} bulkBusy={bulkBusy}
        onItemAdded={(updated, item) => {
          setProject(updated);
          setInventoryNotice({ tone: 'success', text: `${item.name} se agregó a los artículos pendientes.` });
        }}
        activity={Object.keys(bulkActivity).length > 0 && <QuoteProgress jobs={Object.values(bulkActivity)} busy={bulkBusy} onDismiss={() => setBulkActivity({})} />}
        notice={inventoryNotice} onOpenQuote={openQuote} onBulkQuote={quotePending} updateActivity={updateActivity}
      /></div>
      <div hidden={view !== 'referencias'}><References project={project} active={view === 'referencias'} onProjectChange={setProject} /></div>
      {quoteItem && <QuoteDialog projectId={project.id} item={quoteItem} onDismiss={() => setQuoteItem(null)} onSuccess={(updatedProject, message) => {
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
