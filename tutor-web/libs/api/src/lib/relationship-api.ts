import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './page';
import { toHttpParams } from './query-params';
import {
  GuardianLink,
  GuardianLinkQuery,
  GuardianLinkRequest,
  TutorAssignment,
  TutorAssignmentQuery,
  TutorAssignmentRequest,
} from './relationship';

@Injectable({ providedIn: 'root' })
export class RelationshipApi {
  private readonly http = inject(HttpClient);

  listGuardianLinks(query: GuardianLinkQuery): Observable<Page<GuardianLink>> {
    return this.http.get<Page<GuardianLink>>('/api/guardian-links', {
      params: toHttpParams(query),
    });
  }

  createGuardianLink(request: GuardianLinkRequest): Observable<GuardianLink> {
    return this.http.post<GuardianLink>('/api/guardian-links', request);
  }

  deleteGuardianLink(id: string): Observable<void> {
    return this.http.delete<void>(`/api/guardian-links/${id}`);
  }

  listTutorAssignments(
    query: TutorAssignmentQuery,
  ): Observable<Page<TutorAssignment>> {
    return this.http.get<Page<TutorAssignment>>('/api/tutor-assignments', {
      params: toHttpParams(query),
    });
  }

  createTutorAssignment(
    request: TutorAssignmentRequest,
  ): Observable<TutorAssignment> {
    return this.http.post<TutorAssignment>('/api/tutor-assignments', request);
  }

  deleteTutorAssignment(id: string): Observable<void> {
    return this.http.delete<void>(`/api/tutor-assignments/${id}`);
  }
}
