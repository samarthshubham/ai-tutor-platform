import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import {
  ApiError,
  Page,
  ROLES,
  Role,
  User,
  UserApi,
  roleLabel,
} from '@tutor-web/api';
import { EmptyState, ErrorBanner, Loading, PageHeader } from '@tutor-web/ui';
import {
  catchError,
  debounceTime,
  map,
  merge,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import {
  LINKABLE_ROLES,
  UserLinksDialog,
  UserLinksDialogData,
} from './user-links-dialog';

type UsersState =
  | { status: 'loading' }
  | { status: 'error'; error: ApiError }
  | { status: 'ready'; page: Page<User> };

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'tp-admin-users',
  imports: [
    EmptyState,
    ErrorBanner,
    Loading,
    MatButton,
    MatFormField,
    MatInput,
    MatLabel,
    MatOption,
    MatPaginator,
    MatSelect,
    MatTableModule,
    PageHeader,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.scss',
})
export class AdminUsers {
  private readonly userApi = inject(UserApi);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private pageIndex = 0;

  protected readonly roles = ROLES;
  protected readonly roleLabel = roleLabel;
  protected readonly columns = ['displayName', 'username', 'role', 'actions'];
  protected readonly filters = new FormGroup({
    role: new FormControl<Role | ''>('', { nonNullable: true }),
    q: new FormControl('', { nonNullable: true }),
  });
  protected readonly state = signal<UsersState>({ status: 'loading' });

  constructor() {
    merge(
      this.filters.controls.role.valueChanges.pipe(
        tap(() => (this.pageIndex = 0)),
      ),
      this.filters.controls.q.valueChanges.pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        tap(() => (this.pageIndex = 0)),
      ),
      this.reload,
    )
      .pipe(
        startWith(null),
        tap(() => this.state.set({ status: 'loading' })),
        switchMap(() => this.fetchPage()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected errorState(): ApiError | null {
    const state = this.state();
    return state.status === 'error' ? state.error : null;
  }

  protected readyPage(): Page<User> | null {
    const state = this.state();
    return state.status === 'ready' ? state.page : null;
  }

  protected retry(): void {
    this.reload.next();
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.reload.next();
  }

  protected canManageLinks(user: User): boolean {
    return LINKABLE_ROLES.includes(user.role);
  }

  protected manageLinks(user: User): void {
    const data: UserLinksDialogData = { user };
    this.dialog.open(UserLinksDialog, {
      data,
      width: '640px',
      maxWidth: '95vw',
    });
  }

  private fetchPage() {
    const { role, q } = this.filters.getRawValue();
    return this.userApi
      .list({
        role: role === '' ? undefined : role,
        q: q.trim(),
        page: this.pageIndex,
        size: PAGE_SIZE,
      })
      .pipe(
        map((page): UsersState => ({ status: 'ready', page })),
        catchError((error: ApiError) =>
          of<UsersState>({ status: 'error', error }),
        ),
      );
  }
}
