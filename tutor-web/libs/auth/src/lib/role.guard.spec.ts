import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Role } from '@tutor-web/api';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { AuthService } from './auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  function run(
    guard: ReturnType<typeof roleGuard>,
    state: { authenticated: boolean; role: Role | null },
  ) {
    const auth = {
      whenReady: () => of(state.authenticated),
      role: () => state.role,
      login: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    const result = TestBed.runInInjectionContext(() =>
      guard(
        {} as ActivatedRouteSnapshot,
        {
          url: '/admin/users',
        } as RouterStateSnapshot,
      ),
    );
    return {
      auth,
      result: firstValueFrom(result as Observable<boolean | UrlTree>),
    };
  }

  it('should start sign-in and keep the requested url when signed out', async () => {
    const { auth, result } = run(roleGuard('ADMIN'), {
      authenticated: false,
      role: null,
    });

    expect(await result).toBe(false);
    expect(auth.login).toHaveBeenCalledWith('/admin/users');
  });

  it('should let a matching role through', async () => {
    const { result } = run(roleGuard('ADMIN', 'TEACHER'), {
      authenticated: true,
      role: 'TEACHER',
    });

    expect(await result).toBe(true);
  });

  it('should send another role to the forbidden page', async () => {
    const { result } = run(roleGuard('ADMIN'), {
      authenticated: true,
      role: 'TEACHER',
    });

    expect((await result).toString()).toBe('/forbidden');
  });

  it('should let any signed-in role through when no roles are listed', async () => {
    const { result } = run(roleGuard(), {
      authenticated: true,
      role: 'PARENT',
    });

    expect(await result).toBe(true);
  });

  it('should hold the navigation when the user could not be loaded', async () => {
    const { result } = run(roleGuard('ADMIN'), {
      authenticated: true,
      role: null,
    });

    expect(await result).toBe(false);
    expect(isObservable(result)).toBe(false);
  });
});
