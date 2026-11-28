import {
  After,
  Before,
  Given,
  Then,
  When,
} from '@badeball/cypress-cucumber-preprocessor';
import { apiRequestAs, createRunPrefix } from '@tutor-web/e2e-support';

let courseTitle: string;
let lessonTitle: string;
let courseId: string | undefined;
let lessonId: string | undefined;
let studentId: string | undefined;
const topic = 'Plant growth';
const preview = 'A plant needs light';
const revision = 'Plants use light to make sugars';
const comment = 'Explain what the plant makes.';
let consoleErrors: string[];

Before({ tags: '@ui' }, () => {
  const prefix = createRunPrefix();
  courseTitle = `${prefix} Science`;
  lessonTitle = `${prefix} Plant lesson`;
  courseId = lessonId = studentId = undefined;
  consoleErrors = [];
});

function visit(path: string): void {
  cy.visit(path, {
    onBeforeLoad(window) {
      const originalError = window.console.error.bind(window.console);
      cy.stub(window.console, 'error').callsFake((...messages: unknown[]) => {
        consoleErrors.push(messages.map(String).join(' '));
        originalError(...messages);
      });
    },
  });
}

function label(name: string, value: string): void {
  const control = () =>
    cy.contains('mat-form-field', name).find('input, textarea');
  control().focus();
  control().clear();
  control().type(value);
}

function button(name: string): Cypress.Chainable<JQuery<HTMLElement>> {
  return cy.contains('button', new RegExp(`^\\s*${name}\\s*$`));
}

Given('{string} has registered with the tutor app', (username: string) => {
  apiRequestAs<{ id: string }>(username, { method: 'GET', path: '/api/me' })
    .its('body.id')
    .then((id) => {
      studentId = id;
    });
});

When('I create a course for this lesson', () => {
  visit('/courses');
  cy.intercept('POST', '/api/courses').as('createCourse');
  button('New course').click();
  label('Course title', courseTitle);
  label('Subject', 'Science');
  label('Grade level', '8');
  button('Create course').click();
  cy.wait('@createCourse')
    .its('response.body.id')
    .then((id: string) => {
      courseId = id;
    });
  cy.contains('h1', courseTitle).should('be.visible');
});

When('I add a topic and enroll {string}', (name: string) => {
  cy.contains('[role="tab"]', 'Topics').click();
  label('New topic name', topic);
  button('Add topic').click();
  cy.contains('li', topic).should('be.visible');
  cy.contains('[role="tab"]', 'Students').click();
  label('Student name or username', 'student1');
  button('Find students').click();
  cy.get(`button[aria-label="Enroll ${name}"]`).click();
  cy.get('ul[aria-label="Enrolled students"]').should('contain.text', name);
  cy.contains('[role="tab"]', 'Lessons').click();
});

When('I write and reload the lesson draft', () => {
  cy.contains('a', 'New lesson').click();
  label('Lesson title', lessonTitle);
  cy.contains('mat-form-field', 'Topic').find('mat-select').click();
  cy.contains('mat-option', topic).click();
  label('Lesson Markdown', `## ${preview}`);
  cy.contains('section[aria-labelledby="preview-title"] h2', preview).should(
    'be.visible',
  );
  cy.intercept('POST', '/api/courses/*/lessons').as('createLesson');
  button('Save draft').click();
  cy.wait('@createLesson')
    .its('response.body.id')
    .then((id: string) => {
      lessonId = id;
      cy.location('pathname').should('eq', `/lessons/${id}/edit`);
      visit(`/lessons/${id}/edit`);
    });
  cy.get('input[formControlName="title"]').should('have.value', lessonTitle);
  cy.get('textarea[formControlName="bodyMarkdown"]').should(
    'have.value',
    `## ${preview}`,
  );
});

When('I submit the lesson for review', () => {
  button('Submit for review').click();
  cy.contains('mat-chip', 'IN_REVIEW').should('be.visible');
});

When('I send the lesson back for changes', () => {
  visit('/admin/review');
  cy.contains('mat-card', lessonTitle).within(() =>
    button('Request changes').click(),
  );
  label('Reviewer comment', comment);
  button('Send back').click();
  cy.contains('mat-card', lessonTitle).should('not.exist');
});

When('I revise the draft using the review comment', () => {
  cy.then(() => visit(`/lessons/${lessonId}/edit`));
  cy.contains('p', comment).should('be.visible');
  label('Lesson Markdown', `## ${revision}`);
  button('Save draft').click();
  cy.contains('[role="status"]', 'Draft saved').should('be.visible');
});

When('I publish the reviewed lesson', () => {
  visit('/admin/review');
  cy.contains('mat-card', lessonTitle).within(() => button('Publish').click());
  cy.contains('mat-card', lessonTitle).should('not.exist');
});

Then('I can read the published lesson in my course', () => {
  visit('/courses');
  cy.contains('a', courseTitle).click();
  cy.contains('a', lessonTitle).click();
  cy.contains('article h2', revision).should('be.visible');
  cy.contains('button', 'Archive lesson').should('not.exist');
  cy.then(() => expect(consoleErrors, 'browser console errors').to.be.empty);
});

After({ tags: '@ui' }, () => {
  if (lessonId)
    apiRequestAs('teacher1', {
      method: 'POST',
      path: `/api/lessons/${lessonId}/archive`,
    });
  if (courseId && studentId)
    apiRequestAs('teacher1', {
      method: 'DELETE',
      path: `/api/courses/${courseId}/enrollments/${studentId}`,
    });
  if (courseId)
    apiRequestAs('teacher1', {
      method: 'POST',
      path: `/api/courses/${courseId}/archive`,
    });
});
