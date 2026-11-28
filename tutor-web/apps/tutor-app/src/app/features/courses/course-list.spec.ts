import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CourseApi, Role, UserApi } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { NEVER, of, throwError } from 'rxjs';
import { CourseList } from './course-list';

const course = {
  id: 'c',
  title: 'Plants',
  subject: 'Science',
  teacherName: 'Tara',
  gradeLevel: 8,
  description: 'Plants and growth',
  status: 'ACTIVE',
  topics: [],
};
function render(role: Role) {
  const api = {
    list: vi.fn().mockReturnValue(of({ content: [], page: 0, totalPages: 0 })),
    create: vi.fn().mockReturnValue(of(course)),
  };
  const users = {
    list: vi.fn().mockReturnValue(
      of({
        content: [{ id: 't', displayName: 'Tara', username: 'teacher1' }],
      }),
    ),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { role: signal(role) } },
      { provide: CourseApi, useValue: api },
      { provide: UserApi, useValue: users },
    ],
  });
  const navigate = vi
    .spyOn(TestBed.inject(Router), 'navigate')
    .mockResolvedValue(true);
  const fixture = TestBed.createComponent(CourseList);
  return {
    fixture,
    component: fixture.componentInstance,
    api,
    users,
    navigate,
    element: fixture.nativeElement as HTMLElement,
  };
}

describe('CourseList', () => {
  it('loads a paged student list with no create control', async () => {
    const { fixture, element, api, component } = render('STUDENT');
    api.list
      .mockReturnValueOnce(NEVER)
      .mockReturnValueOnce(of({ content: [course], page: 1, totalPages: 2 }));
    fixture.detectChanges();
    expect(element.querySelector('tp-loading')).not.toBeNull();
    component.page.set(1);
    await fixture.whenStable();
    expect(element.querySelector('a')?.textContent).toBe('Plants');
    expect(element.textContent).not.toContain('New course');
  });

  it('creates a course as its teacher and shows an empty state beforehand', async () => {
    const { fixture, element, component, api, navigate } = render('TEACHER');
    await fixture.whenStable();
    expect(element.textContent).toContain('No courses');
    component.creating.set(true);
    component.create();
    expect(api.create).not.toHaveBeenCalled();
    component.form.patchValue({
      title: 'Plants',
      subject: 'Science',
      gradeLevel: 8,
    });
    component.create();
    expect(api.create).toHaveBeenCalledWith({
      title: 'Plants',
      subject: 'Science',
      gradeLevel: 8,
      description: null,
      teacherId: null,
    });
    expect(navigate).toHaveBeenCalledWith(['/courses', 'c']);
  });

  it('requires an admin to select a teacher and allows retrying a failed list', async () => {
    const { fixture, element, component, api, users } = render('ADMIN');
    api.list.mockReturnValueOnce(
      throwError(() => ({
        status: 503,
        code: 'UNAVAILABLE',
        message: 'Offline',
      })),
    );
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Offline',
    );
    element.querySelector<HTMLButtonElement>('tp-error-banner button')?.click();
    component.creating.set(true);
    component.form.patchValue({
      title: 'Plants',
      subject: 'Science',
      description: 'Living things',
    });
    component.create();
    expect(api.create).not.toHaveBeenCalled();
    component.teacherQuery.setValue('Tara');
    component.findTeachers();
    await fixture.whenStable();
    expect(users.list).toHaveBeenCalledWith({ role: 'TEACHER', q: 'Tara' });
    expect(element.querySelector('option[value="t"]')?.textContent).toContain(
      'Tara',
    );
    component.findTeachers();
    component.form.controls.teacherId.setValue('t');
    component.create();
    expect(api.create).toHaveBeenCalledWith(
      expect.objectContaining({ teacherId: 't', description: 'Living things' }),
    );
  });
});
