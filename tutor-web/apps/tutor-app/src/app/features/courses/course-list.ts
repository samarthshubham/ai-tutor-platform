import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardTitle,
} from '@angular/material/card';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { CourseApi, UserApi } from '@tutor-web/api';
import { AuthService } from '@tutor-web/auth';
import { EmptyState, ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import { Mutation } from '../shared/mutation';
import { Pagination } from '../shared/pagination';
import { resourceError } from '../shared/resource-error';

@Component({
  selector: 'tp-course-list',
  imports: [
    EmptyState,
    ErrorBanner,
    Loading,
    PageHeader,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatFormField,
    MatLabel,
    MatInput,
    ReactiveFormsModule,
    RouterLink,
    Pagination,
  ],
  templateUrl: './course-list.html',
  styleUrl: './courses.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseList {
  private readonly api = inject(CourseApi);
  private readonly users = inject(UserApi);
  private readonly router = inject(Router);
  private readonly role = inject(AuthService).role;
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly page = signal(0);
  readonly courses = rxResource({
    params: this.page,
    stream: ({ params }) => this.api.list(params),
  });
  readonly canCreate = computed(
    () => this.role() === 'ADMIN' || this.role() === 'TEACHER',
  );
  readonly admin = computed(() => this.role() === 'ADMIN');
  readonly creating = signal(false);
  readonly mutation = new Mutation();
  readonly error = resourceError;
  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    subject: ['', [Validators.required, Validators.maxLength(100)]],
    gradeLevel: [
      1,
      [
        Validators.required,
        Validators.min(1),
        Validators.max(12),
        Validators.pattern(/^\d+$/),
      ],
    ],
    description: ['', Validators.maxLength(2000)],
    teacherId: [''],
  });
  readonly teacherQuery = this.fb.control('', Validators.maxLength(100));
  readonly query = signal<string | undefined>(undefined);
  readonly teachers = rxResource({
    params: this.query,
    stream: ({ params }) => this.users.list({ role: 'TEACHER', q: params }),
  });

  findTeachers(): void {
    const query = this.teacherQuery.value.trim();
    this.form.controls.teacherId.reset();
    if (this.query() === query) this.teachers.reload();
    this.query.set(query);
  }

  cancelCreate(): void {
    this.creating.set(false);
    this.form.reset();
    this.teacherQuery.reset();
    this.query.set(undefined);
    this.mutation.clear();
  }

  create(): void {
    if (
      this.form.invalid ||
      (this.admin() && !this.form.controls.teacherId.value)
    )
      return;
    const fields = this.form.getRawValue();
    this.mutation.run(
      () =>
        this.api.create({
          ...fields,
          teacherId: this.admin() ? fields.teacherId : null,
          description: fields.description || null,
        }),
      (course) => {
        this.cancelCreate();
        void this.router.navigate(['/courses', course.id]);
      },
    );
  }
}
