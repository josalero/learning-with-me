import { brandGuide } from '@shared/brands';

export function BrandGuide() {
  return <details className="brand-guide">
    <summary>Guía de marcas y gamas</summary>
    <p>Referencias internacionales para comparar líneas de producto. Las fichas locales determinan el precio y la disponibilidad. Una línea reconocida se identifica por su nombre en la ficha de la tienda.</p>
    <ul>{brandGuide.map((entry) => <li key={entry.brand}>
      <strong>{entry.brand} · {entry.line}</strong><small>{entry.category}</small><p>{entry.trait}</p>
      <a className="text-action" href={entry.url} target="_blank" rel="noreferrer">Fuente de {entry.brand}</a>
    </li>)}</ul>
    <p>Para comprobar la identidad comercial, compara el código de barras con el registro del titular. Confirma autenticidad y garantía con el fabricante o distribuidor.</p>
    <a className="text-action" target="_blank" rel="noreferrer" href="https://www.gs1.org/services/verified-by-gs1">Consultar Verified by GS1</a>
  </details>;
}
