import { describe, expect, it } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { buildFurnishingGraph, initialGraphState } from '../src/server/agents/furnishingGraph.js';
import type { ReviewActivity } from '../src/shared/types.js';

describe('furnishing graph HITL', () => {
  it('completes after both gates approved', async () => {
    const project = buildNatarundiProject('ws-test', 'proj-test');
    const graph = buildFurnishingGraph();
    const state = initialGraphState(project);
    state.approvedGates = ['zone_layout', 'final_budget'];
    const result = await graph.invoke(state, { configurable: { thread_id: 'test-thread' } });
    expect(result.stage).toBe('completed');
    expect(result.notes.at(-1)).toContain('Presupuesto aprobado');
  });

  it('reports real activity and resumes from each approval without rerunning finished steps', async () => {
    const project = buildNatarundiProject('ws-test', 'proj-test');
    const activity: ReviewActivity[] = [];
    const graph = buildFurnishingGraph((event) => activity.push(event));

    const initial = await graph.invoke(initialGraphState(project));
    expect(initial.gate).toBe('zone_layout');
    expect(activity.map(({ step, status }) => [step, status])).toEqual([
      ['orchestrator', 'running'], ['orchestrator', 'completed'],
      ['visual_space', 'running'], ['visual_space', 'waiting'],
    ]);

    activity.length = 0;
    const budget = await graph.invoke({ ...initial, approvedGates: ['zone_layout'] });
    expect(budget.gate).toBe('final_budget');
    expect(activity.filter(({ status }) => status === 'running').map(({ step }) => step)).toEqual([
      'visual_space', 'market_scrape', 'quality_critic', 'budget_quoter',
    ]);
    expect(activity.at(-1)).toMatchObject({ step: 'budget_quoter', status: 'waiting' });

    activity.length = 0;
    const complete = await graph.invoke({ ...budget, approvedGates: ['zone_layout', 'final_budget'] });
    expect(complete.stage).toBe('completed');
    expect(complete.gate).toBeUndefined();
    expect(activity.map(({ step, status }) => [step, status])).toEqual([
      ['budget_quoter', 'running'], ['budget_quoter', 'completed'],
    ]);
  });
});
