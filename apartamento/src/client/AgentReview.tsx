import { useEffect, useState } from 'react';
import { ArrowRight, Calculator, Check, CircleAlert, ClipboardCheck, Clock3, ListChecks, Map, Play, Store, Workflow } from 'lucide-react';
import type { ReviewActivity, ReviewSnapshot, ReviewStep } from '@shared/types';
import ReviewHistory from './ReviewHistory';
import { streamPost } from './streamApi';
import { Link, projectPath } from './navigation';
import { api, ApiError, Notice, Spinner } from './ui';

const stages = [
  { id: 'orchestrator', title: 'Preparación', description: 'Carga el inventario.', icon: Workflow },
  { id: 'visual_space', title: 'Distribución', description: 'Revisa las zonas del apartamento.', icon: Map },
  { id: 'market_scrape', title: 'Fuentes de precios', description: 'Identifica el origen de los precios guardados.', icon: Store },
  { id: 'quality_critic', title: 'Control de calidad', description: 'Detecta artículos sin precio.', icon: ClipboardCheck },
  { id: 'budget_quoter', title: 'Presupuesto', description: 'Consolida los importes.', icon: Calculator },
] satisfies { id: ReviewStep; title: string; description: string; icon: typeof Workflow }[];

const stageTitles = Object.fromEntries(stages.map(({ id, title }) => [id, title])) as Record<ReviewStep, string>;

const statusLabels = { ready: 'Sin iniciar', running: 'En curso', waiting: 'Por aprobar', completed: 'Completada', failed: 'Con error' };

type ActivityMap = Partial<Record<ReviewStep, ReviewActivity>>;

function byStep(activity: ReviewActivity[]): ActivityMap {
  return Object.fromEntries(activity.map((event) => [event.step, event]));
}

export default function AgentReview({ projectId, active }: { projectId: string; active: boolean }) {
  const [workflow, setWorkflow] = useState<ReviewSnapshot | null>(null);
  const [activity, setActivity] = useState<ActivityMap>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [lastEvent, setLastEvent] = useState<string>();
  const [historyVersion, setHistoryVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    api<ReviewSnapshot | null>(`/api/projects/${projectId}/review`, { signal: controller.signal })
      .then((result) => {
        if (!result || controller.signal.aborted) return;
        setWorkflow(result);
        setActivity(byStep(result.activity));
        if (result.failed) setError('La ejecución anterior se interrumpió. Puedes volver a intentarlo.');
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('No se pudo cargar la última revisión.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [projectId]);

  async function run() {
    if (busy || loading) return;
    const gate = workflow?.pendingGate;
    setBusy(true);
    setError('');
    setLastEvent('Conectando con los agentes…');
    if (!gate) setActivity({});
    try {
      const result = await streamPost<ReviewActivity, ReviewSnapshot>(
        gate ? '/api/workflow/approve' : '/api/workflow/start',
        gate ? { projectId, gate, threadId: workflow.threadId } : { projectId },
        (event) => {
          setActivity((current) => ({ ...current, [event.step]: event }));
          const stage = stages.find(({ id }) => id === event.step);
          setLastEvent(`${stage?.title ?? 'Revisión'}. ${statusLabels[event.status]}. ${event.detail}`);
        },
      );
      setWorkflow(result);
      setActivity(byStep(result.activity));
      setLastEvent(result.stage === 'completed' ? 'Revisión completada.' : 'La revisión está en pausa y requiere tu aprobación.');
    } catch (cause) {
      setActivity((current) => Object.fromEntries(Object.entries(current).map(([step, event]) => [step, event.status === 'running'
        ? { ...event, status: 'failed', detail: 'La ejecución se interrumpió. Puedes volver a intentarlo.' } : event])));
      if (gate && cause instanceof ApiError && cause.status === 404) {
        setWorkflow(null);
        setError('La revisión anterior ya no está disponible. Ejecuta los agentes de nuevo.');
      } else {
        setError(gate ? 'No se pudo confirmar la aprobación. Inténtalo de nuevo.' : 'La revisión no terminó. Inténtalo de nuevo.');
      }
      setLastEvent(undefined);
    } finally {
      setBusy(false);
      setHistoryVersion((version) => version + 1);
    }
  }

  const gate = workflow?.pendingGate;
  const completed = workflow?.stage === 'completed' && !error;
  const action = gate === 'zone_layout' ? 'Aprobar distribución' : gate === 'final_budget' ? 'Aprobar presupuesto' : completed ? 'Volver a ejecutar' : 'Ejecutar agentes';

  return (
    <div className="agent-review-page">
      <section id="revision" className="panel agent-panel" aria-labelledby="agent-title">
        <div className="panel-header agent-header">
          <div className="agent-title">
            <span className="stat-icon"><ListChecks size={20} aria-hidden="true" /></span>
            <div>
              <h2 id="agent-title">Actividad de los agentes</h2>
              <p>{loading ? 'Cargando la última revisión…' : busy ? 'Revisión en curso' : error ? 'Revisión interrumpida' : gate ? 'En pausa · requiere tu aprobación' : completed ? 'Revisión completada' : 'Revisión del equipamiento y el presupuesto'}</p>
            </div>
          </div>
          <button className={`button ${completed && !busy ? 'button-secondary' : 'button-primary'}`} type="button" onClick={run} aria-disabled={busy || loading || undefined}>
            {busy ? <><Spinner /> Ejecutando…</> : gate ? <>{action} <ArrowRight size={16} aria-hidden="true" /></> : <><Play size={15} aria-hidden="true" /> {action}</>}
          </button>
        </div>
        <div className="sr-only" role="status">{lastEvent}</div>
        <ol className="agent-flow" role="list" aria-label="Etapas de los agentes">
          {stages.map(({ id, title, description, icon: Icon }) => {
            const event = activity[id];
            const status = event?.status ?? 'ready';
            const StatusIcon = status === 'waiting' ? Clock3 : status === 'completed' ? Check : status === 'failed' ? CircleAlert : null;
            return (
              <li className="agent-step" key={id} data-status={status} aria-current={status === 'running' || status === 'waiting' ? 'step' : undefined}>
                <div className="agent-step-top">
                  <span className="agent-step-icon"><Icon size={18} aria-hidden="true" /></span>
                  <span className="agent-step-status">{status === 'running' ? <Spinner /> : StatusIcon && <StatusIcon size={14} aria-hidden="true" />} {statusLabels[status]}</span>
                </div>
                <strong>{title}</strong>
                <p>{event?.detail ?? description}</p>
              </li>
            );
          })}
        </ol>
        {error && <div className="agent-footer"><Notice>{error}</Notice></div>}
        {gate && !busy && (
          <div className="agent-footer agent-approval">
            <div>
              <strong>{gate === 'zone_layout' ? 'Confirma la distribución para continuar' : 'Confirma el monto de la revisión'}</strong>
              <Link href={projectPath(projectId, gate === 'zone_layout' ? 'referencias' : 'resumen')}>{gate === 'zone_layout' ? 'Ver plano y referencias' : 'Ver total presupuestado'} <ArrowRight size={14} aria-hidden="true" /></Link>
            </div>
          </div>
        )}
      </section>
      <ReviewHistory projectId={projectId} active={active} version={historyVersion} titles={stageTitles} />
    </div>
  );
}
