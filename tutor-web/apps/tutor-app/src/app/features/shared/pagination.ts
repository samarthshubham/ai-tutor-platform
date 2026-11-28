import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { Page } from '@tutor-web/api';

@Component({
  selector: 'tp-pagination',
  imports: [MatButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (page().totalPages > 1 || page().page > 0) {
      <nav aria-label="Pagination">
        <button
          matButton
          type="button"
          [disabled]="page().page === 0"
          (click)="navigate.emit(page().page - 1)"
        >
          Previous page
        </button>
        <span>Page {{ page().page + 1 }} of {{ page().totalPages }}</span>
        <button
          matButton
          type="button"
          [disabled]="page().page + 1 >= page().totalPages"
          (click)="navigate.emit(page().page + 1)"
        >
          Next page
        </button>
      </nav>
    }
  `,
  styles: `
    nav {
      display: flex;
      gap: 12px;
      align-items: center;
      margin-top: 16px;
    }
  `,
})
export class Pagination {
  readonly page = input.required<Page<unknown>>();
  readonly navigate = output<number>();
}
