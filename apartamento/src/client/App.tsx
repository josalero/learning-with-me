import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff } from 'lucide-react';
import type { FurnishingProject } from '@shared/types';
import Dashboard from './Dashboard';
import { Link, navigate, projectsPath, readRoute, usePathname, type ProjectView } from './navigation';
import ProjectsPage from './ProjectsPage';
import { api, ApiError, Brand, Notice, post, Spinner } from './ui';
import { Workspace, type Account, type Session } from './Workspace';

export default function App() {
  const pathname = usePathname();
  const route = readRoute(pathname);
  const [session, setSession] = useState<Session | null>();
  const [sessionError, setSessionError] = useState(false);
  const [sessionRetry, setSessionRetry] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState(false);
  const onSessionExpired = useCallback(() => setSession(null), []);

  useEffect(() => {
    const controller = new AbortController();
    api<Session>('/api/auth/me', { signal: controller.signal })
      .then((user) => { if (!controller.signal.aborted) setSession(user); })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setSessionError(!(error instanceof ApiError && error.status === 401));
        setSession(null);
      });
    return () => controller.abort();
  }, [sessionRetry]);

  useEffect(() => {
    if (session && pathname === '/') navigate(projectsPath, true);
  }, [session, pathname]);

  async function logout() {
    setLoggingOut(true);
    setLogoutError(false);
    try {
      await post('/api/auth/logout', {});
      navigate(projectsPath, true);
      setSession(null);
    } catch {
      setLogoutError(true);
    } finally {
      setLoggingOut(false);
    }
  }

  if (session === undefined) {
    return <main className="loading-screen"><Brand /><p role="status"><Spinner /> Verificando sesión…</p></main>;
  }

  if (session === null) {
    return <Login connectionError={sessionError} onRetry={() => {
      setSessionError(false);
      setSession(undefined);
      setSessionRetry((count) => count + 1);
    }} onLogin={(user) => {
      navigate(projectsPath, true);
      setSessionError(false);
      setSession(user);
    }} />;
  }

  const account = { session, onLogout: logout, loggingOut, logoutError };
  if (route.kind === 'projects') return <ProjectsPage {...account} onSessionExpired={onSessionExpired} />;
  if (route.kind === 'project') return <ProjectPage key={route.id} {...account} projectId={route.id} view={route.view} onSessionExpired={onSessionExpired} />;
  return <Workspace {...account}><MissingProject /></Workspace>;
}

function ProjectPage({ projectId, view, onSessionExpired, ...account }: Account & {
  projectId: string;
  view: ProjectView;
  onSessionExpired: () => void;
}) {
  const [project, setProject] = useState<FurnishingProject | null>(null);
  const [error, setError] = useState<'missing' | 'failed' | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    api<FurnishingProject>(`/api/projects/${projectId}`, { signal: controller.signal }).then((result) => {
      if (!controller.signal.aborted) setProject(result);
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setError(cause instanceof ApiError && cause.status === 404 ? 'missing' : 'failed');
    });
    return () => controller.abort();
  }, [projectId, retry, onSessionExpired]);

  if (project) return <Dashboard {...account} initialProject={project} view={view} />;
  return (
    <Workspace {...account}>
      {error === 'missing' ? <MissingProject /> : error ? (
        <div className="panel page-state">
          <h1 tabIndex={-1}>No se pudo cargar el proyecto</h1>
          <button type="button" className="button button-secondary" onClick={() => {
            setError(null);
            setRetry((value) => value + 1);
          }}>Reintentar</button>
          <Link href={projectsPath}>Volver a proyectos</Link>
        </div>
      ) : <p className="page-loading" role="status"><Spinner /> Cargando proyecto…</p>}
    </Workspace>
  );
}

function MissingProject() {
  return <div className="panel page-state">
    <h1 tabIndex={-1}>Proyecto no disponible</h1>
    <p>Abre un proyecto de tu espacio de trabajo.</p>
    <Link className="button button-primary" href={projectsPath}>Volver a proyectos</Link>
  </div>;
}

function Login({ onLogin, connectionError, onRetry }: {
  onLogin: (user: Session) => void;
  connectionError: boolean;
  onRetry: () => void;
}) {
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError('');
    try {
      onLogin(await post<Session>('/api/auth/login', {
        email: String(data.get('email')).trim(),
        password: String(data.get('password')),
      }));
    } catch (cause) {
      setError(cause instanceof ApiError && (cause.status === 400 || cause.status === 401)
        ? 'Revisa el correo y la contraseña e inténtalo de nuevo.'
        : 'No se pudo iniciar sesión. Inténtalo de nuevo en un momento.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-layout">
      <div className="login-main">
        <Brand />
        <div className="login-content">
          <h1>Inicia sesión</h1>
          <p>Abre tus proyectos de equipamiento y continúa donde los dejaste.</p>
          {connectionError && (
            <div className="login-connection">
              <Notice>No se pudo verificar la sesión.</Notice>
              <button type="button" className="button button-secondary" onClick={onRetry}>Reintentar conexión</button>
            </div>
          )}
          <form className="login-form" onSubmit={submit}>
            <label htmlFor="login-email">Correo electrónico</label>
            <input id="login-email" name="email" type="email" placeholder="tu@correo.com" autoComplete="username" required />
            <label htmlFor="login-password">Contraseña</label>
            <div className="password-field">
              <input id="login-password" name="password" type={visible ? 'text' : 'password'} autoComplete="current-password" required />
              <button type="button" className="icon-button" onClick={() => setVisible(!visible)} aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
              </button>
            </div>
            {error && <Notice>{error}</Notice>}
            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? <><Spinner /> Ingresando…</> : <>Ingresar <ArrowRight size={17} aria-hidden="true" /></>}
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}
