export function normalizeSearch(value: string) {
  return value.trim().toLocaleLowerCase('es-CR').normalize('NFD').replace(/\p{Diacritic}/gu, '');
}
