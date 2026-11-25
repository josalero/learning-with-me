import { useEffect, useId, useRef, type ReactNode } from 'react';
import { AlertCircle, Check, House, LoaderCircle, X } from 'lucide-react';

export const money = new Intl.NumberFormat('es-CR', {
  style: 'currency',
  currency: 'CRC',
  maximumFractionDigits: 0,
});

export function referenceImage(projectId: string, referenceId: string) {
  return `/api/projects/${projectId}/references/${referenceId}/image`;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, credentials: 'include' });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, typeof body?.error === 'string' ? body.error : '');
  }
  return response.json() as Promise<T>;
}

export function post<T>(path: string, body: Record<string, unknown>): Promise<T> {
  return api<T>(path, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark"><House size={20} aria-hidden="true" /></span>
      <span><strong>Natarundi</strong><small>Equipamiento</small></span>
    </div>
  );
}

export function Spinner() {
  return <LoaderCircle className="spinner" size={17} aria-hidden="true" />;
}

export function Notice({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'success' }) {
  const Icon = tone === 'error' ? AlertCircle : Check;
  return (
    <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={17} aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}

export function Modal({ title, onDismiss, busy = false, wide = false, children }: {
  title: string;
  onDismiss: () => void;
  busy?: boolean;
  wide?: boolean;
  children: ReactNode;
}) {
  const id = useId();
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current!;
    const trigger = document.activeElement;
    element.showModal();
    return () => {
      element.close();
      if (trigger instanceof HTMLElement && trigger.isConnected && document.activeElement === document.body) {
        trigger.focus();
      }
    };
  }, []);

  return (
    <dialog
      ref={dialog}
      className={`modal${wide ? ' modal-wide' : ''}`}
      aria-labelledby={id}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onDismiss();
      }}
    >
      <div className="modal-head">
        <h2 id={id}>{title}</h2>
        <button type="button" className="icon-button" aria-label="Cerrar" disabled={busy} onClick={onDismiss}>
          <X size={20} aria-hidden="true" />
        </button>
      </div>
      {children}
    </dialog>
  );
}
