import ExcelJS from 'exceljs';
import { describe, expect, it } from 'vitest';
import { buildNatarundiProject } from '../src/domain/natarundi-catalog.js';
import { applyDemoQuotes } from '../src/domain/demo-quotes.js';
import { buildBudgetWorkbook } from '../src/server/services/excelExport.js';
import { projectStats } from '../src/shared/projectStats.js';
import type { FurnishingProject } from '../src/shared/types.js';

async function openBudget(project: FurnishingProject) {
  const data = await buildBudgetWorkbook(project);
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(new Uint8Array(data).buffer);
  return workbook;
}

describe('Excel budget export', () => {
  it('reconciles the project total and keeps alternative quotes without counting them twice', async () => {
    const project = applyDemoQuotes(buildNatarundiProject('workspace', 'project'));
    const first = project.items[0]!;
    first.quotes.push({ ...first.quotes[0]!, id: 'alternative', priceCrc: 891_000 });
    const stats = projectStats(project.items);
    const workbook = await openBudget(project);
    const summary = workbook.getWorksheet('Resumen')!;
    const detail = workbook.getWorksheet('Ítems')!;
    const pending = workbook.getWorksheet('Sin cotizar')!;
    expect(summary.getCell('A8').result).toBe(stats.totalCrc);
    expect(summary.getCell('E8').result).toBe(stats.quotedCount);
    expect(summary.getCell('G8').result).toBe(stats.itemCount - stats.quotedCount);
    expect(detail.getCell('D6').value).toBe('Cotizado');
    expect(detail.getCell('D7').value).toBe('Alternativa');
    expect(detail.getCell('E6').value).toBe('Catálogo de referencia');
    expect(detail.getCell('G6').result).toBe(first.quotes[0]!.priceCrc * first.qty);
    expect(detail.getCell('G7').value).toBeNull();
    expect(detail.getCell('I6').hyperlink).toBe(first.quotes[0]?.productUrl);
    expect(detail.getCell('I6').text).toBe('Ver opciones');
    expect(pending.rowCount).toBe(5 + stats.itemCount - stats.quotedCount);
  });

  it('reports missing exchange rates without a divide-by-zero formula in an empty workbook', async () => {
    const project = { ...buildNatarundiProject('workspace', 'project'), items: [], zones: [], usdCrcRate: 0 };
    const workbook = await openBudget(project);
    const summary = workbook.getWorksheet('Resumen')!;
    expect(summary.getCell('A8').result).toBe(0);
    expect(summary.getCell('C8').result).toBe('Sin tipo de cambio');
    expect(workbook.getWorksheet('Sin cotizar')?.getCell('A6').value).toBe('El proyecto aún no tiene artículos.');
  });
});
