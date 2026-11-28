import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LessonApi } from './lesson-api';

describe('LessonApi', () => {
  it('reads the paged list and queue, writes Markdown, and sends the review comment', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(LessonApi);
    const backend = TestBed.inject(HttpTestingController);
    const fields = {
      title: 'Plants',
      topicId: 'topic',
      bodyMarkdown: '## Roots',
    };
    api.list('c').subscribe();
    backend.expectOne('/api/courses/c/lessons?page=0').flush({});
    api.reviewQueue().subscribe();
    backend.expectOne('/api/lessons?status=IN_REVIEW&page=0').flush({});
    api.get('l').subscribe();
    backend.expectOne('/api/lessons/l').flush({});
    api.create('c', fields).subscribe();
    const create = backend.expectOne({
      url: '/api/courses/c/lessons',
      method: 'POST',
    });
    expect(create.request.body).toEqual(fields);
    create.flush({});
    api.update('l', fields).subscribe();
    const update = backend.expectOne({ url: '/api/lessons/l', method: 'PUT' });
    expect(update.request.body).toEqual(fields);
    update.flush({});
    api.transition('l', 'publish').subscribe();
    const publish = backend.expectOne({
      url: '/api/lessons/l/publish',
      method: 'POST',
    });
    expect(publish.request.body).toBeNull();
    publish.flush({});
    api.transition('l', 'request-changes', 'Explain roots.').subscribe();
    const changes = backend.expectOne({
      url: '/api/lessons/l/request-changes',
      method: 'POST',
    });
    expect(changes.request.body).toEqual({ comment: 'Explain roots.' });
    changes.flush({});
    backend.verify();
  });
});
