import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HealthApi } from './health-api';

describe('HealthApi', () => {
  it('should read the actuator health endpoint', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const backend = TestBed.inject(HttpTestingController);

    const health = firstValueFrom(TestBed.inject(HealthApi).get());
    const request = backend.expectOne('/actuator/health');
    request.flush({ status: 'UP' });

    expect(request.request.method).toBe('GET');
    expect(await health).toEqual({ status: 'UP' });
    backend.verify();
  });
});
