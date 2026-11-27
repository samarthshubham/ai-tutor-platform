import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import {
  apiRequestAs,
  signInOnKeycloakPage,
  stackUrl,
} from '@tutor-web/e2e-support';

const SIGN_IN_TIMEOUT_MS = 20_000;

Given('I open {string} signed out', (path: string) => {
  cy.visit(path);
});

Given('I am signed in as {string}', (username: string) => {
  cy.loginAs(username);
});

Given('I open {string}', (path: string) => {
  cy.visit(path);
  cy.get('[data-testid="user-menu"]', { timeout: SIGN_IN_TIMEOUT_MS }).should(
    'be.visible',
  );
});

Given('I watch the API call {string}', (path: string) => {
  cy.intercept('GET', path).as('watchedApiCall');
});

When('I sign in as {string} on the Keycloak page', (username: string) => {
  signInOnKeycloakPage(username);
});

When('I sign out', () => {
  cy.get('[data-testid="user-menu"]').click();
  cy.contains('button[role="menuitem"]', 'Sign out').click();
});

When('I go back in the browser', () => {
  cy.go('back');
});

Then('I am on the Keycloak sign-in page', () => {
  cy.origin(stackUrl('keycloakUrl'), () => {
    cy.get('#kc-login', { timeout: 20_000 }).should('be.visible');
  });
});

Then('I land on {string}', (route: string) => {
  cy.location('pathname', { timeout: SIGN_IN_TIMEOUT_MS }).should('eq', route);
});

Then(
  'the user menu shows {string} with role {string}',
  (name: string, role: string) => {
    cy.get('[data-testid="user-menu"]').should('contain.text', name);
    cy.get('[data-testid="user-menu"]').click();
    cy.get('[data-testid="user-role"]').should('have.text', role);
    cy.get('body').type('{esc}');
  },
);

Then('the navigation lists {string}', (items: string) => {
  const expected = items.split(',').map((item) => item.trim());
  cy.get('mat-nav-list[aria-label="Main navigation"] a').should(($links) => {
    expect($links.toArray().map((link) => link.textContent?.trim())).to.deep.eq(
      expected,
    );
  });
});

Then('I see the heading {string}', (heading: string) => {
  cy.get('h1').should('have.text', heading);
});

Then('no protected content is shown', () => {
  cy.location('origin').then((origin) => {
    if (origin === stackUrl('webUrl')) {
      cy.get('[data-testid="user-menu"]').should('not.exist');
      cy.get('mat-nav-list').should('not.exist');
    }
  });
});

Then('the watched API call carried a bearer token', () => {
  cy.wait('@watchedApiCall', { timeout: SIGN_IN_TIMEOUT_MS })
    .its('request.headers.authorization')
    .should('match', /^Bearer .+/);
});

Given('the demo users have signed in once', () => {
  ['admin1', 'parent1', 'student1'].forEach((username) => {
    apiRequestAs(username, { method: 'GET', path: '/api/me' })
      .its('status')
      .should('eq', 200);
  });
});

Given('{string} has no guardian links', (student: string) => {
  apiRequestAs<{ content: { id: string }[] }>('admin1', {
    method: 'GET',
    path: `/api/users?q=${student}`,
  })
    .its('body.content.0.id')
    .then((studentId) =>
      apiRequestAs<{ content: { id: string }[] }>('admin1', {
        method: 'GET',
        path: `/api/guardian-links?studentId=${studentId}`,
      }),
    )
    .then((response) => {
      response.body.content.forEach((link) => {
        apiRequestAs('admin1', {
          method: 'DELETE',
          path: `/api/guardian-links/${link.id}`,
        });
      });
    });
});

When('I filter users by role {string}', (role: string) => {
  cy.contains('mat-form-field', 'Role').find('mat-select').click();
  cy.contains('mat-option', role).click();
});

Then('the users table lists {string}', (name: string) => {
  cy.contains('table[aria-label="Users"] td', name).should('be.visible');
});

Then('the users table does not list {string}', (name: string) => {
  cy.contains('table[aria-label="Users"] td', name).should('not.exist');
});

When('I open the links of {string}', (name: string) => {
  cy.get(`button[aria-label="Manage links of ${name}"]`).click();
  cy.get('mat-dialog-container').should('be.visible');
});

When('I add {string} under {string}', (name: string, section: string) => {
  cy.get(`mat-dialog-container section[aria-label="${section}"]`).within(() => {
    cy.get('mat-select').click();
  });
  cy.contains('mat-option', name).click();
  cy.get(
    `mat-dialog-container section[aria-label="${section}"] .add-row button`,
  ).click();
});

Then('{string} lists {string}', (section: string, name: string) => {
  cy.get(`mat-dialog-container section[aria-label="${section}"] li`).should(
    'contain.text',
    name,
  );
});

When('I remove {string} from the links', (name: string) => {
  cy.get(`mat-dialog-container button[aria-label="Remove ${name}"]`).click();
});

Then('{string} lists nobody', (section: string) => {
  cy.get(`mat-dialog-container section[aria-label="${section}"] li`).should(
    'not.exist',
  );
});
