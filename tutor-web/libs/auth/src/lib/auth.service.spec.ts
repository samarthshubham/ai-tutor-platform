import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ApiError, User, UserApi } from '@tutor-web/api';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom, of, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const TARA: User = {
  id: 'u1',
  username: 'teacher1',
  displayName: 'Tara Teacher',
  email: 'teacher1@tutor.local',
  role: 'TEACHER',
  schoolId: 's1',
};

describe('AuthService', () => {
  function setup(options: {
    authenticated: boolean;
    me?: ReturnType<UserApi['me']>;
  }) {
    const oidc = {
      checkAuth: vi.fn(() =>
        of({ isAuthenticated: options.authenticated, userData: null }),
      ),
      authorize: vi.fn(),
      logoff: vi.fn<OidcSecurityService['logoff']>(() => of(null)),
      getAccessToken: vi.fn(() => of('token-1')),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OidcSecurityService, useValue: oidc },
        { provide: UserApi, useValue: { me: () => options.me ?? of(TARA) } },
      ],
    });
    return { oidc, auth: TestBed.inject(AuthService) };
  }

  beforeEach(() => sessionStorage.clear());

  it('should load the current user once the session is authenticated', async () => {
    const { auth } = setup({ authenticated: true });

    expect(auth.checking()).toBe(true);
    auth.start();

    expect(await firstValueFrom(auth.whenReady())).toBe(true);
    expect(auth.checking()).toBe(false);
    expect(auth.user()).toEqual(TARA);
    expect(auth.role()).toBe('TEACHER');
  });

  it('should report a signed-out visitor without loading a user', async () => {
    const { auth } = setup({ authenticated: false });

    expect(await firstValueFrom(auth.whenReady())).toBe(false);
    expect(auth.user()).toBeNull();
    expect(auth.role()).toBeNull();
  });

  it('should check the session only once for all callers', async () => {
    const { auth, oidc } = setup({ authenticated: true });

    auth.start();
    await firstValueFrom(auth.whenReady());
    await firstValueFrom(auth.whenReady());

    expect(oidc.checkAuth).toHaveBeenCalledTimes(1);
  });

  it('should keep the API error when the current user cannot be loaded', async () => {
    const failure: ApiError = {
      status: 503,
      code: 'UNAVAILABLE',
      message: 'Down',
    };
    const { auth } = setup({
      authenticated: true,
      me: throwError(() => failure),
    });

    expect(await firstValueFrom(auth.whenReady())).toBe(true);
    expect(auth.error()).toEqual(failure);
    expect(auth.user()).toBeNull();
  });

  it('should start sign-in once and remember where to return', () => {
    const { auth, oidc } = setup({ authenticated: false });
    TestBed.inject(Router);

    auth.login('/admin/users');
    auth.login('/other');

    expect(oidc.authorize).toHaveBeenCalledTimes(1);
    expect(auth.takeReturnUrl()).toBe('/admin/users');
    expect(auth.takeReturnUrl()).toBeNull();
  });

  it.each(['/', '//evil.example', 'https://evil.example'])(
    'should ignore the stored return url %s',
    (url) => {
      const { auth } = setup({ authenticated: false });
      sessionStorage.setItem('tp.returnUrl', url);

      expect(auth.takeReturnUrl()).toBeNull();
    },
  );

  it('should hand the access token to callers', async () => {
    const { auth } = setup({ authenticated: true });

    expect(await firstValueFrom(auth.accessToken())).toBe('token-1');
  });

  it('should forget the user and end the Keycloak session on sign out', async () => {
    const { auth, oidc } = setup({ authenticated: true });
    await firstValueFrom(auth.whenReady());

    auth.logout();

    expect(auth.user()).toBeNull();
    expect(oidc.logoff).toHaveBeenCalledTimes(1);
    expect(oidc.logoff.mock.calls[0][1]).toEqual(
      expect.objectContaining({ urlHandler: expect.any(Function) }),
    );
  });

  it('should reload a page restored from the back-forward cache', () => {
    const { auth } = setup({ authenticated: false });
    const reload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload,
    });

    auth.start();
    window.dispatchEvent(
      Object.assign(new Event('pageshow'), { persisted: true }),
    );

    expect(reload).toHaveBeenCalled();
    vi.restoreAllMocks();
  });
});
