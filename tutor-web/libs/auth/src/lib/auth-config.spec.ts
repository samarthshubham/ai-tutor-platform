import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideTutorAuth } from './auth-config';
import { AuthService } from './auth.service';

describe('provideTutorAuth', () => {
  it('should start the session check when the app initializes', async () => {
    const start = vi.spyOn(AuthService.prototype, 'start').mockReturnValue();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideTutorAuth({
          authority: 'http://localhost:8180/realms/tutor',
          clientId: 'tutor-web',
          scope: 'openid profile email',
        }),
      ],
    });

    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(start).toHaveBeenCalledTimes(1);
  });
});
