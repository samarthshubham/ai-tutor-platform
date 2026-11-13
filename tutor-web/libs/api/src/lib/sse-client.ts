import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { NETWORK_ERROR, toApiError } from './api-error';

export interface SseEvent<T = unknown> {
  event: string;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class SseClient {
  stream<T = unknown>(
    url: string,
    init: RequestInit = {},
  ): Observable<SseEvent<T>> {
    return new Observable<SseEvent<T>>((subscriber) => {
      const controller = new AbortController();
      const headers = new Headers(init.headers);
      if (!headers.has('Accept')) {
        headers.set('Accept', 'text/event-stream');
      }

      const run = async () => {
        try {
          const response = await fetch(url, {
            ...init,
            headers,
            signal: controller.signal,
          });
          if (!response.ok) {
            const body: unknown = await response.json().catch(() => null);
            subscriber.error(toApiError(response.status, body));
            return;
          }
          if (response.body) {
            await readEvents<T>(response.body, (event) =>
              subscriber.next(event),
            );
          }
          subscriber.complete();
        } catch (error) {
          if (!controller.signal.aborted) {
            subscriber.error(
              error instanceof TypeError ? NETWORK_ERROR : error,
            );
          }
        }
      };
      void run();

      return () => controller.abort();
    });
  }
}

async function readEvents<T>(
  body: ReadableStream<Uint8Array>,
  emit: (event: SseEvent<T>) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = '';
  let dataLines: string[] = [];

  const dispatch = () => {
    if (dataLines.length > 0) {
      emit({
        event: eventName || 'message',
        data: JSON.parse(dataLines.join('\n')) as T,
      });
    }
    eventName = '';
    dataLines = [];
  };

  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      return;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      if (line === '') {
        dispatch();
      } else if (!line.startsWith(':')) {
        const separator = line.indexOf(':');
        const field = separator === -1 ? line : line.slice(0, separator);
        const value =
          separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '');
        if (field === 'event') {
          eventName = value;
        } else if (field === 'data') {
          dataLines.push(value);
        }
      }
    }
  }
}
