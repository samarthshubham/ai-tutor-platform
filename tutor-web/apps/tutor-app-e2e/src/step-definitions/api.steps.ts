import {
  Before,
  Given,
  Then,
  When,
} from '@badeball/cypress-cucumber-preprocessor';
import { apiRequest, stackUrl, tokenFor } from '@tutor-web/e2e-support';

interface ScenarioState {
  token: string | undefined;
  response: Cypress.Response<unknown> | undefined;
}

const scenario: ScenarioState = { token: undefined, response: undefined };

function lastResponse(): Cypress.Response<unknown> {
  if (!scenario.response) throw new Error('No request was sent yet');
  return scenario.response;
}

function fieldOf(body: unknown, field: string): unknown {
  return field
    .split('.')
    .reduce<unknown>(
      (value, key) =>
        typeof value === 'object' && value !== null
          ? (value as Record<string, unknown>)[key]
          : undefined,
      body,
    );
}

Before(() => {
  scenario.token = undefined;
  scenario.response = undefined;
});

Given('I am not signed in to the API', () => {
  scenario.token = undefined;
});

Given('I am signed in to the API as {string}', (username: string) => {
  tokenFor(username).then((token) => {
    scenario.token = token;
  });
});

Given('the API runs the fake AI profile', () => {
  apiRequest({
    method: 'GET',
    path: '/actuator/info',
    token: scenario.token,
  }).then((response) => {
    expect(
      fieldOf(response.body, 'ai.chat-provider'),
      'chat provider in /actuator/info; start the stack with AI_PROFILE=fake-ai so no test calls a real model',
    ).to.eq('fake');
  });
});

When('I call {word} {string}', (method: Cypress.HttpMethod, path: string) => {
  apiRequest({ method, path, token: scenario.token }).then((response) => {
    scenario.response = response;
  });
});

When('I request the web app root', () => {
  cy.request({ url: `${stackUrl('webUrl')}/`, failOnStatusCode: false }).then(
    (response) => {
      scenario.response = response;
    },
  );
});

Then('the response status is {int}', (status: number) => {
  expect(lastResponse().status, 'response status').to.eq(status);
});

Then(
  'the response field {string} is {string}',
  (field: string, value: string) => {
    expect(fieldOf(lastResponse().body, field), field).to.eq(value);
  },
);

Then('the response field {string} is not empty', (field: string) => {
  const value = fieldOf(lastResponse().body, field);
  expect(value, field).to.be.a('string');
  expect((value as string).trim(), field).to.have.length.greaterThan(0);
});

Then('the response body contains {string}', (text: string) => {
  expect(String(lastResponse().body), 'response body').to.contain(text);
});
