import { Role } from './role';

export interface User {
  id: string;
  username: string;
  displayName: string;
  email: string | null;
  role: Role;
  schoolId: string;
}

export interface UserQuery {
  role?: Role;
  q?: string;
  page?: number;
  size?: number;
}
