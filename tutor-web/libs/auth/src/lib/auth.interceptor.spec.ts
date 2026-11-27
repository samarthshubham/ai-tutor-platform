import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  function setup(token = 'token-1') {
    const auth = { accessToken: vi.fn(() => of(token)), login: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    return {
      auth,
      http: TestBed.inject(HttpClient),
      backend: TestBed.inject(HttpTestingController),
    };
  }

  it('should add the bearer token to API calls', () => {
    const { http, backend } = setup();

    http.get('/api/me').subscribe();

    expect(
      backend.expectOne('/api/me').request.headers.get('Authorization'),
    ).toBe('Bearer token-1');
  });

  it('should not send the token to other origins or non-API paths', () => {
    const { http, backend } = setup();

    http.get('https://other.example/api/me').subscribe();
    http.get('/actuator/health').subscribe();

    expect(
      backend
        .expectOne('https://other.example/api/me')
        .request.headers.has('Authorization'),
    ).toBe(false);
    expect(
      backend
        .expectOne('/actuator/health')
        .request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('should send the call without a header when there is no token', () => {
    const { http, backend } = setup('');

    http.get('/api/me').subscribe();

    expect(
      backend.expectOne('/api/me').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('should start sign-in when the API answers 401', async () => {
    const { http, backend, auth } = setup();

    const result = firstValueFrom(http.get('/api/users'));
    backend
      .expectOne('/api/users')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toEqual(
      expect.objectContaining({ status: 401 }),
    );
    expect(auth.login).toHaveBeenCalledTimes(1);
  });

  it('should leave other errors alone', async () => {
    const { http, backend, auth } = setup();

    const result = firstValueFrom(http.get('/api/users'));
    backend
      .expectOne('/api/users')
      .flush(null, { status: 403, statusText: 'Forbidden' });

    await expect(result).rejects.toEqual(
      expect.objectContaining({ status: 403 }),
    );
    expect(auth.login).not.toHaveBeenCalled();
  });
});
