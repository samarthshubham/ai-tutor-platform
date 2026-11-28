import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Course, CreateCourse, Enrollment, Topic, UserSummary } from './course';
import { Page } from './page';
import { toHttpParams } from './query-params';

@Injectable({ providedIn: 'root' })
export class CourseApi {
  private readonly http = inject(HttpClient);

  list(page = 0) {
    return this.http.get<Page<Course>>('/api/courses', {
      params: toHttpParams({ page }),
    });
  }

  get(id: string) {
    return this.http.get<Course>(`/api/courses/${id}`);
  }

  create(course: CreateCourse) {
    return this.http.post<Course>('/api/courses', course);
  }

  addTopic(courseId: string, name: string) {
    return this.http.post<Topic>(`/api/courses/${courseId}/topics`, { name });
  }

  updateTopic({ id, name, position }: Topic) {
    return this.http.put<Topic>(`/api/topics/${id}`, { name, position });
  }

  deleteTopic(id: string) {
    return this.http.delete<void>(`/api/topics/${id}`);
  }

  students(courseId: string) {
    return this.http.get<UserSummary[]>(`/api/courses/${courseId}/students`);
  }

  searchStudents(courseId: string, q: string) {
    return this.http.get<UserSummary[]>(
      `/api/courses/${courseId}/student-search`,
      { params: toHttpParams({ q }) },
    );
  }

  enroll(courseId: string, studentId: string) {
    return this.http.post<Enrollment>(`/api/courses/${courseId}/enrollments`, {
      studentId,
    });
  }

  remove(courseId: string, studentId: string) {
    return this.http.delete<void>(
      `/api/courses/${courseId}/enrollments/${studentId}`,
    );
  }
}
