import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CourseApi } from './course-api';

describe('CourseApi', () => {
  it('uses the course and topic routes and preserves request fields', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(CourseApi);
    const backend = TestBed.inject(HttpTestingController);
    const fields = {
      title: 'Plants',
      subject: 'Science',
      gradeLevel: 8,
      description: null,
      teacherId: null,
    };
    api.list().subscribe();
    backend.expectOne('/api/courses?page=0').flush({});
    api.get('c').subscribe();
    backend.expectOne({ url: '/api/courses/c', method: 'GET' }).flush({});
    api.create(fields).subscribe();
    const create = backend.expectOne({ url: '/api/courses', method: 'POST' });
    expect(create.request.body).toEqual(fields);
    create.flush({});
    api.addTopic('c', 'Roots').subscribe();
    const topic = backend.expectOne({
      url: '/api/courses/c/topics',
      method: 'POST',
    });
    expect(topic.request.body).toEqual({ name: 'Roots' });
    topic.flush({});
    api.updateTopic({ id: 't', name: 'Leaves', position: 2 }).subscribe();
    const update = backend.expectOne({ url: '/api/topics/t', method: 'PUT' });
    expect(update.request.body).toEqual({ name: 'Leaves', position: 2 });
    update.flush({});
    api.deleteTopic('t').subscribe();
    backend.expectOne({ url: '/api/topics/t', method: 'DELETE' }).flush(null);
    backend.verify();
  });

  it('bounds enrollment actions to the selected course', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(CourseApi);
    const backend = TestBed.inject(HttpTestingController);
    api.students('c').subscribe();
    backend.expectOne('/api/courses/c/students').flush([]);
    api.searchStudents('c', 'Sam & Smith').subscribe();
    const search = backend.expectOne(
      (request) => request.url === '/api/courses/c/student-search',
    );
    expect(search.request.params.get('q')).toBe('Sam & Smith');
    search.flush([]);
    api.enroll('c', 's').subscribe();
    const enroll = backend.expectOne({
      url: '/api/courses/c/enrollments',
      method: 'POST',
    });
    expect(enroll.request.body).toEqual({ studentId: 's' });
    enroll.flush({});
    api.remove('c', 's').subscribe();
    backend
      .expectOne({ url: '/api/courses/c/enrollments/s', method: 'DELETE' })
      .flush(null);
    backend.verify();
  });
});
