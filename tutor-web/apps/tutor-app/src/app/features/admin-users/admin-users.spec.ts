import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ApiError, Page, User, UserApi } from '@tutor-web/api';
import { Observable, of, Subject, throwError } from 'rxjs';
import { AdminUsers } from './admin-users';
import { UserLinksDialog } from './user-links-dialog';

function user(
  id: string,
  displayName: string,
  role: User['role'],
  username = displayName.toLowerCase(),
): User {
  return { id, username, displayName, email: null, role, schoolId: 's1' };
}

const SAM = user('s1', 'Sam Student', 'STUDENT', 'student1');
const TARA = user('t1', 'Tara Teacher', 'TEACHER', 'teacher1');

function pageOf(content: User[], totalElements = content.length): Page<User> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements,
    totalPages: 1,
  };
}

const FAILURE: ApiError = {
  status: 500,
  code: 'INTERNAL_ERROR',
  message: 'Something went wrong. Please try again.',
};

describe('AdminUsers', () => {
  async function render(...responses: Observable<Page<User>>[]) {
    const list = vi.fn();
    responses.forEach((response) => list.mockReturnValueOnce(response));
    const open = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: UserApi, useValue: { list } },
        { provide: MatDialog, useValue: { open } },
      ],
    });
    const fixture = TestBed.createComponent(AdminUsers);
    await fixture.whenStable();
    return { fixture, list, open, el: fixture.nativeElement as HTMLElement };
  }

  const rows = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('tr[mat-row]')).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) =>
        cell.textContent?.trim(),
      ),
    );

  it('should show a loading state while users load', async () => {
    const { el } = await render(new Subject<Page<User>>());

    expect(el.querySelector('tp-loading')).not.toBeNull();
    expect(el.querySelector('table')).toBeNull();
  });

  it('should list the first page of users with their role', async () => {
    const { el, list } = await render(of(pageOf([SAM, TARA])));

    expect(list).toHaveBeenCalledWith({
      role: undefined,
      q: '',
      page: 0,
      size: 20,
    });
    expect(rows(el)).toEqual([
      ['Sam Student', 'student1', 'Student', 'Manage links'],
      ['Tara Teacher', 'teacher1', 'Teacher', ''],
    ]);
  });

  it('should show the empty state when nobody matches', async () => {
    const { el } = await render(of(pageOf([])));

    expect(el.querySelector('tp-empty-state')?.textContent).toContain(
      'No users found',
    );
    expect(el.querySelector('table')).toBeNull();
  });

  it('should show the error banner and load again on retry', async () => {
    const { fixture, list, el } = await render(
      throwError(() => FAILURE),
      of(pageOf([SAM])),
    );
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      FAILURE.message,
    );

    el.querySelector<HTMLButtonElement>('[role="alert"] button')?.click();
    await fixture.whenStable();

    expect(list).toHaveBeenCalledTimes(2);
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(rows(el)).toHaveLength(1);
  });

  it('should filter by role and go back to the first page', async () => {
    const { fixture, list } = await render(
      of(pageOf([SAM, TARA])),
      of(pageOf([SAM])),
    );

    const component = fixture.componentInstance as unknown as {
      filters: AdminUsers['filters' & keyof AdminUsers];
    };
    (
      component.filters as unknown as {
        controls: { role: { setValue(value: string): void } };
      }
    ).controls.role.setValue('STUDENT');
    await fixture.whenStable();

    expect(list).toHaveBeenLastCalledWith({
      role: 'STUDENT',
      q: '',
      page: 0,
      size: 20,
    });
  });

  it('should search once the typing pauses', async () => {
    const { fixture, list, el } = await render(
      of(pageOf([SAM, TARA])),
      of(pageOf([SAM])),
    );
    const input = el.querySelector<HTMLInputElement>('input[type="search"]');

    if (!input) throw new Error('search input missing');
    input.value = ' sam ';
    input.dispatchEvent(new Event('input'));
    expect(list).toHaveBeenCalledTimes(1);
    await new Promise((resolve) => setTimeout(resolve, 400));
    await fixture.whenStable();

    expect(list).toHaveBeenLastCalledWith({
      role: undefined,
      q: 'sam',
      page: 0,
      size: 20,
    });
  });

  it('should load the requested page from the paginator', async () => {
    const { fixture, list, el } = await render(
      of(pageOf([SAM], 45)),
      of(pageOf([TARA], 45)),
    );

    el.querySelector<HTMLButtonElement>(
      'button.mat-mdc-paginator-navigation-next',
    )?.click();
    await fixture.whenStable();

    expect(list).toHaveBeenLastCalledWith({
      role: undefined,
      q: '',
      page: 1,
      size: 20,
    });
  });

  it('should open the links dialog only for students, parents and tutors', async () => {
    const { el, open } = await render(of(pageOf([SAM, TARA])));

    const buttons = el.querySelectorAll<HTMLButtonElement>(
      'button[aria-label^="Manage links of"]',
    );
    expect(buttons).toHaveLength(1);
    buttons[0].click();

    expect(open).toHaveBeenCalledWith(
      UserLinksDialog,
      expect.objectContaining({ data: { user: SAM } }),
    );
  });
});
