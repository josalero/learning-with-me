import { MAX_SPECIFICATIONS, MAX_SPECIFICATION_LENGTH } from '@shared/itemSpecifications';

export function specificationLines(value: FormDataEntryValue | null) {
  return String(value ?? '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

export function specificationError(lines: string[]) {
  if (!lines.length) return 'Agregá al menos una especificación del artículo.';
  if (lines.length > MAX_SPECIFICATIONS || lines.some((line) => line.length > MAX_SPECIFICATION_LENGTH)) {
    return `Ingresá hasta ${MAX_SPECIFICATIONS} especificaciones de ${MAX_SPECIFICATION_LENGTH} caracteres cada una.`;
  }
  return '';
}

export function SpecificationField({ id, label = 'Especificaciones', lines = [], busy = false, compact = false }: {
  id: string;
  label?: string;
  lines?: string[];
  busy?: boolean;
  compact?: boolean;
}) {
  return <div className="specification-field">
    <label htmlFor={id}>{label}</label>
    {!compact && <small id={`${id}-hint`}>Escribí un detalle por línea. Indicá unidades en las medidas y marcá como preferencia lo que sea flexible.</small>}
    <textarea id={id} name={id} rows={compact ? 2 : 5} required readOnly={busy}
      aria-describedby={compact ? undefined : `${id}-hint`} defaultValue={lines.join('\n')}
      maxLength={MAX_SPECIFICATIONS * (MAX_SPECIFICATION_LENGTH + 1) - 1}
      placeholder={compact ? 'Ej. Material y medidas' : 'Tamaño: queen\nPreferencia: base de madera\nAltura del respaldo: por confirmar'} />
  </div>;
}
