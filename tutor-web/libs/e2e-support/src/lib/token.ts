import { demoPassword } from './demo-users';
import { stackUrl } from './stack-url';

interface TokenResponse {
  access_token: string;
  expires_in: number;
}

const EXPIRY_MARGIN_MS = 30_000;
const cachedTokens = new Map<string, { token: string; expiresAt: number }>();

export function clearTokenCache(): void {
  cachedTokens.clear();
}

export function tokenFor(username: string): Cypress.Chainable<string> {
  const cached = cachedTokens.get(username);
  if (cached && cached.expiresAt > Date.now()) {
    return cy.wrap(cached.token, { log: false });
  }
  return cy
    .request<TokenResponse>({
      method: 'POST',
      url: `${stackUrl('keycloakUrl')}/realms/tutor/protocol/openid-connect/token`,
      form: true,
      body: {
        grant_type: 'password',
        client_id: 'tutor-cli',
        username,
        password: demoPassword(username),
      },
      log: false,
    })
    .then(({ body }) => {
      cachedTokens.set(username, {
        token: body.access_token,
        expiresAt: Date.now() + body.expires_in * 1000 - EXPIRY_MARGIN_MS,
      });
      return body.access_token;
    });
}
