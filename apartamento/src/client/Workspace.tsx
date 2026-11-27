import { useEffect, type ReactNode } from 'react';
import { ChevronRight, Download, FolderOpen, House, Images, LayoutGrid, ListChecks, LogOut, ClipboardCheck } from 'lucide-react';
import type { BudgetTier, FurnishingProject } from '@shared/types';
import { Link, projectPath, projectsPath, usePathname, type ProjectView } from './navigation';
import { Brand, Notice, Spinner } from './ui';

export type Session = { email: string; isDemo: boolean };
export type Account = { session: Session; onLogout: () => void; loggingOut: boolean; logoutError: boolean };

const sections = [
  { id: 'resumen', title: 'Resumen', icon: LayoutGrid },
  { id: 'inventario', title: 'Ambientes', icon: House },
  { id: 'agentes', title: 'Agentes', icon: ListChecks },
  { id: 'referencias', title: 'Referencias', icon: Images },
  { id: 'airbnb', title: 'Airbnb', icon: ClipboardCheck },
] as const;

export function Workspace({ session, onLogout, loggingOut, logoutError, project, view, roomLabel, exportTier, children }: Account & {
  project?: FurnishingProject;
  view?: ProjectView;
  roomLabel?: string;
  exportTier?: BudgetTier;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const currentSection = sections.find(({ id }) => id === view);
  const projectsActive = pathname === '/' || pathname.replace(/\/$/, '') === projectsPath;

  useEffect(() => {
    const heading = document.querySelector<HTMLElement>('main h1');
    if (heading && (document.activeElement === document.body || document.activeElement?.closest('.workspace-nav, .breadcrumbs, .project-list, .overview-links, .planner-room-links'))) {
      heading.focus({ preventScroll: true });
    }
  }, [pathname, project?.id]);

  return (
    <div className="workspace">
      <a className="skip-link" href="#contenido">Ir al contenido</a>
      <aside className="workspace-sidebar">
        <div className="workspace-brand">
          <Link href={projectsPath} className="brand-link"><Brand /></Link>
          <button type="button" className="icon-button mobile-logout" aria-label="Cerrar sesión" onClick={onLogout} disabled={loggingOut}>
            {loggingOut ? <Spinner /> : <LogOut size={17} aria-hidden="true" />}
          </button>
        </div>
        <nav className="workspace-nav" aria-label="Navegación del espacio de trabajo">
          <Link className="workspace-projects-link" href={projectsPath} aria-current={projectsActive ? 'page' : undefined}>
            <FolderOpen size={18} aria-hidden="true" /> <span>Proyectos</span>
          </Link>
          {project && (
            <div className="workspace-project-nav">
              <span className="workspace-nav-label">Proyecto activo</span>
              <div className="workspace-section-links">
                {sections.map(({ id, title, icon: Icon }) => <Link key={id} href={projectPath(project.id, id)} aria-current={view === id ? 'page' : undefined}>
                  <Icon size={17} aria-hidden="true" /><span>{title}</span>
                </Link>)}
              </div>
            </div>
          )}
        </nav>
        <div className="workspace-account">
          <span className="avatar" aria-hidden="true">{session.email.charAt(0).toUpperCase()}</span>
          <div><strong>{session.isDemo ? 'Cuenta demo' : 'Mi cuenta'}</strong><span title={session.email}>{session.email}</span></div>
          <button type="button" className="icon-button" aria-label="Cerrar sesión" title="Cerrar sesión" onClick={onLogout} disabled={loggingOut}>
            {loggingOut ? <Spinner /> : <LogOut size={17} aria-hidden="true" />}
          </button>
        </div>
      </aside>
      <main id="contenido" tabIndex={-1} className="workspace-main">
        <header className="workspace-topbar">
          <nav className="breadcrumbs" aria-label="Ruta de navegación">
            {project ? (
              <>
                <Link href={projectsPath}>Proyectos</Link><ChevronRight size={14} aria-hidden="true" />
                {view === 'resumen' ? <span aria-current="page" title={project.name}>{project.name}</span> : (
                  <><Link href={projectPath(project.id)} title={project.name}>{project.name}</Link><ChevronRight size={14} aria-hidden="true" />
                    {roomLabel ? <><Link href={projectPath(project.id, 'inventario')}>Ambientes</Link><ChevronRight size={14} aria-hidden="true" /><span aria-current="page">{roomLabel}</span></> : <span aria-current="page">{currentSection?.title}</span>}
                  </>
                )}
              </>
            ) : <span aria-current="page">Proyectos</span>}
          </nav>
          {project && <a href={`/api/projects/${project.id}/export.xlsx${exportTier ? `?tier=${exportTier}` : ''}`} className="button button-secondary export-button">
            <Download size={16} aria-hidden="true" /> Exportar Excel
          </a>}
        </header>
        <div className="workspace-content">
          {logoutError && <Notice>No se pudo cerrar la sesión. Inténtalo de nuevo.</Notice>}
          {children}
        </div>
      </main>
    </div>
  );
}
