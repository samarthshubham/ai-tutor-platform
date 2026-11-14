import { stackUrl } from './stack-url';
import { tokenFor } from './token';

export interface ApiRequest {
  method: Cypress.HttpMethod;
  path: string;
  body?: Cypress.RequestBody;
  token?: string;
}

export function apiRequest<T = unknown>({
  method,
  path,
  body,
  token,
}: ApiRequest): Cypress.Chainable<Cypress.Response<T>> {
  return cy.request<T>({
    method,
    url: `${stackUrl('apiUrl')}${path}`,
    body,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
}

export function apiRequestAs<T = unknown>(
  username: string,
  request: Omit<ApiRequest, 'token'>,
): Cypress.Chainable<Cypress.Response<T>> {
  return tokenFor(username).then((token) =>
    apiRequest<T>({ ...request, token }),
  );
}
