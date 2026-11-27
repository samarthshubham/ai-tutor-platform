import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { User } from './user';
import { UserApi } from './user-api';

const USER: User = {
  id: 'u1',
  username: 'teacher1',
  displayName: 'Tara Teacher',
  email: null,
  role: 'TEACHER',
  schoolId: 's1',
};

describe('UserApi', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    return {
      api: TestBed.inject(UserApi),
      backend: TestBed.inject(HttpTestingController),
    };
  }

  it('should read the current user', async () => {
    const { api, backend } = setup();

    const user = firstValueFrom(api.me());
    backend.expectOne('/api/me').flush(USER);

    expect(await user).toEqual(USER);
    backend.verify();
  });

  it('should send only the filters that are set', async () => {
    const { api, backend } = setup();

    const page = firstValueFrom(
      api.list({ role: 'PARENT', q: '', page: 2, size: 20 }),
    );
    const request = backend.expectOne((r) => r.url === '/api/users');
    request.flush({
      content: [USER],
      page: 2,
      size: 20,
      totalElements: 41,
      totalPages: 3,
    });

    expect(request.request.params.get('role')).toBe('PARENT');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.has('q')).toBe(false);
    expect((await page).content).toEqual([USER]);
    backend.verify();
  });
});
