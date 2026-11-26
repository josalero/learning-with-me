import { useState, type FormEvent } from 'react';
import { Plus, X } from 'lucide-react';
import type { CatalogItem, FurnishingProject } from '@shared/types';
import { ApiError, Modal, Notice, post, Spinner } from './ui';

export default function SplitItemDialog({ projectId, item, onClose, onSaved }: {
  projectId: string;
  item: CatalogItem;
  onClose: () => void;
  onSaved: (project: FurnishingProject) => void;
}) {
  const [parts, setParts] = useState([0, 1]);
  const [next, setNext] = useState(2);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const components = parts.map((id) => ({
      name: String(data.get(`part-${id}`) ?? '').trim(), qty: Number(data.get(`qty-${id}`)),
    }));
    setBusy(true);
    setError('');
    try {
      onSaved(await post<FurnishingProject>(`/api/projects/${projectId}/items/${item.id}/split`, { components }));
    } catch (cause) {
      setError(cause instanceof ApiError && cause.message ? cause.message : 'No se pudo guardar el desglose. Inténtalo de nuevo.');
    } finally {
      setBusy(false);
    }
  }

  return <Modal title="Dividir en artículos" onDismiss={onClose} busy={busy}>
    <p className="quote-item-title">{item.name}</p>
    <p className="quote-tier-label">Sustituye esta partida por dos o más artículos del mismo ambiente. Por ejemplo, «Menaje» puede dividirse en ollas y utensilios. Cada artículo empieza sin precio y se cotiza por separado.</p>
    <form className="manual-form" onSubmit={save}>
      <fieldset disabled={busy} className="split-parts">
        <legend className="sr-only">Artículos del conjunto</legend>
        {parts.map((id, index) => <div key={id} className="split-part">
          <label>Artículo {index + 1}<input autoFocus={index === 0} name={`part-${id}`} required minLength={2} maxLength={120} /></label>
          <label>Cantidad<span className="sr-only"> del artículo {index + 1}</span><input name={`qty-${id}`} type="number" min={1} max={999} defaultValue={item.qty} required /></label>
          <button type="button" className="icon-button" disabled={parts.length <= 2} aria-label={`Quitar artículo ${index + 1}`}
            onClick={() => setParts((current) => current.filter((value) => value !== id))}><X size={17} aria-hidden="true" /></button>
        </div>)}
      </fieldset>
      {error && <Notice>{error}</Notice>}
      <div className="button-row modal-actions">
        <button type="button" className="button button-secondary" disabled={busy || parts.length >= 12} onClick={() => {
          setParts((current) => [...current, next]);
          setNext((value) => value + 1);
        }}><Plus size={17} aria-hidden="true" /> Otra fila</button>
        <button type="submit" className="button button-primary" disabled={busy}>{busy ? <><Spinner /> Guardando…</> : 'Guardar artículos'}</button>
      </div>
    </form>
  </Modal>;
}
