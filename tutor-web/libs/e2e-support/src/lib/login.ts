import { demoPassword } from './demo-users';
import { stackUrl } from './stack-url';

interface Credentials {
  username: string;
  password: string;
}

function submitKeycloakForm({ username, password }: Credentials): void {
  cy.get('#username').type(username);
  cy.get('#password').type(password, { log: false });
  cy.get('#kc-login').click();
}

export function signInOnKeycloakPage(username: string): void {
  cy.origin(
    stackUrl('keycloakUrl'),
    { args: { username, password: demoPassword(username) } },
    submitKeycloakForm,
  );
}

export function loginAs(username: string): void {
  cy.session(username, () => {
    cy.visit('/');
    signInOnKeycloakPage(username);
    cy.get('[data-testid="user-menu"]').should('be.visible');
  });
}
