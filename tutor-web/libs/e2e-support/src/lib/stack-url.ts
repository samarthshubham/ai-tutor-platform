const DEFAULT_URLS = {
  apiUrl: 'http://localhost:9190',
  keycloakUrl: 'http://localhost:8180',
  webUrl: 'http://localhost:4300',
} as const;

export type StackUrl = keyof typeof DEFAULT_URLS;

export function stackUrl(name: StackUrl): string {
  const configured: unknown = Cypress.env(name);
  return typeof configured === 'string' && configured !== ''
    ? configured
    : DEFAULT_URLS[name];
}
