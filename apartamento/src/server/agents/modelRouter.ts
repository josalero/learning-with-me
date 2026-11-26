export type RoutingMode = 'ECONOMY' | 'BALANCED' | 'FRONTIER';

export function resolveModelIds(mode: RoutingMode = 'BALANCED'): {
  primary: string;
  vision: string;
  critic: string;
} {
  const economy = process.env.OPENROUTER_MODEL_ECONOMY || 'anthropic/claude-haiku-4.5';
  const balanced = process.env.OPENROUTER_MODEL_BALANCED || 'google/gemini-2.5-flash';
  const frontier = process.env.OPENROUTER_MODEL_FRONTIER || 'anthropic/claude-sonnet-4';

  if (mode === 'ECONOMY') {
    return { primary: economy, vision: economy, critic: economy };
  }
  if (mode === 'FRONTIER') {
    return { primary: frontier, vision: balanced, critic: frontier };
  }
  return { primary: balanced, vision: balanced, critic: balanced };
}

export function configuredModels() {
  const mode = process.env.AI_ROUTING_MODE;
  return resolveModelIds(mode === 'ECONOMY' || mode === 'FRONTIER' ? mode : 'BALANCED');
}

export function offlineReason() {
  if (process.env.AI_OFFLINE_STUB === '1') return 'El servidor tiene activado el modo sin IA.';
  if (!process.env.OPENROUTER_API_KEY?.trim()) return 'El servidor no tiene configurada la clave de búsqueda de OpenRouter.';
}

export function isAiOffline(): boolean {
  return Boolean(offlineReason());
}