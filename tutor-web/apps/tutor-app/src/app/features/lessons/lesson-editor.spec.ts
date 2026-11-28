import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { CourseApi, LessonApi } from '@tutor-web/api';
import { of, Subject } from 'rxjs';
import { canLeaveLesson, LessonEditor } from './lesson-editor';

const draft = {
  id: 'l',
  courseId: 'c',
  title: 'Plants',
  bodyMarkdown: '## Roots',
  topicId: 't',
  status: 'DRAFT',
  reviewComment: 'Add an example.',
};
const course = {
  id: 'c',
  title: 'Science',
  topics: [{ id: 't', name: 'Plant growth', position: 1 }],
};

function render(params: Record<string, string>) {
  const api = {
    get: vi.fn().mockReturnValue(of(draft)),
    create: vi.fn().mockReturnValue(of(draft)),
    update: vi.fn().mockReturnValue(of(draft)),
    transition: vi.fn().mockReturnValue(of({ ...draft, status: 'IN_REVIEW' })),
  };
  const courses = { get: vi.fn().mockReturnValue(of(course)) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap(params)) },
      },
      { provide: CourseApi, useValue: courses },
      { provide: LessonApi, useValue: api },
    ],
  });
  const navigate = vi
    .spyOn(TestBed.inject(Router), 'navigate')
    .mockResolvedValue(true);
  const fixture = TestBed.createComponent(LessonEditor);
  return {
    fixture,
    component: fixture.componentInstance,
    api,
    courses,
    navigate,
    element: fixture.nativeElement as HTMLElement,
  };
}

describe('LessonEditor', () => {
  it('shows the feedback, sanitizes the live preview and asks before leaving a dirty draft', async () => {
    const { fixture, component, api, navigate, element } = render({
      lessonId: 'l',
    });
    await fixture.whenStable();
    expect(element.textContent).toContain('Add an example.');
    const markdown =
      '## Leaves\n\n<script type="text/plain">marker</script>\n\n<img onerror="">';
    component.form.controls.bodyMarkdown.setValue(markdown);
    component.form.markAsDirty();
    await fixture.whenStable();
    expect(element.querySelector('tp-markdown h2')?.textContent).toBe('Leaves');
    expect(
      element.querySelector('tp-markdown script, tp-markdown [onerror]'),
    ).toBeNull();
    component.submit('l');
    expect(api.transition).not.toHaveBeenCalled();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    expect(
      canLeaveLesson(component, {} as never, {} as never, {} as never),
    ).toBe(false);
    expect(confirm).toHaveBeenCalledWith(
      expect.stringContaining('unsaved changes'),
    );
    const unload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unload);
    expect(unload.defaultPrevented).toBe(true);
    component.save();
    expect(component.canLeave()).toBe(true);
    expect(api.update).toHaveBeenCalledWith('l', {
      title: 'Plants',
      bodyMarkdown: markdown,
      topicId: 't',
    });
    component.submit('l');
    expect(api.transition).toHaveBeenCalledWith('l', 'submit');
    expect(navigate).toHaveBeenCalledWith(['/lessons', 'l']);
  });

  it('creates a draft and replaces the empty editor URL', async () => {
    const { fixture, component, api, navigate } = render({ courseId: 'c' });
    component.save();
    await fixture.whenStable();
    component.save();
    expect(api.create).not.toHaveBeenCalled();
    component.form.setValue({
      title: 'Plants',
      bodyMarkdown: 'Water and light',
      topicId: 't',
    });
    component.save();
    expect(api.create).toHaveBeenCalledWith('c', {
      title: 'Plants',
      bodyMarkdown: 'Water and light',
      topicId: 't',
    });
    expect(navigate).toHaveBeenCalledWith(['/lessons', 'l', 'edit'], {
      replaceUrl: true,
    });
  });

  it('shows loading, API errors, and an empty state when the course has no topics', async () => {
    const { fixture, component, courses, element } = render({ courseId: 'c' });
    const pending = new Subject();
    courses.get
      .mockReturnValueOnce(pending)
      .mockReturnValueOnce(of({ ...course, topics: [] }));
    fixture.detectChanges();
    expect(element.querySelector('tp-loading')).not.toBeNull();
    pending.error({
      status: 503,
      code: 'UNAVAILABLE',
      message: 'Course is unavailable',
    });
    await fixture.whenStable();
    expect(element.querySelector('tp-error-banner')?.textContent).toContain(
      'Course is unavailable',
    );
    component.editor.reload();
    await fixture.whenStable();
    expect(element.textContent).toContain('No topics');
  });

  it('does not open the editor for a lesson awaiting review', async () => {
    const { fixture, api, element } = render({ lessonId: 'l' });
    api.get.mockReturnValue(of({ ...draft, status: 'IN_REVIEW' }));
    await fixture.whenStable();
    expect(element.textContent).toContain('Only draft lessons can be edited.');
    expect(element.querySelector('form')).toBeNull();
  });
});
