import { useRef, useState, type FormEvent } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import type { CatalogItem, ProjectZone } from '@shared/types';
import { ApiError, Modal, Notice, Spinner } from './ui';

export const unassignedLabel = 'Sin ambiente asignado';

export function ItemZonePicker({ name, label, zones, initialZoneId, disabled = false }: {
  name: string;
  label: string;
  zones: ProjectZone[];
  initialZoneId?: string;
  disabled?: boolean;
}) {
  const [selected, setSelected] = useState(initialZoneId);
  const menu = useRef<HTMLDetailsElement>(null);
  const options = [...zones.map(({ id, labelEs }) => ({ id, label: labelEs })), { id: '', label: unassignedLabel }];
  const currentLabel = options.find(({ id }) => id === selected)?.label;

  return <div className="item-zone-field">
    <span className="item-zone-label">{label}</span>
    {selected !== undefined && <input type="hidden" name={name} value={selected} />}
    <details className="item-zone-picker" ref={menu}>
      <summary aria-label={`${label}. ${currentLabel ?? 'Elegí un ambiente'}`} aria-disabled={disabled || undefined}
        onClick={(event) => { if (disabled) event.preventDefault(); }}>
        <span>{currentLabel ?? 'Elegí un ambiente'}</span><ChevronDown size={15} aria-hidden="true" />
      </summary>
      <ul role="list" aria-label={label}>{options.map(({ id, label: option }) => <li key={id}>
        <button type="button" disabled={disabled} aria-pressed={selected === id} onClick={() => {
          setSelected(id);
          const details = menu.current;
          if (details) {
            details.open = false;
            details.querySelector('summary')?.focus();
          }
        }}>{option}{selected === id && <Check size={15} aria-hidden="true" />}</button>
      </li>)}</ul>
    </details>
  </div>;
}

export function MoveItemDialog({ item, zones, onClose, onSave, onMoved }: {
  item: CatalogItem;
  zones: ProjectZone[];
  onClose: () => void;
  onSave: (zoneId: string) => Promise<void>;
  onMoved: (zoneId: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const current = zones.find(({ id }) => id === item.zoneId);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const zoneId = new FormData(event.currentTarget).get('zoneId');
    if (busy) return;
    if (typeof zoneId !== 'string' || zoneId === (current?.id ?? '')) {
      setError('Elegí un ambiente distinto para mover el artículo.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await onSave(zoneId);
      onMoved(zoneId);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : 'No se pudo mover el artículo. Intentá de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  return <Modal title="Mover a otro ambiente" onDismiss={onClose} busy={busy}>
    <p className="quote-item-title">{item.name}</p>
    <p className="quote-tier-label">Ubicación actual · {current?.labelEs ?? unassignedLabel}</p>
    <form className="manual-form" onSubmit={submit}>
      <ItemZonePicker name="zoneId" label="Ambiente de destino" zones={zones} initialZoneId={current?.id ?? ''} disabled={busy} />
      {error && <Notice>{error}</Notice>}
      <div className="button-row modal-actions">
        <button type="button" className="button button-secondary" onClick={onClose} disabled={busy}>Cancelar</button>
        <button type="submit" className="button button-primary" disabled={busy}>{busy ? <><Spinner /> Moviendo…</> : 'Mover artículo'}</button>
      </div>
    </form>
  </Modal>;
}
