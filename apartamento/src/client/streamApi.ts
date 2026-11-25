import type { ProgressEvent } from '@shared/types';
import { ApiError } from './ui';

export async function streamPost<Activity, Result>(path: string, body: Record<string, unknown>, onActivity: (activity: Activity) => void) {
  const response = await fetch(path, {
    method: 'POST', credentials: 'include',
    headers: { 'content-type': 'application/json', accept: 'application/x-ndjson' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const failure = await response.json().catch(() => null);
    throw new ApiError(response.status, typeof failure?.error === 'string' ? failure.error : '');
  }
  if (!response.body) throw new Error('Operation returned no response stream');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';
      if (done && buffer.trim()) lines.push(buffer);
      for (const line of lines) {
        if (!line.trim()) continue;
        const event = JSON.parse(line) as ProgressEvent<Activity, Result>;
        if (event.type === 'activity') onActivity(event.activity);
        if (event.type === 'result') return event.result;
        if (event.type === 'error') throw new ApiError(event.status ?? 500, event.message ?? '');
      }
      if (done) throw new Error('Operation ended before returning a result');
    }
  } finally {
    reader.releaseLock();
  }
}
