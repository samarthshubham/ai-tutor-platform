import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Observable, toArray } from 'rxjs';
import { SseClient, SseEvent } from './sse-client';

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

function eventStreamResponse(...chunks: string[]): Response {
  return new Response(streamOf(...chunks), {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

function collect<T>(events: Observable<SseEvent<T>>): Promise<SseEvent<T>[]> {
  return firstValueFrom(events.pipe(toArray()));
}

describe('SseClient', () => {
  let client: SseClient;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    client = TestBed.inject(SseClient);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('should emit parsed events and complete when the stream ends', async () => {
    fetchMock.mockResolvedValue(
      eventStreamResponse(
        'event: attempt-graded\ndata: {"attemptId":"a1","status":"GRADED","score":70}\n\n',
        'event: session-graded\ndata: {"sessionId":"s1","averageScore":76}\n\n',
      ),
    );

    const events = await collect(
      client.stream('/api/practice-sessions/s1/events'),
    );

    expect(events).toEqual([
      {
        event: 'attempt-graded',
        data: { attemptId: 'a1', status: 'GRADED', score: 70 },
      },
      { event: 'session-graded', data: { sessionId: 's1', averageScore: 76 } },
    ]);
  });

  it('should ignore comment lines', async () => {
    fetchMock.mockResolvedValue(
      eventStreamResponse(
        ': keep-alive\n\n',
        'event: token\n: keep-alive\ndata: {"text":"Hi"}\n\n',
      ),
    );

    const events = await collect(client.stream('/api/chat'));

    expect(events).toEqual([{ event: 'token', data: { text: 'Hi' } }]);
  });

  it('should join events split across chunks and CRLF line endings', async () => {
    fetchMock.mockResolvedValue(
      eventStreamResponse(
        'event: tok',
        'en\r\ndata: {"text":',
        '"Hi"}\r\n\r\n',
      ),
    );

    const events = await collect(client.stream('/api/chat'));

    expect(events).toEqual([{ event: 'token', data: { text: 'Hi' } }]);
  });

  it('should name unnamed events "message" and join multi-line data', async () => {
    fetchMock.mockResolvedValue(
      eventStreamResponse('id: 7\ndata: [1,\ndata:2]\n\n', 'event\n\n'),
    );

    const events = await collect(client.stream('/api/chat'));

    expect(events).toEqual([{ event: 'message', data: [1, 2] }]);
  });

  it('should drop an unfinished event at the end of the stream', async () => {
    fetchMock.mockResolvedValue(eventStreamResponse('data: {"text":"Hi"}\n'));

    expect(await collect(client.stream('/api/chat'))).toEqual([]);
  });

  it('should complete at once when the response has no body', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 200 }));

    expect(await collect(client.stream('/api/chat'))).toEqual([]);
  });

  it('should send the request options and ask for an event stream', async () => {
    fetchMock.mockResolvedValue(eventStreamResponse());

    await collect(
      client.stream('/api/chat', {
        method: 'POST',
        body: '{"text":"Why are leaves green?"}',
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(url).toBe('/api/chat');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"text":"Why are leaves green?"}');
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('should keep an Accept header the caller sets', async () => {
    fetchMock.mockResolvedValue(eventStreamResponse());

    await collect(
      client.stream('/api/chat', { headers: { Accept: 'text/plain' } }),
    );

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).get('Accept')).toBe('text/plain');
  });

  it('should abort the fetch on unsubscribe', async () => {
    let signal: AbortSignal | undefined;
    fetchMock.mockImplementation((_url: string, init: RequestInit) => {
      signal = init.signal ?? undefined;
      return new Promise<Response>(() => undefined);
    });

    const subscription = client.stream('/api/chat').subscribe();
    expect(signal?.aborted).toBe(false);

    subscription.unsubscribe();

    expect(signal?.aborted).toBe(true);
  });

  it('should not report an error after the caller unsubscribed', async () => {
    const error = vi.fn();
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise<Response>((_resolve, reject) =>
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('Aborted', 'AbortError')),
          ),
        ),
    );

    client.stream('/api/chat').subscribe({ error }).unsubscribe();
    await Promise.resolve();

    expect(error).not.toHaveBeenCalled();
  });

  it('should turn an error response into an ApiError', async () => {
    fetchMock.mockResolvedValue(
      Response.json(
        { status: 429, code: 'RATE_LIMITED', message: 'Too many requests' },
        { status: 429 },
      ),
    );

    await expect(collect(client.stream('/api/chat'))).rejects.toEqual({
      status: 429,
      code: 'RATE_LIMITED',
      message: 'Too many requests',
    });
  });

  it('should give a generic ApiError when the error body is not JSON', async () => {
    fetchMock.mockResolvedValue(new Response('Bad Gateway', { status: 502 }));

    await expect(collect(client.stream('/api/chat'))).rejects.toEqual(
      expect.objectContaining({ status: 502, code: 'INTERNAL_ERROR' }),
    );
  });

  it('should turn a network failure into code NETWORK', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(collect(client.stream('/api/chat'))).rejects.toEqual(
      expect.objectContaining({ status: 0, code: 'NETWORK' }),
    );
  });

  it('should report data that is not JSON as it is', async () => {
    fetchMock.mockResolvedValue(eventStreamResponse('data: not json\n\n'));

    await expect(collect(client.stream('/api/chat'))).rejects.toBeInstanceOf(
      SyntaxError,
    );
  });
});
