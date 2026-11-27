import { useEffect, useState } from 'react';
import { ArrowUpRight, Building2, FolderOpen, MapPin, Search } from 'lucide-react';
import { normalizeSearch } from '@shared/search';
import { budgetProfiles } from '@shared/budgetProfiles';
import type { ProjectSummary } from '@shared/types';
import { Link, projectPath } from './navigation';
import { api, ApiError, money, Notice, referenceImage, Spinner } from './ui';
import { Workspace, type Account } from './Workspace';

export default function ProjectsPage({ onSessionExpired, ...account }: Account & { onSessionExpired: () => void }) {
  const [projects, setProjects] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    api<ProjectSummary[]>('/api/projects', { signal: controller.signal }).then((result) => {
      if (!controller.signal.aborted) setProjects(result);
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setError(true);
    });
    return () => controller.abort();
  }, [onSessionExpired, retry]);

  const filter = normalizeSearch(query);
  const visible = projects?.filter((project) => normalizeSearch(`${project.name} ${project.address}`).includes(filter));

  return (
    <Workspace {...account}>
      <div className="page-heading">
        <h1 tabIndex={-1}>Proyectos</h1>
        <p>Selecciona un proyecto para revisar su equipamiento y presupuesto.</p>
      </div>
      {error ? <div className="panel page-state">
        <Notice>No se pudieron cargar los proyectos.</Notice>
        <button className="button button-secondary" type="button" onClick={() => {
          setError(false);
          setRetry((value) => value + 1);
        }}>Reintentar</button>
      </div> : !projects ? <p className="page-loading" role="status"><Spinner /> Cargando proyectos…</p> : projects.length === 0 ? (
        <div className="panel page-state">
          <span className="state-icon"><FolderOpen size={23} aria-hidden="true" /></span>
          <h2>Aún no tienes proyectos asignados</h2>
          <p>Los proyectos de tu espacio de trabajo aparecerán aquí.</p>
        </div>
      ) : (
        <>
          <div className="projects-toolbar">
            <span role="status">{visible?.length ?? 0} {visible?.length === 1 ? 'proyecto' : 'proyectos'}</span>
            <label className="search-field project-search">
              <Search size={18} aria-hidden="true" /><span className="sr-only">Buscar proyectos</span>
              <input type="search" placeholder="Buscar por nombre o ubicación" value={query} onChange={(event) => setQuery(event.target.value)} />
            </label>
          </div>
          {visible?.length ? (
            <ul className="project-list" role="list">
              {visible.map((project) => {
                const pending = project.itemCount - project.quotedCount;
                return (
                  <li key={project.id}>
                    <Link className="project-card panel" href={projectPath(project.id, 'inventario')}>
                      <span className="project-cover">
                        {project.coverReferenceId ? <img src={referenceImage(project.id, project.coverReferenceId)} alt="" loading="lazy" /> : <Building2 size={42} aria-hidden="true" />}
                      </span>
                      <div className="project-card-body">
                        <h2>{project.name}</h2>
                        {project.address && <p className="project-address"><MapPin size={15} aria-hidden="true" />{project.address}</p>}
                        <dl className="project-card-metrics">
                          <div><dt>{budgetProfiles[project.budgetTier].label}</dt><dd>{project.quotedCount ? money.format(project.totalCrc) : 'Sin cotizar'}</dd></div>
                          <div><dt>Por cotizar</dt><dd>{pending} {pending === 1 ? 'artículo' : 'artículos'}</dd></div>
                        </dl>
                        <span className="project-open">Abrir proyecto <ArrowUpRight size={16} aria-hidden="true" /></span>
                      </div>
                    </Link>
                  </li>
                );
              })}
            </ul>
          ) : <div className="panel page-state">
            <h2>No encontramos proyectos</h2>
            <p>Prueba con otro nombre o ubicación.</p>
            <button className="button button-secondary" type="button" onClick={() => setQuery('')}>Limpiar búsqueda</button>
          </div>}
        </>
      )}
    </Workspace>
  );
}
