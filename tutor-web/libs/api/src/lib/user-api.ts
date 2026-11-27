import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './page';
import { toHttpParams } from './query-params';
import { User, UserQuery } from './user';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  me(): Observable<User> {
    return this.http.get<User>('/api/me');
  }

  list(query: UserQuery = {}): Observable<Page<User>> {
    return this.http.get<Page<User>>('/api/users', {
      params: toHttpParams(query),
    });
  }
}
