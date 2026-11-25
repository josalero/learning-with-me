import ExcelJS from 'exceljs';
import { projectStats } from '../../shared/projectStats.js';
import { quoteBasisLabel, quoteLink } from '../../shared/quoteBasis.js';
import type { CatalogItem, FurnishingProject, LineItemQuote } from '../../shared/types.js';

const colors = {
  ink: 'FF202139', muted: 'FF545A72', purple: 'FF463296', purpleSoft: 'FFEFEBFB',
  blue: 'FF254889', line: 'FFDADFEA', stripe: 'FFF6F7FC', white: 'FFFFFFFF',
  success: 'FF235842', successSoft: 'FFEAF5EE', amber: 'FF79501E', amberSoft: 'FFFFF2D9',
};
const crc = '"₡" #,##0;[Red]("₡" #,##0);"₡" 0';
const usd = '"US$" #,##0.00;[Red]("US$" #,##0.00);"US$" 0.00';
const percent = '0%';
const fontName = 'Arial';
const detailHeaderRow = 5;
const detailFirstRow = detailHeaderRow + 1;
const conversionCell = 'G11';
const exportDate = new Intl.DateTimeFormat('es-CR', { dateStyle: 'medium', timeZone: 'America/Costa_Rica' });
const localDate = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'America/Costa_Rica' });

const regionNames: Record<FurnishingProject['region'], string> = { guanacaste: 'Guanacaste', limon: 'Limón', 'san-jose': 'San José', alajuela: 'Alajuela' };
const sources: Record<LineItemQuote['source'], string> = {
  mister_precios: 'MisterPrecios', ahorra_ya: 'AhorraYa', store_direct: 'Tienda directa', manual: 'Manual', agent_estimate: 'Estimación',
};
const availability: Record<LineItemQuote['availability'], string> = { in_stock: 'Disponible', out_of_stock: 'Agotado', unknown: 'Sin confirmar' };
const categoryNames: Record<string, string> = { electrodomesticos: 'Electrodomésticos', climatizacion: 'Climatización', electronica: 'Electrónica', logistica: 'Logística' };

type CellSpan = [string, string?];
type ZoneGroup = { label: string; items: CatalogItem[]; firstRow?: number; lastRow?: number };

function fill(color: string): ExcelJS.Fill {
  return { type: 'pattern', pattern: 'solid', fgColor: { argb: color } };
}

function cell(sheet: ExcelJS.Worksheet, [start, end]: CellSpan, value: ExcelJS.CellValue, options: {
  font?: Partial<ExcelJS.Font>;
  alignment?: Partial<ExcelJS.Alignment>;
  background?: string;
  numFmt?: string;
} = {}) {
  if (end) sheet.mergeCells(`${start}:${end}`);
  const target = sheet.getCell(start);
  target.value = value;
  target.font = { name: fontName, size: 10, color: { argb: colors.ink }, ...options.font };
  target.alignment = { vertical: 'middle', ...options.alignment };
  if (options.background) target.fill = fill(options.background);
  if (options.numFmt) target.numFmt = options.numFmt;
  return target;
}

