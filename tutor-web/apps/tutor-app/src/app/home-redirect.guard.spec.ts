import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Route,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Role } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { firstValueFrom, Observable, of } from 'rxjs';
import { homeRedirectGuard } from './home-redirect.guard';

describe('homeRedirectGuard', () => {
  function run(options: {
    authenticated: boolean;
    role: Role | null;
    returnUrl?: string | null;
    routes?: Route[];
  }) {
    const auth = {
      whenReady: () => of(options.authenticated),
      role: () => options.role,
      login: vi.fn(),
      takeReturnUrl: () => options.returnUrl ?? null,
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(options.routes ?? []),
        { provide: AuthService, useValue: auth },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      homeRedirectGuard(
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );
    return {
      auth,
      result: firstValueFrom(result as Observable<boolean | UrlTree>),
    };
  }

  it('should start sign-in when nobody is signed in', async () => {
    const { auth, result } = run({ authenticated: false, role: null });

    expect(await result).toBe(false);
    expect(auth.login).toHaveBeenCalledWith('/');
  });

  it('should return to the link that was opened before sign-in', async () => {
    const { result } = run({
      authenticated: true,
      role: 'ADMIN',
      returnUrl: '/admin/users',
    });

    expect((await result).toString()).toBe('/admin/users');
  });

  it("should open the role's first navigation item", async () => {
    const { result } = run({
      authenticated: true,
      role: 'ADMIN',
      routes: [{ path: 'admin/users', redirectTo: '' }],
    });

    expect((await result).toString()).toBe('/admin/users');
  });

  it('should open Home while the role has no screen yet', async () => {
    const { result } = run({ authenticated: true, role: 'TEACHER' });

    expect((await result).toString()).toBe('/home');
  });

  it('should hold the navigation when the user could not be loaded', async () => {
    const { result } = run({ authenticated: true, role: null });

    expect(await result).toBe(false);
  });
});
