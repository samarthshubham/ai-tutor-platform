import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  ApiError,
  GuardianLink,
  Page,
  RelationshipApi,
  TutorAssignment,
  User,
  UserApi,
} from '@tutor-web/api';
import { of, throwError } from 'rxjs';
import { UserLinksDialog } from './user-links-dialog';

function user(id: string, displayName: string, role: User['role']): User {
  return {
    id,
    username: id,
    displayName,
    email: null,
    role,
    schoolId: 's1',
  };
}

const SAM = user('sam', 'Sam Student', 'STUDENT');
const SARA = user('sara', 'Sara Student', 'STUDENT');
const PAT = user('pat', 'Pat Parent', 'PARENT');
const PRIYA = user('priya', 'Priya Parent', 'PARENT');
const UMA = user('uma', 'Uma Tutor', 'TUTOR');

const page = <T>(content: T[]): Page<T> => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
});

const CONFLICT: ApiError = {
  status: 409,
  code: 'CONFLICT',
  message: 'That parent is already linked to this student.',
};

describe('UserLinksDialog', () => {
  function setup(
    subject: User,
    options: { guardianLinks?: GuardianLink[] } = {},
  ) {
    let guardianLinks = options.guardianLinks ?? [];
    let assignments: TutorAssignment[] = [];
    const relationships = {
      listGuardianLinks: vi.fn(() => of(page(guardianLinks))),
      createGuardianLink: vi.fn(
        (request: { parentId: string; studentId: string }) => {
          const link = { id: 'new-link', ...request };
          guardianLinks = [...guardianLinks, link];
          return of(link);
        },
      ),
      deleteGuardianLink: vi.fn((id: string) => {
        guardianLinks = guardianLinks.filter((link) => link.id !== id);
        return of(undefined);
      }),
      listTutorAssignments: vi.fn(() => of(page(assignments))),
      createTutorAssignment: vi.fn(
        (request: { tutorId: string; studentId: string }) => {
          const assignment = { id: 'new-assignment', ...request };
          assignments = [...assignments, assignment];
          return of(assignment);
        },
      ),
      deleteTutorAssignment: vi.fn((id: string) => {
        assignments = assignments.filter((a) => a.id !== id);
        return of(undefined);
      }),
    };
    const everyone = [SAM, SARA, PAT, PRIYA, UMA];
    const users = {
      list: vi.fn(({ role }: { role: User['role'] }) =>
        of(page(everyone.filter((u) => u.role === role))),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { user: subject } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: RelationshipApi, useValue: relationships },
        { provide: UserApi, useValue: users },
      ],
    });
    return { relationships, users };
  }

  async function render() {
    const fixture = TestBed.createComponent(UserLinksDialog);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const sectionTitles = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('section h3')).map((h) => h.textContent);
  const names = (el: HTMLElement, section: string) =>
    Array.from(
      el.querySelectorAll(
        `section[aria-label="${section}"] li > span:first-child`,
      ),
    ).map((span) => span.textContent);
  const button = (el: HTMLElement, label: string) =>
    el.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);

  async function choose(
    fixture: Awaited<ReturnType<typeof render>>['fixture'],
    el: HTMLElement,
    section: string,
    option: string,
  ) {
    el.querySelector<HTMLElement>(
      `section[aria-label="${section}"] mat-select`,
    )?.click();
    await fixture.whenStable();
    Array.from(document.querySelectorAll<HTMLElement>('mat-option'))
      .find((o) => o.textContent?.trim() === option)
      ?.click();
    await fixture.whenStable();
  }

  it('should show parents and tutors for a student', async () => {
    const { relationships } = setup(SAM, {
      guardianLinks: [{ id: 'l1', parentId: 'pat', studentId: 'sam' }],
    });
    const { el } = await render();

    expect(sectionTitles(el)).toEqual(['Parents and guardians', 'Tutors']);
    expect(names(el, 'Parents and guardians')).toEqual(['Pat Parent']);
    expect(relationships.listGuardianLinks).toHaveBeenCalledWith(
      expect.objectContaining({ studentId: 'sam' }),
    );
  });

  it('should show the children of a parent', async () => {
    const { relationships } = setup(PAT, {
      guardianLinks: [{ id: 'l1', parentId: 'pat', studentId: 'sam' }],
    });
    const { el } = await render();

    expect(sectionTitles(el)).toEqual(['Children']);
    expect(names(el, 'Children')).toEqual(['Sam Student']);
    expect(relationships.listGuardianLinks).toHaveBeenCalledWith(
      expect.objectContaining({ parentId: 'pat' }),
    );
  });

  it('should show the assigned students of a tutor', async () => {
    const { relationships } = setup(UMA);
    const { el } = await render();

    expect(sectionTitles(el)).toEqual(['Assigned students']);
    expect(el.textContent).toContain('None yet.');
    expect(relationships.listTutorAssignments).toHaveBeenCalledWith(
      expect.objectContaining({ tutorId: 'uma' }),
    );
  });

  it('should add a guardian link and list it', async () => {
    const { relationships } = setup(SAM);
    const { fixture, el } = await render();
    const add = () =>
      el.querySelector<HTMLButtonElement>(
        'section[aria-label="Parents and guardians"] .add-row button',
      );
    expect(add()?.disabled).toBe(true);

    await choose(fixture, el, 'Parents and guardians', 'Pat Parent');
    add()?.click();
    await fixture.whenStable();

    expect(relationships.createGuardianLink).toHaveBeenCalledWith({
      parentId: 'pat',
      studentId: 'sam',
    });
    expect(names(el, 'Parents and guardians')).toEqual(['Pat Parent']);
  });

  it('should add a tutor assignment', async () => {
    const { relationships } = setup(SAM);
    const { fixture, el } = await render();

    await choose(fixture, el, 'Tutors', 'Uma Tutor');
    el.querySelector<HTMLButtonElement>(
      'section[aria-label="Tutors"] .add-row button',
    )?.click();
    await fixture.whenStable();

    expect(relationships.createTutorAssignment).toHaveBeenCalledWith({
      tutorId: 'uma',
      studentId: 'sam',
    });
    expect(names(el, 'Tutors')).toEqual(['Uma Tutor']);
  });

  it('should remove a link', async () => {
    const { relationships } = setup(SAM, {
      guardianLinks: [{ id: 'l1', parentId: 'pat', studentId: 'sam' }],
    });
    const { fixture, el } = await render();

    button(el, 'Remove Pat Parent')?.click();
    await fixture.whenStable();

    expect(relationships.deleteGuardianLink).toHaveBeenCalledWith('l1');
    expect(el.textContent).toContain('None yet.');
  });

  it('should remove a tutor assignment', async () => {
    const { relationships } = setup(UMA);
    const { fixture, el } = await render();
    await choose(fixture, el, 'Assigned students', 'Sam Student');
    el.querySelector<HTMLButtonElement>('.add-row button')?.click();
    await fixture.whenStable();

    button(el, 'Remove Sam Student')?.click();
    await fixture.whenStable();

    expect(relationships.deleteTutorAssignment).toHaveBeenCalledWith(
      'new-assignment',
    );
  });

  it('should only offer people who are not linked yet', async () => {
    setup(SAM, {
      guardianLinks: [{ id: 'l1', parentId: 'pat', studentId: 'sam' }],
    });
    const { fixture, el } = await render();

    el.querySelector<HTMLElement>(
      'section[aria-label="Parents and guardians"] mat-select',
    )?.click();
    await fixture.whenStable();

    expect(
      Array.from(document.querySelectorAll('mat-option')).map((o) =>
        o.textContent?.trim(),
      ),
    ).toEqual(['Priya Parent']);
  });

  it('should show an API error from adding in the dialog', async () => {
    const { relationships } = setup(SAM);
    relationships.createGuardianLink.mockReturnValueOnce(
      throwError(() => CONFLICT),
    );
    const { fixture, el } = await render();

    await choose(fixture, el, 'Parents and guardians', 'Pat Parent');
    el.querySelector<HTMLButtonElement>(
      'section[aria-label="Parents and guardians"] .add-row button',
    )?.click();
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      CONFLICT.message,
    );
    expect(
      el.querySelector<HTMLButtonElement>('.add-row button')?.disabled,
    ).toBe(false);
  });

  it('should show an API error from removing in the dialog', async () => {
    const { relationships } = setup(SAM, {
      guardianLinks: [{ id: 'l1', parentId: 'pat', studentId: 'sam' }],
    });
    relationships.deleteGuardianLink.mockReturnValueOnce(
      throwError(() => CONFLICT),
    );
    const { fixture, el } = await render();

    button(el, 'Remove Pat Parent')?.click();
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      CONFLICT.message,
    );
    expect(names(el, 'Parents and guardians')).toEqual(['Pat Parent']);
  });

  it('should show the error banner when loading fails and reload on retry', async () => {
    const { relationships } = setup(SAM);
    relationships.listGuardianLinks.mockReturnValueOnce(
      throwError(() => CONFLICT),
    );
    const { fixture, el } = await render();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      CONFLICT.message,
    );

    el.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(sectionTitles(el)).toEqual(['Parents and guardians', 'Tutors']);
  });

  it('should show an error when the links cannot be refreshed after a change', async () => {
    const { relationships } = setup(SAM);
    const { fixture, el } = await render();
    await choose(fixture, el, 'Parents and guardians', 'Pat Parent');
    relationships.listGuardianLinks.mockReturnValueOnce(
      throwError(() => CONFLICT),
    );

    el.querySelector<HTMLButtonElement>(
      'section[aria-label="Parents and guardians"] .add-row button',
    )?.click();
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      CONFLICT.message,
    );
  });
});
