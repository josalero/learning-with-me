import { ArrowRight } from 'lucide-react';
import { budgetProfiles } from '@shared/budgetProfiles';
import { projectStats } from '@shared/projectStats';
import { BUDGET_TIERS, type BudgetTier, type FurnishingProject } from '@shared/types';
import { money } from './ui';

export default function BudgetSettings({ project, locked, onChoose }: {
  project: FurnishingProject;
  locked: boolean;
  onChoose: (tier: BudgetTier) => void;
}) {
  return <section className="panel budget-settings" aria-labelledby="budget-title">
    <div className="panel-header">
      <div><h2 id="budget-title">Presupuestos del proyecto</h2><p>Cada escenario tiene sus propios artículos cotizados y total.</p></div>
    </div>
    <div className="budget-profile-options">
      {BUDGET_TIERS.map((tier) => {
        const { label, description } = budgetProfiles[tier];
        const { quotedCount, itemCount, totalCrc } = projectStats(project.items, tier);
        return <article className="budget-profile" key={tier}>
          <h3>{label}</h3>
          <p>{description}</p>
          <div className="budget-profile-total">
            <strong>{quotedCount ? money.format(totalCrc) : 'Sin cotizar'}</strong>
            <p>{quotedCount > 0 && quotedCount < itemCount && 'Total parcial · '}{quotedCount} de {itemCount} artículos cotizados</p>
            {itemCount > 0 && <progress value={quotedCount} max={itemCount} aria-label={`Cobertura de ${label}`} />}
          </div>
          <button type="button" className="button button-secondary button-small" disabled={locked} onClick={() => onChoose(tier)}
            aria-label={`Ver y cotizar ${label.toLowerCase()}`}>Ver y cotizar <ArrowRight size={16} aria-hidden="true" /></button>
        </article>;
      })}
    </div>
  </section>;
}
