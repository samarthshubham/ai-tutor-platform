import { Role } from './role';

export interface Topic {
  id: string;
  name: string;
  position: number;
}

export interface CourseFields {
  title: string;
  subject: string;
  gradeLevel: number;
  description: string | null;
}

export interface Course extends CourseFields {
  id: string;
  teacherId: string;
  teacherName: string;
  status: 'ACTIVE' | 'ARCHIVED';
  topics: Topic[];
}

export interface CreateCourse extends CourseFields {
  teacherId: string | null;
}

export interface UserSummary {
  id: string;
  displayName: string;
  role: Role;
}

export interface Enrollment {
  courseId: string;
  studentId: string;
}
