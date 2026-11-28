import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { CourseApi, LessonApi, Role } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { of, Subject, throwError } from 'rxjs';
import { CourseDetail } from './course-detail';

const topic = { id: 't', name: 'Plants', position: 1 };
const course = {
  id: 'c',
  title: 'Science',
  subject: 'Science',
  gradeLevel: 8,
  teacherName: 'Tara',
  status: 'ACTIVE',
  description: null,
  topics: [topic],
};
const student = { id: 's', displayName: 'Sam', role: 'STUDENT' };
function render(role: Role) {
  const api = {
    get: vi.fn().mockReturnValue(of(course)),
    students: vi.fn().mockReturnValue(of([])),
    searchStudents: vi.fn().mockReturnValue(of([student])),
    enroll: vi.fn().mockReturnValue(of({})),
    remove: vi.fn().mockReturnValue(of(null)),
    addTopic: vi.fn().mockReturnValue(of(topic)),
    updateTopic: vi.fn().mockReturnValue(of(topic)),
    deleteTopic: vi
      .fn()
      .mockReturnValue(
        throwError(() => ({ status: 409, message: 'Topic is in use' })),
      ),
  };
  const lesson = {
    list: vi.fn().mockReturnValue(of({ content: [], page: 0, totalPages: 0 })),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ courseId: 'c' })) },
      },
      { provide: AuthService, useValue: { role: signal(role) } },
      { provide: CourseApi, useValue: api },
      { provide: LessonApi, useValue: lesson },
    ],
  });
  const fixture = TestBed.createComponent(CourseDetail);
  return {
    fixture,
    component: fixture.componentInstance,
    api,
    lesson,
    element: fixture.nativeElement as HTMLElement,
  };
}

describe('CourseDetail', () => {
  it('shows lessons and hides enrollment and authoring from students', async () => {
    const { fixture, api, lesson, element } = render('STUDENT');
    lesson.list.mockReturnValue(
      of({
        content: [{ id: 'l', title: 'Photosynthesis' }],
        page: 0,
        totalPages: 1,
      }),
    );
    await fixture.whenStable();
    expect(
      element.querySelector('a[href="/lessons/l"]')?.textContent,
    ).toContain('Photosynthesis');
    expect(api.students).not.toHaveBeenCalled();
    expect(element.textContent).not.toContain('New lesson');
  });

  it('loads, empties, and reports course errors', async () => {
    const { fixture, api, component, element } = render('TEACHER');
    const pending = new Subject();
    api.get.mockReturnValueOnce(pending);
    fixture.detectChanges();
    expect(element.querySelector('tp-loading')).not.toBeNull();
    pending.error({
      status: 503,
      code: 'UNAVAILABLE',
      message: 'Course unavailable',
    });
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Course unavailable',
    );
    component.course.reload();
    await fixture.whenStable();
    expect(element.textContent).toContain('No lessons');
  });

  it('edits topics and shows the server conflict for an in-use topic', async () => {
    const { fixture, component, api } = render('TEACHER');
    await fixture.whenStable();
    component.addTopic();
    component.topicName.setValue(' Roots ');
    component.addTopic();
    expect(api.addTopic).toHaveBeenCalledExactlyOnceWith('c', 'Roots');
    component.saveTopic();
    component.editTopic(topic);
    component.topicForm.setValue({ name: 'Leaves', position: 2 });
    component.saveTopic();
    expect(api.updateTopic).toHaveBeenCalledWith({
      id: 't',
      name: 'Leaves',
      position: 2,
    });
    component.deleteTopic('t');
    expect(component.topicsMutation.error()?.message).toBe('Topic is in use');
    component.topicsMutation.retry();
    expect(api.deleteTopic).toHaveBeenCalledTimes(2);
  });

  it('searches, enrolls and removes a student with refreshes after writes', async () => {
    const { fixture, component, api } = render('TEACHER');
    await fixture.whenStable();
    expect(component.enrolled('s')).toBe(false);
    component.findStudents();
    component.studentQuery.setValue(' Sam ');
    component.findStudents();
    await fixture.whenStable();
    component.findStudents();
    expect(api.searchStudents).toHaveBeenCalledWith('c', 'Sam');
    api.students.mockReturnValueOnce(of([student]));
    component.enroll('s');
    await fixture.whenStable();
    expect(component.enrolled('s')).toBe(true);
    component.remove('s');
    await fixture.whenStable();
    expect(component.enrolled('s')).toBe(false);
    expect(api.enroll).toHaveBeenCalledWith('c', 's');
    expect(api.remove).toHaveBeenCalledWith('c', 's');
  });
});
