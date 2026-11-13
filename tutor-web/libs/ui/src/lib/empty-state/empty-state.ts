import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatButton } from '@angular/material/button';

@Component({
  selector: 'tp-empty-state',
  imports: [MatButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="empty-state" [attr.aria-label]="title()">
      <h2>{{ title() }}</h2>
      <p>{{ message() }}</p>
      @if (actionLabel(); as actionLabel) {
        <button matButton="filled" type="button" (click)="action.emit()">
          {{ actionLabel }}
        </button>
      }
    </section>
  `,
  styles: `
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 48px 24px;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
    h2 {
      margin: 0;
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-on-surface);
    }
    p {
      margin: 0 0 8px;
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly actionLabel = input<string>();
  readonly action = output<void>();
}
