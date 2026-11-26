import { useSyncExternalStore, type ComponentProps } from 'react';

export const projectsPath = '/proyectos';
export const projectViews = ['resumen', 'agentes', 'inventario', 'referencias', 'airbnb'] as const;
export type ProjectView = (typeof projectViews)[number];

export function projectPath(id: string, view: ProjectView = 'resumen') {
  return `${projectsPath}/${id}${view === 'resumen' ? '' : `/${view}`}`;
}

export function readRoute(path: string) {
  const parts = path.replace(/\/$/, '').split('/');
  if (path === '/' || path === projectsPath || path === `${projectsPath}/`) return { kind: 'projects' } as const;
  const [, prefix, id, rawView = 'resumen'] = parts;
  const view = projectViews.find((candidate) => candidate === rawView);
  if (prefix === 'proyectos' && id && view && parts.length <= 4
    && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)) {
    return { kind: 'project', id, view } as const;
  }
  return { kind: 'missing' } as const;
}

const navigationEvent = 'workspace-navigation';

function subscribe(listener: () => void) {
  window.addEventListener('popstate', listener);
  window.addEventListener(navigationEvent, listener);
  return () => {
    window.removeEventListener('popstate', listener);
    window.removeEventListener(navigationEvent, listener);
  };
}

export function usePathname() {
  return useSyncExternalStore(subscribe, () => window.location.pathname);
}

export function navigate(path: string, replace = false) {
  if (`${window.location.pathname}${window.location.search}${window.location.hash}` === path) return;
  if (replace) window.history.replaceState(null, '', path);
  else window.history.pushState(null, '', path);
  window.dispatchEvent(new Event(navigationEvent));
  window.scrollTo(0, 0);
}

export function Link({ href, onClick, ...props }: ComponentProps<'a'> & { href: string }) {
  return <a {...props} href={href} onClick={(event) => {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey
      || props.target || props.download !== undefined) return;
    event.preventDefault();
    navigate(href);
  }} />;
}
