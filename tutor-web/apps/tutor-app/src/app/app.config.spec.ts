import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '@tutor-web/auth';
import { firstValueFrom, of } from 'rxjs';
import { appConfig } from './app.config';

describe('appConfig', () => {
  function setup() {
    const auth = {
      start: vi.fn(),
      accessToken: () => of('token-1'),
      login: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    return TestBed.inject(HttpTestingController);
  }

  it('should turn HTTP failures into ApiError through the interceptor', async () => {
    const backend = setup();

    const request = firstValueFrom(TestBed.inject(HttpClient).get('/api/me'));
    backend.expectOne('/api/me').error(new ProgressEvent('error'));

    await expect(request).rejects.toEqual(
      expect.objectContaining({ status: 0, code: 'NETWORK' }),
    );
  });

  it('should add the bearer token to API calls', () => {
    const backend = setup();

    TestBed.inject(HttpClient).get('/api/me').subscribe();

    expect(
      backend.expectOne('/api/me').request.headers.get('Authorization'),
    ).toBe('Bearer token-1');
  });
});
