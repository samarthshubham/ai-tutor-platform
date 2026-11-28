import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  SecurityContext,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Marked } from 'marked';

const parser = new Marked({ async: false, renderer: { html: () => '' } });

@Component({
  selector: 'tp-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="markdown" [innerHTML]="html()"></div>`,
  styles: `
    .markdown {
      overflow-wrap: anywhere;
      overflow-x: auto;
    }
  `,
})
export class Markdown {
  private readonly sanitizer = inject(DomSanitizer);
  readonly source = input.required<string>();
  protected readonly html = computed(() =>
    this.sanitizer.sanitize(
      SecurityContext.HTML,
      parser.parse(this.source(), { async: false }),
    ),
  );
}
