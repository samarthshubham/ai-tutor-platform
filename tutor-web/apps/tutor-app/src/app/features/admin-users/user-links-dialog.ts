import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { ApiError, RelationshipApi, Role, User, UserApi } from '@tutor-web/api';
import { ErrorBanner, Loading } from '@tutor-web/ui';
import { forkJoin, map, Observable } from 'rxjs';

export const LINKABLE_ROLES: Role[] = ['STUDENT', 'PARENT', 'TUTOR'];

export interface UserLinksDialogData {
  user: User;
}

interface LinkRow {
  linkId: string;
  counterpartId: string;
}

interface LinkSection {
  key: string;
  title: string;
  addLabel: string;
  candidateRole: Role;
  list: () => Observable<LinkRow[]>;
  create: (counterpartId: string) => Observable<unknown>;
  remove: (linkId: string) => Observable<unknown>;
  choice: FormControl<string>;
}

const CANDIDATE_PAGE_SIZE = 100;

@Component({
  selector: 'tp-user-links-dialog',
  imports: [
    ErrorBanner,
    Loading,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-links-dialog.html',
  styleUrl: './user-links-dialog.scss',
})
export class UserLinksDialog {
  private readonly userApi = inject(UserApi);
  private readonly relationshipApi = inject(RelationshipApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly user = inject<UserLinksDialogData>(MAT_DIALOG_DATA).user;
  protected readonly sections = this.sectionsFor(this.user);
  protected readonly candidates = signal<User[]>([]);
  protected readonly rows = signal<Partial<Record<string, LinkRow[]>>>({});
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    const roles = [...new Set(this.sections.map((s) => s.candidateRole))];
    forkJoin([
      forkJoin(
        roles.map((role) =>
          this.userApi
            .list({ role, size: CANDIDATE_PAGE_SIZE })
            .pipe(map((page) => page.content)),
        ),
      ),
      this.listRows(),
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([users, rows]) => {
          this.candidates.set(users.flat());
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: (error: ApiError) => {
          this.error.set(error);
          this.loading.set(false);
        },
      });
  }

  protected rowsOf(section: LinkSection): LinkRow[] {
    return this.rows()[section.key] ?? [];
  }

  protected optionsFor(section: LinkSection): User[] {
    const linked = new Set(
      this.rowsOf(section).map((row) => row.counterpartId),
    );
    return this.candidates().filter(
      (candidate) =>
        candidate.role === section.candidateRole && !linked.has(candidate.id),
    );
  }

  protected nameOf(id: string): string {
    return this.candidates().find((c) => c.id === id)?.displayName ?? id;
  }

  protected add(section: LinkSection): void {
    this.mutate(section.create(section.choice.value), () =>
      section.choice.reset(),
    );
  }

  protected remove(section: LinkSection, row: LinkRow): void {
    this.mutate(section.remove(row.linkId));
  }

  private mutate(request: Observable<unknown>, onDone?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        onDone?.();
        this.refreshRows();
      },
      error: (error: ApiError) => {
        this.error.set(error);
        this.busy.set(false);
      },
    });
  }

  private refreshRows(): void {
    this.listRows()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.busy.set(false);
        },
        error: (error: ApiError) => {
          this.error.set(error);
          this.busy.set(false);
        },
      });
  }

  private listRows(): Observable<Record<string, LinkRow[]>> {
    return forkJoin(this.sections.map((section) => section.list())).pipe(
      map((lists) =>
        Object.fromEntries(
          this.sections.map((section, index) => [section.key, lists[index]]),
        ),
      ),
    );
  }

  private sectionsFor(user: User): LinkSection[] {
    const api = this.relationshipApi;
    const choice = () => new FormControl('', { nonNullable: true });
    const guardianOfStudent: LinkSection = {
      key: 'guardians',
      title: 'Parents and guardians',
      addLabel: 'Add parent',
      candidateRole: 'PARENT',
      list: () =>
        api
          .listGuardianLinks({ studentId: user.id, size: CANDIDATE_PAGE_SIZE })
          .pipe(
            map((page) =>
              page.content.map((l) => ({
                linkId: l.id,
                counterpartId: l.parentId,
              })),
            ),
          ),
      create: (parentId) =>
        api.createGuardianLink({ parentId, studentId: user.id }),
      remove: (id) => api.deleteGuardianLink(id),
      choice: choice(),
    };
    const tutorsOfStudent: LinkSection = {
      key: 'tutors',
      title: 'Tutors',
      addLabel: 'Add tutor',
      candidateRole: 'TUTOR',
      list: () =>
        api
          .listTutorAssignments({
            studentId: user.id,
            size: CANDIDATE_PAGE_SIZE,
          })
          .pipe(
            map((page) =>
              page.content.map((a) => ({
                linkId: a.id,
                counterpartId: a.tutorId,
              })),
            ),
          ),
      create: (tutorId) =>
        api.createTutorAssignment({ tutorId, studentId: user.id }),
      remove: (id) => api.deleteTutorAssignment(id),
      choice: choice(),
    };
    const childrenOfParent: LinkSection = {
      key: 'children',
      title: 'Children',
      addLabel: 'Add child',
      candidateRole: 'STUDENT',
      list: () =>
        api
          .listGuardianLinks({ parentId: user.id, size: CANDIDATE_PAGE_SIZE })
          .pipe(
            map((page) =>
              page.content.map((l) => ({
                linkId: l.id,
                counterpartId: l.studentId,
              })),
            ),
          ),
      create: (studentId) =>
        api.createGuardianLink({ parentId: user.id, studentId }),
      remove: (id) => api.deleteGuardianLink(id),
      choice: choice(),
    };
    const studentsOfTutor: LinkSection = {
      key: 'students',
      title: 'Assigned students',
      addLabel: 'Add student',
      candidateRole: 'STUDENT',
      list: () =>
        api
          .listTutorAssignments({ tutorId: user.id, size: CANDIDATE_PAGE_SIZE })
          .pipe(
            map((page) =>
              page.content.map((a) => ({
                linkId: a.id,
                counterpartId: a.studentId,
              })),
            ),
          ),
      create: (studentId) =>
        api.createTutorAssignment({ tutorId: user.id, studentId }),
      remove: (id) => api.deleteTutorAssignment(id),
      choice: choice(),
    };
    switch (user.role) {
      case 'STUDENT':
        return [guardianOfStudent, tutorsOfStudent];
      case 'PARENT':
        return [childrenOfParent];
      case 'TUTOR':
        return [studentsOfTutor];
      default:
        return [];
    }
  }
}
