import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiError, User, UserApi } from '@tutor-web/api';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import {
  catchError,
  defer,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
} from 'rxjs';

const RETURN_URL_KEY = 'tp.returnUrl';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly userApi = inject(UserApi);
  private readonly router = inject(Router);
  private readonly window = inject(DOCUMENT).defaultView;

  private readonly currentUser = signal<User | null>(null);
  private readonly sessionCheck = signal(true);
  private readonly sessionError = signal<ApiError | null>(null);
  private signInStarted = false;

  readonly user = this.currentUser.asReadonly();
  readonly role = computed(() => this.currentUser()?.role ?? null);
  readonly checking = this.sessionCheck.asReadonly();
  readonly error = this.sessionError.asReadonly();

  private readonly ready$: Observable<boolean> = defer(() =>
    this.oidc.checkAuth(),
  ).pipe(
    switchMap(({ isAuthenticated }) =>
      isAuthenticated ? this.loadUser() : of(false),
    ),
    tap(() => this.sessionCheck.set(false)),
    shareReplay(1),
  );

  start(): void {
    this.window?.addEventListener('pageshow', (event) => {
      if (event.persisted) {
        this.window?.location.reload();
      }
    });
    this.ready$.subscribe();
  }

  whenReady(): Observable<boolean> {
    return this.ready$;
  }

  accessToken(): Observable<string> {
    return this.oidc.getAccessToken();
  }

  login(returnUrl: string = this.router.url): void {
    if (this.signInStarted) {
      return;
    }
    this.signInStarted = true;
    this.window?.sessionStorage.setItem(RETURN_URL_KEY, returnUrl);
    this.oidc.authorize();
  }

  logout(): void {
    this.currentUser.set(null);
    this.oidc
      .logoff(undefined, {
        urlHandler: (url) => this.window?.location.assign(url),
      })
      .subscribe();
  }

  takeReturnUrl(): string | null {
    const url = this.window?.sessionStorage.getItem(RETURN_URL_KEY) ?? null;
    this.window?.sessionStorage.removeItem(RETURN_URL_KEY);
    return url !== null &&
      url.length > 1 &&
      url.startsWith('/') &&
      !url.startsWith('//')
      ? url
      : null;
  }

  private loadUser(): Observable<boolean> {
    return this.userApi.me().pipe(
      tap((user) => this.currentUser.set(user)),
      map(() => true),
      catchError((error: ApiError) => {
        this.sessionError.set(error);
        return of(true);
      }),
    );
  }
}
