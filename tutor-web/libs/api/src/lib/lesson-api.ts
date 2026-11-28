import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Lesson, LessonAction, LessonFields, StaffLesson } from './lesson';
import { Page } from './page';
import { toHttpParams } from './query-params';

@Injectable({ providedIn: 'root' })
export class LessonApi {
  private readonly http = inject(HttpClient);

  list(courseId: string, page = 0) {
    return this.http.get<Page<Lesson>>(`/api/courses/${courseId}/lessons`, {
      params: toHttpParams({ page }),
    });
  }

  reviewQueue(page = 0) {
    return this.http.get<Page<StaffLesson>>('/api/lessons', {
      params: toHttpParams({ status: 'IN_REVIEW', page }),
    });
  }

  get(id: string) {
    return this.http.get<Lesson>(`/api/lessons/${id}`);
  }

  create(courseId: string, fields: LessonFields) {
    return this.http.post<StaffLesson>(
      `/api/courses/${courseId}/lessons`,
      fields,
    );
  }

  update(id: string, fields: LessonFields) {
    return this.http.put<StaffLesson>(`/api/lessons/${id}`, fields);
  }

  transition(id: string, action: LessonAction, comment?: string) {
    return this.http.post<StaffLesson>(
      `/api/lessons/${id}/${action}`,
      comment === undefined ? null : { comment },
    );
  }
}
