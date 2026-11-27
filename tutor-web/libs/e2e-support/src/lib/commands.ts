import { loginAs } from './login';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      loginAs(username: string): void;
    }
  }
}

export function registerCommands(): void {
  Cypress.Commands.add('loginAs', loginAs);
}
