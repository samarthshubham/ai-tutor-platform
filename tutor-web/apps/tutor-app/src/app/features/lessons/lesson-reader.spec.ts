import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { LessonApi, Role } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { of, Subject } from 'rxjs';
import { LessonReader } from './lesson-reader';

const published = {
  id: 'l',
  courseId: 'c',
  title: 'Plants',
  bodyMarkdown:
    '## Leaves\n\n<script type="text/plain">marker</script>\n\n<img onerror="">',
};
function render(role: Role) {
  const api = {
    get: vi.fn().mockReturnValue(of(published)),
    transition: vi.fn(),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ lessonId: 'l' })) },
      },
      { provide: AuthService, useValue: { role: signal(role) } },
      { provide: LessonApi, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(LessonReader);
  return {
    fixture,
    component: fixture.componentInstance,
    api,
    element: fixture.nativeElement as HTMLElement,
  };
}

describe('LessonReader', () => {
  it('renders safe published text and no action as a student', async () => {
    const { fixture, element, api } = render('STUDENT');
    await fixture.whenStable();
    expect(api.get).toHaveBeenCalledWith('l');
    expect(element.querySelector('article h2')?.textContent).toBe('Leaves');
    expect(
      element.querySelector(
        'article script, article [onerror], tp-lesson-actions',
      ),
    ).toBeNull();
  });

  it('shows the staff status and updates it when the teacher revises', async () => {
    const { fixture, api, element } = render('TEACHER');
    const staff = {
      ...published,
      status: 'PUBLISHED',
      reviewComment: 'Look at roots.',
    };
    api.get.mockReturnValue(of(staff));
    api.transition.mockReturnValue(of({ ...staff, status: 'DRAFT' }));
    await fixture.whenStable();
    expect(element.textContent).toContain('Look at roots.');
    const revise = Array.from(element.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('Revise lesson'),
    );
    revise?.click();
    await fixture.whenStable();
    expect(api.transition).toHaveBeenCalledWith('l', 'revise', undefined);
    expect(element.querySelector('mat-chip')?.textContent).toContain('DRAFT');
  });

  it('shows loading and retries a failed lesson request', async () => {
    const { fixture, api, element } = render('TUTOR');
    const pending = new Subject();
    api.get.mockReturnValueOnce(pending);
    fixture.detectChanges();
    expect(element.querySelector('tp-loading')).not.toBeNull();
    pending.error({
      status: 503,
      code: 'UNAVAILABLE',
      message: 'Lesson not found',
    });
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Lesson not found',
    );
    element.querySelector<HTMLButtonElement>('tp-error-banner button')?.click();
    await fixture.whenStable();
    expect(element.querySelector('h1')?.textContent).toBe('Plants');
    expect(element.querySelector('a[href="/courses/c"]')).toBeNull();
  });
});
