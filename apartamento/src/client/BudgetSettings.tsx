import { useState, type FormEvent } from 'react';
import { budgetProfiles, selectedBudgetTiers } from '@shared/budgetProfiles';
import { projectStats } from '@shared/projectStats';
import { BUDGET_TIERS, type BudgetTier, type FurnishingProject } from '@shared/types';
import { api, money, Notice, Spinner } from './ui';

export default function BudgetSettings({ project, locked, onSaving, onSaved }: {
  project: FurnishingProject;
  locked: boolean;
  onSaving: (saving: boolean) => void;
  onSaved: (project: FurnishingProject) => void;
}) {
  const selected = selectedBudgetTiers(project);
  const [draft, setDraft] = useState<BudgetTier[]>(() => selected);
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved' | 'failed'>('idle');
  const changed = draft.length !== selected.length || draft.some((tier) => !selected.includes(tier));

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked || !draft.length || !changed) return;
    setStatus('saving');
    onSaving(true);
    try {
      const updated = await api<FurnishingProject>(`/api/projects/${project.id}/budget-tiers`, {
        method: 'PATCH',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ tiers: draft }),
      });
      onSaved(updated);
      setStatus('saved');
    } catch {
      setStatus('failed');
    } finally {
      onSaving(false);
    }
  }

  return <section className="panel budget-settings" aria-labelledby="budget-title">
    <div className="panel-header">
      <div><h2 id="budget-title">Presupuestos del proyecto</h2><p>Selecciona uno o varios niveles para cotizar y comparar.</p></div>
    </div>
    <form onSubmit={save}>
      <fieldset disabled={locked || status === 'saving'}>
        <legend className="sr-only">Niveles de presupuesto</legend>
        <div className="budget-profile-options">
          {BUDGET_TIERS.map((tier) => {
            const { label, description } = budgetProfiles[tier];
            return <div className="budget-profile" key={tier}>
              <label className="budget-profile-option">
                <input type="checkbox" checked={draft.includes(tier)} aria-describedby={`budget-${tier}-description`} onChange={(event) => {
                  const checked = event.target.checked;
                  setDraft((tiers) => checked ? [...tiers, tier] : tiers.filter((entry) => entry !== tier));
                  setStatus('idle');
                }} />
                <strong>{label}</strong>
              </label>
              <p id={`budget-${tier}-description`}>{description}</p>
              {selected.includes(tier) && <BudgetTotal project={project} tier={tier} />}
            </div>;
          })}
        </div>
      </fieldset>
      <div className="budget-actions">
        <button type="submit" className="button button-primary" disabled={locked || !draft.length || !changed || status === 'saving'}>
          {status === 'saving' ? <><Spinner /> Guardando…</> : 'Guardar niveles'}
        </button>
        {!draft.length && <p role="status">Selecciona al menos un nivel.</p>}
        {status === 'saved' && <p role="status">Niveles guardados.</p>}
      </div>
      {status === 'failed' && <Notice>No se pudieron guardar los niveles. Inténtalo de nuevo.</Notice>}
    </form>
  </section>;
}

function BudgetTotal({ project, tier }: { project: FurnishingProject; tier: BudgetTier }) {
  const { quotedCount, itemCount, totalCrc } = projectStats(project.items, tier);
  const label = budgetProfiles[tier].label;
  return <div className="budget-profile-total">
    <strong>{quotedCount ? money.format(totalCrc) : 'Sin cotizar'}</strong>
    <p>{quotedCount} de {itemCount} artículos cotizados</p>
    {itemCount > 0 && <progress value={quotedCount} max={itemCount} aria-label={`Cobertura de ${label}`} />}
  </div>;
}
