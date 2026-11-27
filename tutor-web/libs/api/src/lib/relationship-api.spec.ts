import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { RelationshipApi } from './relationship-api';

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

describe('RelationshipApi', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    return {
      api: TestBed.inject(RelationshipApi),
      backend: TestBed.inject(HttpTestingController),
    };
  }

  it('should list guardian links by student', async () => {
    const { api, backend } = setup();

    const page = firstValueFrom(api.listGuardianLinks({ studentId: 's1' }));
    const request = backend.expectOne((r) => r.url === '/api/guardian-links');
    request.flush(EMPTY_PAGE);

    expect(request.request.params.get('studentId')).toBe('s1');
    expect(await page).toEqual(EMPTY_PAGE);
  });

  it('should create and delete a guardian link', async () => {
    const { api, backend } = setup();

    const created = firstValueFrom(
      api.createGuardianLink({ parentId: 'p1', studentId: 's1' }),
    );
    const post = backend.expectOne('/api/guardian-links');
    post.flush({ id: 'l1', parentId: 'p1', studentId: 's1' });
    const deleted = firstValueFrom(api.deleteGuardianLink('l1'));
    const remove = backend.expectOne('/api/guardian-links/l1');
    remove.flush(null);

    expect(post.request.body).toEqual({ parentId: 'p1', studentId: 's1' });
    expect((await created).id).toBe('l1');
    expect(remove.request.method).toBe('DELETE');
    await deleted;
    backend.verify();
  });

  it('should list tutor assignments by tutor', async () => {
    const { api, backend } = setup();

    const page = firstValueFrom(api.listTutorAssignments({ tutorId: 't1' }));
    const request = backend.expectOne(
      (r) => r.url === '/api/tutor-assignments',
    );
    request.flush(EMPTY_PAGE);

    expect(request.request.params.get('tutorId')).toBe('t1');
    await page;
  });

  it('should create and delete a tutor assignment', async () => {
    const { api, backend } = setup();

    const created = firstValueFrom(
      api.createTutorAssignment({ tutorId: 't1', studentId: 's1' }),
    );
    const post = backend.expectOne('/api/tutor-assignments');
    post.flush({ id: 'a1', tutorId: 't1', studentId: 's1' });
    const deleted = firstValueFrom(api.deleteTutorAssignment('a1'));
    const remove = backend.expectOne('/api/tutor-assignments/a1');
    remove.flush(null);

    expect(post.request.body).toEqual({ tutorId: 't1', studentId: 's1' });
    expect((await created).id).toBe('a1');
    expect(remove.request.method).toBe('DELETE');
    await deleted;
    backend.verify();
  });
});
