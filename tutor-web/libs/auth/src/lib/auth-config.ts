import {
  EnvironmentProviders,
  inject,
  provideAppInitializer,
} from '@angular/core';
import { provideAuth } from 'angular-auth-oidc-client';
import { AuthService } from './auth.service';

export interface AuthEnvironment {
  authority: string;
  clientId: string;
  scope: string;
}

export function provideTutorAuth(
  environment: AuthEnvironment,
): EnvironmentProviders[] {
  const origin = window.location.origin;
  return [
    provideAuth({
      config: {
        authority: environment.authority,
        clientId: environment.clientId,
        scope: environment.scope,
        responseType: 'code',
        redirectUrl: origin,
        postLogoutRedirectUri: origin,
        silentRenew: true,
        useRefreshToken: true,
        renewTimeBeforeTokenExpiresInSeconds: 60,
        ignoreNonceAfterRefresh: true,
        disableRefreshTokenOfflineAccessScopeWarning: true,
      },
    }),
    provideAppInitializer(() => {
      inject(AuthService).start();
    }),
  ];
}
