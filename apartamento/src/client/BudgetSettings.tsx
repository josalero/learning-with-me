import { ArrowRight } from 'lucide-react';
import { budgetProfiles, quotesForTier } from '@shared/budgetProfiles';
import { brandLabel } from '@shared/brands';
import { projectStats } from '@shared/projectStats';
import { BUDGET_TIERS, type BudgetTier, type CatalogItem, type FurnishingProject } from '@shared/types';
import { money } from './ui';

export function BudgetCriteria({ tier, items }: { tier: BudgetTier; items: CatalogItem[] }) {
  const { quality, brands } = budgetProfiles[tier];
  const quotedBrands = [...new Set(items.flatMap((item) => {
    const [quote] = quotesForTier(item, tier);
    const brand = brandLabel(quote);
    return brand === 'Por identificar' || brand === 'Genérico' ? [] : [brand];
  }))].sort((a, b) => a.localeCompare(b, 'es-CR'));
  return <dl className="budget-criteria">
    <div><dt>Calidad</dt><dd>{quality}</dd></div>
    <div><dt>Marcas y líneas</dt><dd>{brands}</dd></div>
    {quotedBrands.length > 0 && <div><dt>Marcas en cotizaciones</dt><dd>
      {quotedBrands.slice(0, 4).join(', ')}{quotedBrands.length > 4 && ` y ${quotedBrands.length - 4} más`}
    </dd></div>}
  </dl>;
}

export default function BudgetSettings({ project, locked, onChoose }: {
  project: FurnishingProject;
  locked: boolean;
  onChoose: (tier: BudgetTier) => void;
}) {
  return <section className="panel budget-settings" aria-labelledby="budget-title">
    <div className="panel-header">
      <div><h2 id="budget-title">Presupuestos del proyecto</h2><p>Compará los totales, la cobertura y los criterios de compra de cada gama.</p></div>
    </div>
    <div className="budget-profile-options">
      {BUDGET_TIERS.map((tier) => {
        const { label, description } = budgetProfiles[tier];
        const { quotedCount, itemCount, totalCrc } = projectStats(project.items, tier);
        return <article className="budget-profile" key={tier}>
          <h3>{label}</h3>
          <div className="budget-profile-total">
            <strong>{quotedCount ? money.format(totalCrc) : 'Sin cotizar'}</strong>
            <p>{quotedCount > 0 && quotedCount < itemCount && 'Total parcial · '}{quotedCount} de {itemCount} artículos cotizados</p>
            {itemCount > 0 && <progress value={quotedCount} max={itemCount} aria-label={`Cobertura de ${label}`} />}
          </div>
          <p>{description}</p>
          <BudgetCriteria tier={tier} items={project.items} />
          <button type="button" className="button button-secondary button-small" disabled={locked} onClick={() => onChoose(tier)}
            aria-label={`Ver y cotizar ${label.toLowerCase()}`}>Ver y cotizar <ArrowRight size={16} aria-hidden="true" /></button>
        </article>;
      })}
    </div>
  </section>;
}
