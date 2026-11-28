import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CourseApi, LessonApi, Topic } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { EmptyState, ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import { map } from 'rxjs';
import { Mutation } from '../shared/mutation';
import { Pagination } from '../shared/pagination';
import { resourceError } from '../shared/resource-error';

@Component({
  selector: 'tp-course-detail',
  imports: [
    EmptyState,
    ErrorBanner,
    Loading,
    PageHeader,
    MatButton,
    MatChip,
    MatFormField,
    MatLabel,
    MatInput,
    MatTab,
    MatTabGroup,
    ReactiveFormsModule,
    RouterLink,
    Pagination,
  ],
  templateUrl: './course-detail.html',
  styleUrl: './courses.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseDetail {
  private readonly api = inject(CourseApi);
  private readonly lessonApi = inject(LessonApi);
  private readonly role = inject(AuthService).role;
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly courseId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => params.get('courseId') ?? ''),
    ),
    { requireSync: true },
  );
  readonly course = rxResource({
    params: this.courseId,
    stream: ({ params }) => this.api.get(params),
  });
  readonly lessonPage = signal(0);
  readonly lessons = rxResource({
    params: () => ({ id: this.courseId(), page: this.lessonPage() }),
    stream: ({ params }) => this.lessonApi.list(params.id, params.page),
  });
  readonly staff = computed(
    () => this.role() === 'TEACHER' || this.role() === 'ADMIN',
  );
  readonly writable = computed(
    () =>
      this.staff() &&
      this.course.hasValue() &&
      this.course.value().status === 'ACTIVE',
  );
  readonly canAuthor = computed(
    () =>
      this.role() === 'TEACHER' &&
      this.course.hasValue() &&
      this.course.value().topics.length > 0,
  );
  readonly students = rxResource({
    params: () => (this.staff() ? this.courseId() : undefined),
    stream: ({ params }) => this.api.students(params),
  });
  readonly topicName = this.fb.control('', [
    Validators.required,
    Validators.maxLength(200),
  ]);
  readonly topicId = signal<string | null>(null);
  readonly topicForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    position: [
      1,
      [
        Validators.required,
        Validators.min(1),
        Validators.max(2147483647),
        Validators.pattern(/^\d+$/),
      ],
    ],
  });
  readonly studentQuery = this.fb.control('', [
    Validators.required,
    Validators.maxLength(100),
  ]);
  readonly query = signal<string | undefined>(undefined);
  readonly matches = rxResource({
    params: () => {
      const query = this.query();
      return this.writable() && query
        ? { id: this.courseId(), query }
        : undefined;
    },
    stream: ({ params }) => this.api.searchStudents(params.id, params.query),
  });
  readonly topicsMutation = new Mutation();
  readonly studentsMutation = new Mutation();
  readonly error = resourceError;

  addTopic(): void {
    const name = this.topicName.value.trim();
    if (!name || this.topicName.invalid) return;
    this.topicsMutation.run(
      () => this.api.addTopic(this.courseId(), name),
      () => {
        this.topicName.reset();
        this.course.reload();
      },
    );
  }

  editTopic(topic: Topic): void {
    this.topicId.set(topic.id);
    this.topicForm.reset({ name: topic.name, position: topic.position });
    this.topicsMutation.clear();
  }

  saveTopic(): void {
    const id = this.topicId();
    const fields = this.topicForm.getRawValue();
    if (!id || this.topicForm.invalid || !fields.name.trim()) return;
    this.topicsMutation.run(
      () => this.api.updateTopic({ id, ...fields, name: fields.name.trim() }),
      () => {
        this.topicId.set(null);
        this.course.reload();
      },
    );
  }

  deleteTopic(id: string): void {
    this.topicsMutation.run(
      () => this.api.deleteTopic(id),
      () => this.course.reload(),
    );
  }

  findStudents(): void {
    const query = this.studentQuery.value.trim();
    if (!query || this.studentQuery.invalid) return;
    if (this.query() === query) this.matches.reload();
    this.query.set(query);
  }

  enrolled(id: string): boolean {
    return (
      this.students.hasValue() &&
      this.students.value().some((student) => student.id === id)
    );
  }

  enroll(id: string): void {
    this.studentsMutation.run(
      () => this.api.enroll(this.courseId(), id),
      () => this.students.reload(),
    );
  }

  remove(id: string): void {
    this.studentsMutation.run(
      () => this.api.remove(this.courseId(), id),
      () => this.students.reload(),
    );
  }
}
