import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { PageHeader } from '@tutor-web/ui';

@Component({
  selector: 'tp-forbidden',
  imports: [MatButton, PageHeader, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <tp-page-header title="You don't have access" />
    <p>Your role cannot open this page.</p>
    <a matButton="filled" routerLink="/">Go to your home page</a>
  `,
})
export class Forbidden {}
