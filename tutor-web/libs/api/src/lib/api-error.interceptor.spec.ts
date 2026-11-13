import {
  HttpClient,
  HttpRequest,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, throwError } from 'rxjs';
import { ApiError } from './api-error';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function failingRequest(): Promise<ApiError> {
    return firstValueFrom(http.get('/api/courses')).then(
      () => {
        throw new Error('expected the request to fail');
      },
      (error: ApiError) => error,
    );
  }

  it('should pass successful responses through', async () => {
    const response = firstValueFrom(http.get('/api/courses'));

    backend.expectOne('/api/courses').flush({ content: [] });

    expect(await response).toEqual({ content: [] });
  });

  it('should turn an API error body into an ApiError', async () => {
    const error = failingRequest();

    backend.expectOne('/api/courses').flush(
      {
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'Request is not valid',
        fields: { title: 'must not be blank' },
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(await error).toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Request is not valid',
      fields: { title: 'must not be blank' },
    });
  });

  it('should leave fields out when the body has none', async () => {
    const error = failingRequest();

    backend
      .expectOne('/api/courses')
      .flush(
        { status: 404, code: 'NOT_FOUND', message: 'Course not found' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(await error).toEqual({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Course not found',
    });
  });

  it('should turn a network failure into code NETWORK', async () => {
    const error = failingRequest();

    backend.expectOne('/api/courses').error(new ProgressEvent('error'));

    expect(await error).toEqual(
      expect.objectContaining({ status: 0, code: 'NETWORK' }),
    );
  });

  it.each([null, 'Bad Gateway', { status: 'DOWN' }])(
    'should give a generic error for an unexpected body %j',
    async (body) => {
      const error = failingRequest();

      backend
        .expectOne('/api/courses')
        .flush(body, { status: 502, statusText: 'Bad Gateway' });

      expect(await error).toEqual({
        status: 502,
        code: 'INTERNAL_ERROR',
        message: 'Something went wrong. Please try again.',
      });
    },
  );

  it('should rethrow errors that are not HTTP errors unchanged', async () => {
    const failure = new Error('boom');

    const result = TestBed.runInInjectionContext(() =>
      firstValueFrom(
        apiErrorInterceptor(new HttpRequest('GET', '/api/courses'), () =>
          throwError(() => failure),
        ),
      ),
    );

    await expect(result).rejects.toBe(failure);
  });
});
