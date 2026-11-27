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
    const itemRow = detail.getRows(6, detail.rowCount)!.find((row) => row.getCell(2).value === first.name)!.number;
    expect(summary.getCell('A8').result).toBe(stats.totalCrc);
    expect(summary.getCell('E8').result).toBe(stats.quotedCount);
    expect(summary.getCell('G8').result).toBe(stats.itemCount - stats.quotedCount);
    expect(detail.getCell(`D${itemRow}`).value).toBe('Cotizado');
    expect(detail.getCell(`D${itemRow + 1}`).value).toBe('Alternativa');
    expect(detail.getCell(`E${itemRow}`).value).toBe('Catálogo de referencia');
    expect(detail.getCell(`G${itemRow}`).result).toBe(first.quotes[0]!.priceCrc * first.qty);
    expect(detail.getCell(`G${itemRow + 1}`).value).toBeNull();
    expect(detail.getCell(`I${itemRow}`).text).toBe('Sin producto elegido');
    expect(pending.rowCount).toBe(5 + stats.itemCount - stats.quotedCount);
  });

  it('keeps totals and direct links separate for each budget tier and groups bedrooms together', async () => {
    const project = applyDemoQuotes(buildNatarundiProject('workspace', 'project'));
    project.budgetTiers = ['balanced', 'premium'];
    const item = project.items.find(({ id }) => id === 'i-master-bed')!;
    item.quantityByTier = { premium: 2 };
    item.specificationsByTier = { premium: ['Material: madera sólida.', 'Tamaño: queen.'] };
    const direct = 'https://tienda.example.cr/cama-queen-500';
    item.quotes.push({ ...item.quotes[0]!, id: 'premium', basis: 'published', priceCrc: 505_000, tier: 'premium', productUrl: direct,
      review: { rating: 4.8, count: 90, sourceUrl: 'https://resenas.example.cr/cama-500' } });
    const workbook = await openBudget(project);
    const comparison = workbook.getWorksheet('Comparación')!;
    const detail = workbook.getWorksheet('Ítems')!;
    const row = detail.getRows(6, detail.rowCount)!.find((entry) => entry.getCell(9).hyperlink === direct)!;
    expect(comparison.getCell('B6').result).toBe(projectStats(project.items).totalCrc);
    expect(comparison.getCell('B7').result).toBe(1_010_000);
    expect(comparison.getCell('C7').result).toBe(1);
    expect(row.getCell(1).text).toContain('Cuartos · Dormitorio principal');
    expect(row.getCell(16).text).toBe('Gama alta');
    expect(row.getCell(3).value).toBe(2);
    expect(row.getCell(15).text).toContain('Material: madera sólida.');
    expect(row.getCell(18).hyperlink).toBe('https://resenas.example.cr/cama-500');
  });

  it('reports missing exchange rates without a divide-by-zero formula in an empty workbook', async () => {
    const project = { ...buildNatarundiProject('workspace', 'project'), items: [], zones: [], usdCrcRate: 0 };
    const workbook = await openBudget(project);
    const summary = workbook.getWorksheet('Resumen')!;
    expect(summary.getCell('A8').result).toBe(0);
    expect(summary.getCell('C8').result).toBe('Sin tipo de cambio');
    expect(workbook.getWorksheet('Sin cotizar')?.getCell('A6').value).toBe('El proyecto aún no tiene artículos.');
  });

  it('exports the updated quantity for a legacy item named with its original count', async () => {
    const project = applyDemoQuotes(buildNatarundiProject('workspace', 'project'));
    const stools = project.items.find(({ id }) => id === 'i-island-stools')!;
    stools.qty = 5;
    const detail = (await openBudget(project)).getWorksheet('Ítems')!;
    const row = detail.getRows(6, detail.rowCount)!.find((entry) => entry.getCell(2).value === 'Taburetes isla')!;
    expect(row.getCell(3).value).toBe(5);
    expect(row.getCell(7).result).toBe(stools.quotes[0]!.priceCrc * 5);
  });
});
