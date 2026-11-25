import { useEffect, useState } from 'react';
import { Check, ChevronDown, CircleAlert, Clock3, History } from 'lucide-react';
import type { HitlGate, ReviewHistoryPage, ReviewSnapshot, ReviewStep } from '@shared/types';
import { api, Notice, Spinner } from './ui';

const dateTime = new Intl.DateTimeFormat('es-CR', { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'America/Costa_Rica' });
const eventTime = new Intl.DateTimeFormat('es-CR', { timeStyle: 'medium', timeZone: 'America/Costa_Rica' });
const approvals: Record<HitlGate, string> = { zone_layout: 'Distribución aprobada', final_budget: 'Presupuesto aprobado' };
const stepStatus = { running: 'En curso', completed: 'Completada', waiting: 'Por aprobar', failed: 'Con error' };

function runStatus(run: ReviewSnapshot) {
  if (run.failed) return { label: 'Interrumpida', tone: 'error', icon: CircleAlert };
  if (run.stage === 'completed') return { label: 'Completada', tone: 'success', icon: Check };
  if (run.pendingGate) return { label: 'Pendiente de aprobación', tone: 'pending', icon: Clock3 };
  return { label: 'Guardada', tone: 'neutral', icon: History };
}

export default function ReviewHistory({ projectId, active, version, titles }: {
  projectId: string;
  active: boolean;
  version: number;
  titles: Record<ReviewStep, string>;
}) {
  const [page, setPage] = useState<ReviewHistoryPage>();
  const [failed, setFailed] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [retry, setRetry] = useState(0);
  const endpoint = `/api/projects/${projectId}/reviews`;

  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setFailed(false);
    api<ReviewHistoryPage>(endpoint, { signal: controller.signal }).then((result) => {
      if (!controller.signal.aborted) setPage(result);
    }).catch(() => {
      if (!controller.signal.aborted) setFailed(true);
    });
    return () => controller.abort();
  }, [active, endpoint, retry, version]);

  async function loadMore() {
    if (!page?.nextCursor || loadingMore) return;
    const cursor = page.nextCursor;
    setLoadingMore(true);
    setFailed(false);
    try {
      const { runs, nextCursor } = await api<ReviewHistoryPage>(`${endpoint}?before=${encodeURIComponent(cursor)}`);
      setPage((current) => current?.nextCursor === cursor
        ? { runs: [...new Map([...current.runs, ...runs].map((run) => [run.threadId, run])).values()], nextCursor }
        : current);
    } catch {
      setFailed(true);
    } finally {
      setLoadingMore(false);
    }
  }

  return <section className="panel review-history" aria-labelledby="review-history-title">
    <div className="panel-header">
      <div className="agent-title">
        <span className="state-icon"><History size={20} aria-hidden="true" /></span>
        <div><h2 id="review-history-title">Historial de ejecuciones</h2><p>Revisiones y aprobaciones guardadas de este proyecto.</p></div>
      </div>
    </div>
    <div className="review-history-body">
      {failed && <div className="button-row"><Notice>No se pudo cargar el historial.</Notice><button type="button" className="button button-secondary" onClick={() => setRetry((value) => value + 1)}>Reintentar</button></div>}
      {!page && !failed && <p className="page-loading" role="status"><Spinner /> Cargando historial…</p>}
      {page?.runs.length === 0 && <p className="history-empty">Aún no hay ejecuciones guardadas.</p>}
      {page && page.runs.length > 0 && <ol className="history-runs" role="list">
        {page.runs.map((run) => {
          const status = runStatus(run);
          return <li key={run.threadId}>
            <details>
              <summary>
                <span className="history-date"><strong><time dateTime={run.updatedAt}>{dateTime.format(new Date(run.updatedAt))}</time></strong><small>Última actividad</small></span>
                <span className="history-status" data-tone={status.tone}><status.icon size={15} aria-hidden="true" />{status.label}</span>
                <ChevronDown size={18} className="history-chevron" aria-hidden="true" />
              </summary>
              <div className="history-detail">
                {run.startedAt && <p className="history-empty">Inicio de la revisión el <time dateTime={run.startedAt}>{dateTime.format(new Date(run.startedAt))}</time></p>}
                {run.approvedGates.length > 0 && <p className="history-approvals">{run.approvedGates.map((gate) => <span key={gate}><Check size={15} aria-hidden="true" />{approvals[gate]}</span>)}</p>}
                {run.pendingGate && <p className="history-awaiting">{run.pendingGate === 'zone_layout' ? 'Pendiente de aprobar la distribución.' : 'Pendiente de aprobar el presupuesto.'}</p>}
                {run.events.length > 0 ? <ol className="history-events" role="list">
                  {run.events.map((event, index) => <li key={`${event.at}-${index}`}>
                    <time dateTime={event.at}>{eventTime.format(new Date(event.at))}</time>
                    <div>{event.type === 'approval' ? <strong>{approvals[event.gate]}</strong> : <><strong>{titles[event.step]} <span>{stepStatus[event.status]}</span></strong><p>{event.detail}</p></>}</div>
                  </li>)}
                </ol> : run.activity.length > 0 ? <ol className="history-events" role="list">
                  {run.activity.map((event) => <li className="history-legacy-step" key={event.step}><div><strong>{titles[event.step]} <span>{stepStatus[event.status]}</span></strong><p>{event.detail}</p></div></li>)}
                </ol> : <p className="history-empty">Esta revisión se guardó sin detalle de etapas.</p>}
              </div>
            </details>
          </li>;
        })}
      </ol>}
      {page?.nextCursor && <button className="button button-secondary" type="button" disabled={loadingMore} onClick={loadMore}>{loadingMore ? <><Spinner /> Cargando…</> : 'Ver ejecuciones anteriores'}</button>}
    </div>
  </section>;
}
