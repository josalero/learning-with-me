import { Annotation, END, START, StateGraph } from '@langchain/langgraph';
import type { FurnishingProject, HitlGate, ReviewActivity, ReviewResult, ReviewStep } from '../../shared/types.js';

export const GraphState = Annotation.Root({
  project: Annotation<FurnishingProject>,
  stage: Annotation<ReviewResult['stage']>,
  notes: Annotation<string[]>,
  gate: Annotation<HitlGate | undefined>,
  approvedGates: Annotation<HitlGate[]>,
});

export type GraphStateType = typeof GraphState.State;
type StepResult = Pick<GraphStateType, 'stage' | 'notes'> & Partial<Pick<GraphStateType, 'gate'>>;

const crc = new Intl.NumberFormat('es-CR', { style: 'currency', currency: 'CRC', maximumFractionDigits: 0 });
const usd = new Intl.NumberFormat('es-CR', { style: 'currency', currency: 'USD' });

function orchestrator(state: GraphStateType): StepResult {
  return {
    stage: 'orchestrator',
    notes: [...state.notes, `Inventario cargado. ${state.project.items.length} artículos en ${state.project.zones.length} zonas.`],
  };
}

function visualSpace(state: GraphStateType): StepResult {
  const approved = state.approvedGates.includes('zone_layout');
  const assigned = new Set(state.project.zones.map((zone) => zone.id));
  const unassigned = state.project.items.filter((item) => !assigned.has(item.zoneId)).length;
  const summary = unassigned ? `${unassigned} artículos sin zona reconocida.` : `${assigned.size} zonas con distribución disponible.`;
  return {
    stage: 'visual_space',
    gate: approved ? undefined : 'zone_layout',
    notes: [...state.notes, approved ? `Distribución aprobada. ${summary}` : summary],
  };
}

function reviewSources(state: GraphStateType): StepResult {
  const sources = new Set(state.project.items.flatMap(({ quotes }) => quotes[0] ? [quotes[0].storeName] : []));
  const summary = !sources.size ? 'Aún no hay fuentes de precios en el inventario.'
    : `${sources.size} ${sources.size === 1 ? 'fuente registrada' : 'fuentes registradas'}. ${[...sources].slice(0, 3).join(', ')}${sources.size > 3 ? ' y otras.' : '.'}`;
  return { stage: 'market_scrape', notes: [...state.notes, summary] };
}

function qualityCritic(state: GraphStateType): StepResult {
  const pending = state.project.items.filter((item) => !item.quotes.length).length;
  const summary = !pending ? 'Todos los artículos tienen un precio registrado.'
    : `${pending} ${pending === 1 ? 'artículo sigue' : 'artículos siguen'} pendiente${pending === 1 ? '' : 's'} de cotizar.`;
  return { stage: 'quality_critic', notes: [...state.notes, summary] };
}

function budgetQuoter(state: GraphStateType): StepResult {
  const approved = state.approvedGates.includes('final_budget');
  const total = state.project.items.reduce((sum, item) => sum + (item.quotes[0]?.priceCrc ?? 0) * item.qty, 0);
  const conversion = state.project.usdCrcRate > 0 ? ` · ${usd.format(total / state.project.usdCrcRate)}` : '';
  return {
    stage: approved ? 'completed' : 'budget_quoter',
    gate: approved ? undefined : 'final_budget',
    notes: [...state.notes, `${approved ? 'Presupuesto aprobado' : 'Total con precios registrados'} (${crc.format(total)}${conversion}).`],
  };
}

export function buildFurnishingGraph(onActivity?: (activity: ReviewActivity) => void) {
  function track(step: ReviewStep, detail: string, run: (state: GraphStateType) => StepResult | Promise<StepResult>) {
    return async (state: GraphStateType): Promise<StepResult> => {
      onActivity?.({ step, status: 'running', detail });
      try {
        const result = await run(state);
        onActivity?.({ step, status: result.gate ? 'waiting' : 'completed', detail: result.notes.at(-1) ?? detail });
        return result;
      } catch (error) {
        onActivity?.({ step, status: 'failed', detail: 'Esta etapa encontró un error.' });
        throw error;
      }
    };
  }

  return new StateGraph(GraphState)
    .addNode('orchestrator', track('orchestrator', 'Cargando el inventario del proyecto.', orchestrator))
    .addNode('visual_space', track('visual_space', 'Comprobando la distribución del inventario por zona.', visualSpace))
    .addNode('market_scrape', track('market_scrape', 'Identificando las fuentes de los precios guardados.', reviewSources))
    .addNode('quality_critic', track('quality_critic', 'Detectando artículos sin cotizar.', qualityCritic))
    .addNode('budget_quoter', track('budget_quoter', 'Calculando el total con las cantidades de cada artículo.', budgetQuoter))
    .addConditionalEdges(START, (state) => {
      if (state.gate === 'zone_layout') return 'visual_space';
      if (state.gate === 'final_budget') return 'budget_quoter';
      return 'orchestrator';
    }, ['visual_space', 'budget_quoter', 'orchestrator'])
    .addEdge('orchestrator', 'visual_space')
    .addConditionalEdges('visual_space', (state) => state.gate ? END : 'market_scrape', [END, 'market_scrape'])
    .addEdge('market_scrape', 'quality_critic')
    .addEdge('quality_critic', 'budget_quoter')
    .addEdge('budget_quoter', END)
    .compile();
}

export function initialGraphState(project: FurnishingProject): GraphStateType {
  return { project, stage: 'idle', notes: [], gate: undefined, approvedGates: [] };
}
