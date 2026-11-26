import { Check, CircleAlert, Clock3, Search, Sparkles } from 'lucide-react';
import { quoteLink } from '@shared/quoteBasis';
import { budgetProfiles } from '@shared/budgetProfiles';
import type { QuoteActivity } from '@shared/types';

const labels: Record<QuoteActivity['stage'], string> = {
  queued: 'En espera', searching: 'Buscando con IA', verifying: 'Verificando ofertas',
  catalog: 'Catálogo de referencia', saving: 'Guardando', completed: 'Completado', failed: 'Sin cotización nueva',
};

export function QuoteJob({ activity, single = false, showTier = false }: { activity: QuoteActivity; single?: boolean; showTier?: boolean }) {
  const { stage } = activity;
  const offer = activity.search?.offers[0];
  const link = offer && quoteLink(offer);
  const active = stage === 'searching' || stage === 'verifying' || stage === 'catalog' || stage === 'saving';
  const Icon = stage === 'completed' ? Check : stage === 'failed' ? CircleAlert : stage === 'queued' ? Clock3 : stage === 'searching' ? Sparkles : Search;
  return (
    <div className="quote-job" data-stage={stage} data-active={active || undefined}>
      <span className="quote-orbit"><Icon size={17} aria-hidden="true" /></span>
      <div>
        {!single && <strong>{activity.itemName}{showTier && ` · ${budgetProfiles[activity.tier].label}`}</strong>}
        <span className="quote-stage">{labels[stage]}</span>
        <p>{activity.detail}</p>
        {offer && link && <a className="text-action" href={link.url} target="_blank" rel="noreferrer">Abrir ficha en {offer.storeName}</a>}
      </div>
    </div>
  );
}

export function QuoteProgress({ jobs, busy, onDismiss }: { jobs: QuoteActivity[]; busy: boolean; onDismiss: () => void }) {
  const processed = jobs.filter(({ stage }) => stage === 'saving' || stage === 'completed' || stage === 'failed').length;
  const showTier = new Set(jobs.map(({ tier }) => tier)).size > 1;
  return (
    <section className="quote-progress" aria-labelledby="quote-progress-title">
      <div className="quote-progress-header">
        <div>
          <h3 id="quote-progress-title">{busy ? 'Cotización en curso' : 'Actividad de cotización'}</h3>
          <p role="status">{processed} de {jobs.length} cotizaciones procesadas</p>
        </div>
        {!busy && <button type="button" className="button button-small button-secondary" onClick={onDismiss}>Cerrar actividad</button>}
      </div>
      <progress value={processed} max={jobs.length} aria-label="Cotizaciones procesadas" />
      <ul role="list">{jobs.map((job) => <li key={`${job.itemId}:${job.tier}`}><QuoteJob activity={job} showTier={showTier} /></li>)}</ul>
    </section>
  );
}
