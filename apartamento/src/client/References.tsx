import { useEffect, useRef, useState } from 'react';
import { ArrowUpRight, Check, ImagePlus, Images, Maximize2, Sparkles, Trash2 } from 'lucide-react';
import { normalizeSearch } from '@shared/search';
import { REFERENCE_MAX_ANALYSIS_IMAGES, REFERENCE_MAX_BYTES, type FurnishingProject, type ProjectReference, type ReferenceAnalysis, type ReferenceSuggestion } from '@shared/types';
import { Link, projectPath } from './navigation';
import { api, ApiError, Modal, Notice, post, referenceImage, Spinner } from './ui';

type ReferenceState = { references: ProjectReference[]; analysis: ReferenceAnalysis | null; analysisAvailable: boolean };
const dateTime = new Intl.DateTimeFormat('es-CR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Costa_Rica' });

function referenceError(cause: unknown, fallback: string) {
  return cause instanceof ApiError && [400, 404, 409, 413, 415, 502, 503].includes(cause.status) && cause.message ? cause.message : fallback;
}

export default function References({ project, active, onProjectChange }: {
  project: FurnishingProject;
  active: boolean;
  onProjectChange: (project: FurnishingProject) => void;
}) {
  const endpoint = `/api/projects/${project.id}/references`;
  const uploadInput = useRef<HTMLInputElement>(null);
  const uploadButton = useRef<HTMLButtonElement>(null);
  const [state, setState] = useState<ReferenceState | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState<'upload' | 'delete' | 'analyze' | 'add' | null>(null);
  const [preview, setPreview] = useState<ProjectReference | null>(null);
  const [deleting, setDeleting] = useState<ProjectReference | null>(null);
  const [modalError, setModalError] = useState('');
  const [message, setMessage] = useState<{ tone: 'error' | 'success'; text: string } | null>(null);

  useEffect(() => {
    if (!active) {
      setPreview(null);
      setDeleting(null);
    }
  }, [active]);

  useEffect(() => {
    const controller = new AbortController();
    api<ReferenceState>(endpoint, { signal: controller.signal }).then((result) => {
      if (controller.signal.aborted) return;
      setState(result);
      setSelected(result.references.slice(0, REFERENCE_MAX_ANALYSIS_IMAGES).map(({ id }) => id));
    }).catch(() => {
      if (!controller.signal.aborted) setMessage({ tone: 'error', text: 'No se pudieron cargar las referencias.' });
    });
    return () => controller.abort();
  }, [endpoint, retry]);

  async function upload(file: File) {
    setMessage(null);
    if (file.size > REFERENCE_MAX_BYTES) {
      setMessage({ tone: 'error', text: 'La imagen supera el límite de 5 MB.' });
      return;
    }
    setBusy('upload');
    try {
      const body = new FormData();
      body.append('file', file);
      const reference = await api<ProjectReference>(endpoint, { method: 'POST', body });
      setState((current) => current && { ...current, references: [...current.references, reference] });
      setSelected((current) => current.length < REFERENCE_MAX_ANALYSIS_IMAGES ? [...current, reference.id] : current);
      setMessage({ tone: 'success', text: 'Referencia subida.' });
    } catch (cause) {
      setMessage({ tone: 'error', text: referenceError(cause, 'No se pudo subir la imagen. Inténtalo de nuevo.') });
    } finally {
      setBusy(null);
    }
  }

  async function remove() {
    if (!deleting) return;
    setBusy('delete');
    setModalError('');
    setMessage(null);
    const id = deleting.id;
    try {
      await api(`${endpoint}/${id}`, { method: 'DELETE' });
      setState((current) => current && { ...current, references: current.references.filter((reference) => reference.id !== id) });
      setSelected((current) => current.filter((selectedId) => selectedId !== id));
      setDeleting(null);
      setMessage({ tone: 'success', text: 'Referencia eliminada.' });
      requestAnimationFrame(() => uploadButton.current?.focus({ preventScroll: true }));
    } catch (cause) {
      setModalError(referenceError(cause, 'No se pudo eliminar la referencia. Inténtalo de nuevo.'));
    } finally {
      setBusy(null);
    }
  }

  async function analyze() {
    if (!selected.length || busy) return;
    setBusy('analyze');
    setMessage(null);
    try {
      const analysis = await post<ReferenceAnalysis>(`${endpoint}/analyze`, { referenceIds: selected });
      setState((current) => current && { ...current, analysis });
      setMessage({ tone: 'success', text: analysis.suggestions.length ? 'El análisis está listo. Revisa las sugerencias.' : 'El análisis terminó sin sugerencias adicionales.' });
    } catch (cause) {
      setMessage({ tone: 'error', text: referenceError(cause, 'El análisis se interrumpió. Inténtalo de nuevo.') });
    } finally {
      setBusy(null);
    }
  }

  async function add(suggestion: ReferenceSuggestion) {
    setBusy('add');
    setMessage(null);
    try {
      const { project: updated } = await post<{ project: FurnishingProject }>(`/api/projects/${project.id}/reference-suggestions/${suggestion.id}/add`, {});
      onProjectChange(updated);
      setMessage({ tone: 'success', text: `${suggestion.name} está en el inventario.` });
    } catch (cause) {
      setMessage({ tone: 'error', text: referenceError(cause, 'No se pudo agregar el artículo. Inténtalo de nuevo.') });
    } finally {
      setBusy(null);
    }
  }

  if (!state) return message ? <div className="panel page-state">
    <Notice>{message.text}</Notice>
    <button className="button button-secondary" type="button" onClick={() => { setMessage(null); setRetry((value) => value + 1); }}>Reintentar</button>
  </div> : <p className="page-loading" role="status"><Spinner /> Cargando referencias…</p>;

  const byId = new Map(state.references.map((reference) => [reference.id, reference]));
  const zoneNames = new Map(project.zones.map(({ id, labelEs }) => [id, labelEs]));

  return (
    <div className="references-page">
      {message && <Notice tone={message.tone}>{message.text}</Notice>}
      <div className="references-layout">
        <section className="panel" aria-labelledby="gallery-title">
          <div className="panel-header">
            <div><h2 id="gallery-title">Galería del proyecto</h2><p>JPG, PNG o WebP de hasta 5 MB por imagen.</p></div>
            <input type="file" accept="image/jpeg,image/png,image/webp" ref={uploadInput} className="sr-only" tabIndex={-1} aria-label="Archivo de referencia" onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (file && !busy) void upload(file);
            }} />
            <button type="button" className="button button-secondary" ref={uploadButton} disabled={!!busy} onClick={() => uploadInput.current?.click()}>
              {busy === 'upload' ? <><Spinner /> Subiendo…</> : <><ImagePlus size={17} aria-hidden="true" /> Subir imagen</>}
            </button>
          </div>
          {state.references.length ? (
            <ul className="reference-gallery" role="list">
              {state.references.map((reference) => {
                const isSelected = selected.includes(reference.id);
                return (
                  <li className="reference-card" key={reference.id}>
                    <button className="reference-preview" type="button" onClick={() => setPreview(reference)} aria-label={`Ampliar ${reference.title}`}>
                      <img src={referenceImage(project.id, reference.id)} alt="" loading="lazy" />
                      <span><Maximize2 size={16} aria-hidden="true" /></span>
                    </button>
                    <div className="reference-meta">
                      <h3>{reference.title}</h3>
                      <button className="icon-button remove-reference" type="button" aria-label={`Eliminar ${reference.title}`} disabled={!!busy} onClick={() => { setDeleting(reference); setModalError(''); }}>
                        <Trash2 size={17} aria-hidden="true" />
                      </button>
                    </div>
                    <label className="reference-select">
                      <input type="checkbox" checked={isSelected} aria-label={`Incluir ${reference.title} en el análisis`} disabled={!!busy || (!isSelected && selected.length === REFERENCE_MAX_ANALYSIS_IMAGES)} onChange={(event) => {
                        const checked = event.currentTarget.checked;
                        setSelected((current) => checked ? [...current, reference.id] : current.filter((id) => id !== reference.id));
                      }} /> Incluir
                    </label>
                  </li>
                );
              })}
            </ul>
          ) : <div className="page-state gallery-empty"><Images size={24} aria-hidden="true" /><h3>Aún no hay referencias</h3><p>Sube un plano, un render o una foto del espacio.</p></div>}
        </section>
        <section className="panel analysis-panel" aria-labelledby="analysis-title" aria-busy={busy === 'analyze'}>
          <div className="panel-header analysis-header">
            <span className="state-icon"><Sparkles size={20} aria-hidden="true" /></span>
            <div><h2 id="analysis-title">Análisis visual</h2><p>La IA compara las imágenes seleccionadas con el inventario del proyecto.</p></div>
          </div>
          <div className="analysis-controls">
            {state.analysisAvailable ? <>
              <button className="button button-primary" type="button" onClick={analyze} disabled={!!busy || !selected.length}>
                {busy === 'analyze' ? <><Spinner /> Analizando imágenes…</> : <><Sparkles size={16} aria-hidden="true" /> Analizar con IA</>}
              </button>
              <p>{selected.length} de {REFERENCE_MAX_ANALYSIS_IMAGES} imágenes seleccionadas</p>
              <p>Se envían al proveedor de IA las imágenes seleccionadas y el inventario del proyecto.</p>
            </> : <p>El análisis por IA no está habilitado en este entorno.</p>}
          </div>
          {state.analysis && <div className="analysis-results">
            <div className="analysis-results-head">
              <h3>Sugerencias para cotizar</h3><p>Último análisis · {dateTime.format(new Date(state.analysis.createdAt))}</p>
            </div>
            {state.analysis.suggestions.length ? <ul role="list">
              {state.analysis.suggestions.map((suggestion) => {
                const existing = project.items.some((item) => item.id === `visual-${suggestion.id}` || (
                  (item.zoneId || null) === suggestion.zoneId && normalizeSearch(item.name) === normalizeSearch(suggestion.name)
                ));
                const asset = byId.get(suggestion.referenceId);
                return <li key={suggestion.id}>
                  <h4>{suggestion.name}</h4>
                  <p className="suggestion-meta">{suggestion.qty} {suggestion.qty === 1 ? 'unidad' : 'unidades'} · {suggestion.zoneId ? zoneNames.get(suggestion.zoneId) ?? 'Sin zona asignada' : 'Sin zona asignada'}</p>
                  <p>{suggestion.reason}</p>
                  {asset && <button className="text-action" type="button" onClick={() => setPreview(asset)}>{asset.title} <ArrowUpRight size={14} aria-hidden="true" /></button>}
                  {existing ? <Link className="suggestion-added" href={projectPath(project.id, 'inventario')}><Check size={16} aria-hidden="true" /> Ver en inventario</Link> : (
                    <button type="button" className="button button-small button-secondary" disabled={!!busy} onClick={() => add(suggestion)} aria-label={`Agregar a pendientes: ${suggestion.name}`}>Agregar a pendientes</button>
                  )}
                </li>;
              })}
            </ul> : <p>No se encontraron artículos adicionales en esta selección.</p>}
          </div>}
        </section>
      </div>
      {preview && <Modal title={preview.title} onDismiss={() => setPreview(null)} wide>
        <div className="reference-full"><img src={referenceImage(project.id, preview.id)} alt={preview.title} /></div>
        <div className="modal-actions button-row"><a className="button button-secondary" href={referenceImage(project.id, preview.id)} target="_blank" rel="noreferrer">Abrir original <ArrowUpRight size={16} aria-hidden="true" /></a></div>
      </Modal>}
      {deleting && <Modal title="Eliminar referencia" busy={busy === 'delete'} onDismiss={() => setDeleting(null)}>
        <p className="delete-description">Se eliminará «{deleting.title}» de este proyecto.</p>
        {modalError && <Notice>{modalError}</Notice>}
        <div className="modal-actions button-row">
          <button type="button" className="button button-secondary" disabled={!!busy} onClick={() => setDeleting(null)}>Cancelar</button>
          <button type="button" className="button button-danger" disabled={!!busy} onClick={remove}>
            {busy === 'delete' ? <><Spinner /> Eliminando…</> : 'Eliminar imagen'}
          </button>
        </div>
      </Modal>}
    </div>
  );
}
