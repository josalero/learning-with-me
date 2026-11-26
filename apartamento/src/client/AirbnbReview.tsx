import { useEffect, useState } from 'react';
import { ArrowUpRight, ChevronDown } from 'lucide-react';
import type { AirbnbAssessment, AirbnbCheck } from '@shared/types';
import { Link, projectPath } from './navigation';
import { api, Notice, referenceImage, Spinner } from './ui';

const statuses = { discrepancy: 'Diferencia por resolver', documented: 'Visible en referencias', pending: 'Por comprobar' };
export default function AirbnbReview({ projectId }: { projectId: string }) {
  const [report, setReport] = useState<AirbnbAssessment>();
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setFailed(false);
    api<AirbnbAssessment>(`/api/projects/${projectId}/airbnb`, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setReport(data); })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [projectId, retry]);
  if (failed) return <div className="panel page-state"><Notice>No se pudo cargar la revisión.</Notice>
    <button className="button button-secondary" onClick={() => setRetry((n) => n + 1)}>Reintentar</button></div>;
  if (!report) return <p className="page-loading" role="status"><Spinner /> Revisando referencias e inventario…</p>;
  function row(check: AirbnbCheck) {
    return <details key={check.id} className="audit-check" open={check.status === 'discrepancy' || undefined}>
      <summary><span><strong>{check.title}</strong><small>{check.policy}</small></span>
        <span className="audit-status" data-status={check.status}>{statuses[check.status]}</span><ChevronDown size={16} aria-hidden="true" /></summary>
      <div className="audit-body">
        {check.evidence.map((entry, index) => <p key={index}>{entry.text}{entry.reference && <> <a className="text-action" target="_blank" rel="noreferrer"
          href={referenceImage(projectId, entry.reference.id)}>{entry.reference.title} <ArrowUpRight size={13} aria-hidden="true" /></a></>}</p>)}
        <p>{check.action}</p>
        <a className="text-action" href={check.sourceUrl} target="_blank" rel="noreferrer">{check.id === 'registration' ? 'Consultar el ICT' : 'Consultar el criterio de Airbnb'} <ArrowUpRight size={13} aria-hidden="true" /></a>
      </div>
    </details>;
  }
  return <div className="audit-page">
    <section className="panel" aria-labelledby="audit-layout"><div className="panel-header">
      <div><h2 id="audit-layout">Lo que muestran las referencias</h2><p>Comprueba la distribución real antes de fijar la capacidad del anuncio.</p></div>
      <Link className="button button-secondary" href={projectPath(projectId, 'referencias')}>Ver planos y fotos</Link>
    </div>{report.checks.filter((c) => c.policy === 'Anuncio y espacio').map(row)}</section>
    <section className="panel" aria-labelledby="audit-visit"><div className="panel-header"><div><h2 id="audit-visit">Antes de recibir huéspedes</h2>
      <p>Contrasta el presupuesto con el equipamiento instalado y las condiciones de operación.</p></div></div>
      {report.checks.filter((c) => c.policy !== 'Anuncio y espacio').map(row)}</section>
    <p className="audit-date">Criterios consultados el {new Intl.DateTimeFormat('es-CR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(report.policyDate))}.</p>
  </div>;
}