function addSheet(workbook: ExcelJS.Workbook, name: string, widths: number[], options: { frozen?: { xSplit: number; ySplit: number }; tab?: string } = {}) {
  const sheet = workbook.addWorksheet(name, {
    properties: options.tab ? { tabColor: { argb: options.tab } } : undefined,
    views: options.frozen ? [{ state: 'frozen', ...options.frozen, showGridLines: false }] : [{ showGridLines: false }],
    pageSetup: { orientation: 'landscape', paperSize: 9, fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  });
  sheet.columns = widths.map((width) => ({ width }));
  return sheet;
}

function heading(sheet: ExcelJS.Worksheet, title: string, project: string, endColumn: string) {
  sheet.getRow(1).height = 10;
  cell(sheet, ['A2', `${endColumn}2`], title, { font: { size: 17, bold: true, color: { argb: colors.purple } } });
  sheet.getRow(2).height = 27;
  cell(sheet, ['A3', `${endColumn}3`], project, { font: { size: 11, bold: true }, alignment: { wrapText: true } });
  sheet.getRow(3).height = 25;
  for (let column = 1; column <= sheet.columns.length; column++) {
    sheet.getRow(3).getCell(column).border = { bottom: { style: 'thin', color: { argb: colors.line } } };
  }
}

function tableHeader(sheet: ExcelJS.Worksheet, row: number, labels: string[]) {
  labels.forEach((label, index) => {
    const column = sheet.getColumn(index + 1).letter;
    cell(sheet, [`${column}${row}`], label, {
      background: colors.purple, font: { bold: true, color: { argb: colors.white } },
      alignment: { horizontal: 'center', wrapText: true, vertical: 'middle' },
    });
  });
  sheet.getRow(row).height = 25;
}

function styleRow(row: ExcelJS.Row, columns: number, striped: boolean) {
  for (let index = 1; index <= columns; index++) {
    const target = row.getCell(index);
    target.font = { name: fontName, size: 10, color: { argb: colors.ink } };
    target.alignment = { horizontal: 'left', vertical: 'middle', wrapText: true, indent: 1 };
    if (striped) target.fill = fill(colors.stripe);
  }
}

function styleStatus(target: ExcelJS.Cell, tone: 'success' | 'pending' | 'info') {
  const [background, foreground] = tone === 'success' ? [colors.successSoft, colors.success]
    : tone === 'pending' ? [colors.amberSoft, colors.amber] : [colors.purpleSoft, colors.purple];
  target.fill = fill(background);
  target.font = { name: fontName, size: 10, bold: true, color: { argb: foreground } };
}

function fittedHeight(...fields: [string, number][]) {
  const lines = Math.max(1, ...fields.map(([value, width]) => value.split('\n')
    .reduce((count, line) => count + Math.max(1, Math.ceil(line.length / width)), 0)));
  return Math.min(200, Math.max(23, lines * 13 + 8));
}

function zoneGroups(project: FurnishingProject): ZoneGroup[] {
  const groups = new Map(project.zones.map((zone) => [zone.id, { label: zone.labelEs, items: [] } as ZoneGroup]));
  for (const item of project.items) {
    const key = groups.has(item.zoneId) ? item.zoneId : '';
    let group = groups.get(key);
    if (!group) {
      group = { label: 'Sin zona asignada', items: [] };
      groups.set(key, group);
    }
    group.items.push(item);
  }
  return [...groups.values()];
}

function storeLink(quote: LineItemQuote): ExcelJS.CellValue {
  const link = quoteLink(quote);
  return link ? { text: link.label, hyperlink: link.url, tooltip: link.url } : quote.productUrl || null;
}

function priceDate(capturedAt: string) {
  const date = new Date(capturedAt);
  if (!Number.isFinite(date.getTime())) return null;
  const parts = Object.fromEntries(localDate.formatToParts(date).map(({ type, value }) => [type, value]));
  return new Date(`${parts.year}-${parts.month}-${parts.day}T00:00:00.000Z`);
}

function addDetailRows(sheet: ExcelJS.Worksheet, groups: ZoneGroup[], exchangeRate: number | null) {
  for (const group of groups) {
    if (!group.items.length) continue;
    group.firstRow = sheet.rowCount + 1;
    for (const item of group.items) {
      const label = categoryNames[item.category] ?? item.category;
      const rows = item.quotes.length ? item.quotes : [undefined];
      for (const [index, quote] of rows.entries()) {
        const isBudgetQuote = quote && index === 0;
        const state = !quote ? 'Pendiente' : isBudgetQuote ? 'Cotizado' : 'Alternativa';
        const basis = quote ? quoteBasisLabel(quote) ?? 'Sin clasificación' : null;
        const notes = [item.notes, !quote ? item.unquotedReason ?? 'Por buscar' : undefined].filter(Boolean).join('\n');
        const date = quote ? priceDate(quote.capturedAt) : null;
        const row = sheet.addRow([
          group.label, item.name, item.qty, state, basis, quote?.priceCrc ?? null, null,
          quote?.storeName ?? null, quote ? storeLink(quote) : null, null,
          quote ? availability[quote.availability] : null, label, quote ? sources[quote.source] : null, date, notes || null,
        ]);
        row.height = fittedHeight([group.label, 21], [item.name, 36], [basis ?? '', 19], [quote?.storeName ?? '', 19], [notes, 45]);
        styleRow(row, 15, row.number % 2 === 0);
        styleStatus(row.getCell(4), !quote ? 'pending' : isBudgetQuote ? 'success' : 'info');
        if (quote?.basis === 'catalog' || quote?.basis === 'estimate' || quote?.source === 'agent_estimate') styleStatus(row.getCell(5), 'info');
        for (const index of [3, 6, 7, 10, 14]) row.getCell(index).alignment = { horizontal: 'right', vertical: 'middle', indent: 1 };
        for (const index of [6, 7]) row.getCell(index).numFmt = crc;
        row.getCell(10).numFmt = usd;
        row.getCell(14).numFmt = 'dd/mm/yyyy';
        if (!quote) continue;
        row.getCell(9).font = { name: fontName, size: 10, color: { argb: colors.blue }, underline: true };
        if (isBudgetQuote) {
          row.getCell(7).value = { formula: `C${row.number}*F${row.number}`, result: item.qty * quote.priceCrc };
          row.getCell(7).font = { name: fontName, size: 10, bold: true, color: { argb: colors.ink } };
        }
        row.getCell(10).value = quote.priceUsd ?? {
          formula: `IF(Resumen!$G$11>0,F${row.number}/Resumen!$G$11,"")`,
          result: exchangeRate ? quote.priceCrc / exchangeRate : '',
        };
      }
    }
    group.lastRow = sheet.rowCount;
  }
}

function addSummary(sheet: ExcelJS.Worksheet, project: FurnishingProject, groups: ZoneGroup[], exchangeRate: number | null) {
  const { itemCount, quotedCount, totalCrc } = projectStats(project.items);
  const zoneStart = 16;
  const totalRow = zoneStart + Math.max(1, groups.length);
  heading(sheet, 'Presupuesto de equipamiento', project.name, 'H');
  const region = regionNames[project.region];
  const address = region && !project.address.toLocaleLowerCase('es-CR').includes(region.toLocaleLowerCase('es-CR'))
    ? [project.address, region].filter(Boolean).join(', ') : project.address;
  cell(sheet, ['A4', 'F4'], address, { font: { italic: true, color: { argb: colors.muted } }, alignment: { wrapText: true } });
  cell(sheet, ['G4', 'H4'], `Exportado el ${exportDate.format(new Date())}`, { font: { italic: true, color: { argb: colors.muted } }, alignment: { horizontal: 'right', indent: 1, wrapText: true } });
  sheet.getRow(4).height = 23;
  const metrics: { col: [string, string]; label: string; value: ExcelJS.CellValue; color: string; numFmt?: string }[] = [
    { col: ['A', 'B'], label: 'Presupuesto en CRC', value: { formula: `F${totalRow}`, result: totalCrc }, color: colors.purpleSoft, numFmt: crc },
    { col: ['C', 'D'], label: 'Equivalente en USD', value: { formula: `IF(${conversionCell}>0,F${totalRow}/${conversionCell},"Sin tipo de cambio")`, result: exchangeRate ? totalCrc / exchangeRate : 'Sin tipo de cambio' }, color: colors.stripe, numFmt: usd },
    { col: ['E', 'F'], label: 'Artículos cotizados', value: { formula: `D${totalRow}`, result: quotedCount }, color: colors.successSoft },
    { col: ['G', 'H'], label: 'Artículos pendientes', value: { formula: `E${totalRow}`, result: itemCount - quotedCount }, color: colors.amberSoft },
  ];
  for (const { col: [start, end], label, value, color, numFmt } of metrics) {
    cell(sheet, [`${start}7`, `${end}7`], label, { background: color, font: { bold: true }, alignment: { horizontal: 'left', indent: 1 } });
    cell(sheet, [`${start}8`, `${end}9`], value, { background: color, numFmt, font: { size: 16, bold: true }, alignment: { horizontal: 'left', indent: 1 } });
  }
  sheet.getRow(7).height = 20;
  sheet.getRow(8).height = 19;
  sheet.getRow(9).height = 15;
  cell(sheet, ['A11', 'B11'], 'Cobertura de cotización', { font: { bold: true } });
  cell(sheet, ['C11', 'D11'], { formula: `IFERROR(D${totalRow}/C${totalRow},0)`, result: itemCount ? quotedCount / itemCount : 0 }, { numFmt: percent, alignment: { horizontal: 'left' } });
  cell(sheet, ['E11', 'F11'], 'Tipo de cambio CRC por USD', { font: { bold: true }, alignment: { horizontal: 'left', indent: 1 } });
  cell(sheet, ['G11', 'H11'], exchangeRate, { background: colors.purpleSoft, numFmt: '#,##0.00', font: { color: { argb: colors.blue } }, alignment: { horizontal: 'right', indent: 1 } });
  sheet.getRow(11).height = 24;
  cell(sheet, ['A12', 'H12'], 'El total usa la primera cotización de cada artículo. Las alternativas y el origen de cada precio se pueden consultar en Ítems.', {
    font: { italic: true, color: { argb: colors.muted } }, alignment: { wrapText: true },
  });
  sheet.getRow(12).height = 25;
  cell(sheet, ['A14', 'H14'], 'Presupuesto por zona', { font: { size: 13, bold: true, color: { argb: colors.purple } } });
  sheet.getRow(14).height = 24;
  for (const [span, label] of [
    [['A15', 'B15'], 'Zona'], [['C15'], 'Artículos'], [['D15'], 'Cotizados'], [['E15'], 'Pendientes'],
    [['F15', 'G15'], 'Subtotal CRC'], [['H15'], '% del total'],
  ] as [CellSpan, string][]) {
    cell(sheet, span, label, { background: colors.purple, font: { bold: true, color: { argb: colors.white } }, alignment: { horizontal: 'center' } });
  }
  sheet.getRow(15).height = 24;
  for (const [index, group] of groups.entries()) {
    const number = zoneStart + index;
    const stats = projectStats(group.items);
    const pending = stats.itemCount - stats.quotedCount;
    const { firstRow, lastRow } = group;
    const row = sheet.getRow(number);
    styleRow(row, 8, index % 2 === 0);
    row.height = fittedHeight([group.label, 34]);
    const background = index % 2 === 0 ? colors.stripe : undefined;
    cell(sheet, [`A${number}`, `B${number}`], group.label, { background, alignment: { wrapText: true } });
    row.getCell(3).value = { formula: `D${number}+E${number}`, result: stats.itemCount };
    row.getCell(4).value = firstRow ? { formula: `COUNTIF('Ítems'!D${firstRow}:D${lastRow},"Cotizado")`, result: stats.quotedCount } : 0;
    row.getCell(5).value = firstRow ? { formula: `COUNTIF('Ítems'!D${firstRow}:D${lastRow},"Pendiente")`, result: pending } : 0;
    for (const column of [3, 4, 5, 8]) row.getCell(column).alignment = { horizontal: 'right', vertical: 'middle', indent: 1 };
    if (pending) styleStatus(row.getCell(5), 'pending');
    cell(sheet, [`F${number}`, `G${number}`], firstRow ? { formula: `SUM('Ítems'!G${firstRow}:G${lastRow})`, result: stats.totalCrc } : 0, {
      background, numFmt: crc, alignment: { horizontal: 'right', indent: 1 },
    });
    row.getCell(8).value = { formula: `IFERROR(F${number}/$F$${totalRow},0)`, result: totalCrc ? stats.totalCrc / totalCrc : 0 };
    row.getCell(8).numFmt = percent;
  }
  if (!groups.length) cell(sheet, ['A16', 'H16'], 'El proyecto aún no tiene zonas ni artículos.', { font: { italic: true, color: { argb: colors.muted } } });
  const totals = sheet.getRow(totalRow);
  styleRow(totals, 8, false);
  totals.height = 26;
  for (let index = 1; index <= 8; index++) {
    totals.getCell(index).fill = fill(colors.purpleSoft);
    totals.getCell(index).font = { name: fontName, size: 10, bold: true, color: { argb: colors.ink } };
  }
  cell(sheet, [`A${totalRow}`, `B${totalRow}`], 'Total del proyecto', { background: colors.purpleSoft, font: { bold: true } });
  for (const [column, result] of [['C', itemCount], ['D', quotedCount], ['E', itemCount - quotedCount]] as const) {
    sheet.getCell(`${column}${totalRow}`).value = { formula: `SUM(${column}${zoneStart}:${column}${totalRow - 1})`, result };
  }
  cell(sheet, [`F${totalRow}`, `G${totalRow}`], { formula: `SUM(F${zoneStart}:F${totalRow - 1})`, result: totalCrc }, {
    background: colors.purpleSoft, numFmt: crc, font: { bold: true }, alignment: { horizontal: 'right', indent: 1 },
  });
  totals.getCell(8).value = { formula: `SUM(H${zoneStart}:H${totalRow - 1})`, result: totalCrc ? 1 : 0 };
  for (const column of [3, 4, 5, 8]) totals.getCell(column).alignment = { horizontal: 'right', vertical: 'middle', indent: 1 };
  totals.getCell(8).numFmt = percent;
  sheet.pageSetup.printArea = `A1:H${totalRow}`;
}

export async function buildBudgetWorkbook(project: FurnishingProject): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'Natarundi';
  workbook.calcProperties.fullCalcOnLoad = true;
  const groups = zoneGroups(project);
  const exchangeRate = Number.isFinite(project.usdCrcRate) && project.usdCrcRate > 0 ? project.usdCrcRate : null;
  const summary = addSheet(workbook, 'Resumen', [19, 17, 15, 14, 15, 17, 14, 14], { tab: colors.purple });
  const detail = addSheet(workbook, 'Ítems', [23, 38, 11, 15, 23, 16, 17, 21, 17, 18, 18, 20, 20, 16, 46], {
    frozen: { xSplit: 2, ySplit: detailHeaderRow },
  });
  const pending = addSheet(workbook, 'Sin cotizar', [34, 25, 11, 57], { frozen: { xSplit: 0, ySplit: detailHeaderRow }, tab: colors.amber });

  heading(detail, 'Detalle del presupuesto', project.name, 'O');
  tableHeader(detail, detailHeaderRow, ['Zona', 'Artículo', 'Cantidad', 'Estado', 'Origen del precio', 'Unitario CRC', 'Subtotal CRC', 'Tienda', 'Producto', 'Unitario USD', 'Disponibilidad', 'Categoría', 'Fuente', 'Fecha del precio', 'Notas y motivo']);
  addDetailRows(detail, groups, exchangeRate);
  if (detail.rowCount >= detailFirstRow) detail.autoFilter = `A${detailHeaderRow}:O${detail.rowCount}`;
  else cell(detail, ['A6', 'O6'], 'El proyecto aún no tiene artículos.');
  detail.pageSetup.printTitlesRow = `${detailHeaderRow}:${detailHeaderRow}`;
  addSummary(summary, project, groups, exchangeRate);

  heading(pending, 'Artículos pendientes de cotizar', project.name, 'D');
  tableHeader(pending, detailHeaderRow, ['Artículo', 'Zona', 'Cantidad', 'Motivo o notas']);
  for (const group of groups) {
    for (const item of group.items.filter(({ quotes }) => !quotes.length)) {
      const reason = [item.unquotedReason ?? 'Por buscar', item.notes].filter(Boolean).join('\n');
      const row = pending.addRow([item.name, group.label, item.qty, reason]);
      row.height = fittedHeight([item.name, 31], [group.label, 22], [reason, 54]);
      styleRow(row, 4, row.number % 2 === 0);
      row.getCell(3).alignment = { horizontal: 'right', vertical: 'middle', indent: 1 };
    }
  }
  if (pending.rowCount >= detailFirstRow) pending.autoFilter = `A${detailHeaderRow}:D${pending.rowCount}`;
  else cell(pending, ['A6', 'D6'], project.items.length ? 'Todos los artículos tienen una cotización.' : 'El proyecto aún no tiene artículos.');
  pending.pageSetup.printTitlesRow = `${detailHeaderRow}:${detailHeaderRow}`;
  return Buffer.from(await workbook.xlsx.writeBuffer() as ArrayBuffer);
}
