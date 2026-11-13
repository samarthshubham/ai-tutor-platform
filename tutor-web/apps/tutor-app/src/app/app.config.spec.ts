import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('should turn HTTP failures into ApiError through the interceptor', async () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    const backend = TestBed.inject(HttpTestingController);

    const request = firstValueFrom(TestBed.inject(HttpClient).get('/api/me'));
    backend.expectOne('/api/me').error(new ProgressEvent('error'));

    await expect(request).rejects.toEqual(
      expect.objectContaining({ status: 0, code: 'NETWORK' }),
    );
  });
});
