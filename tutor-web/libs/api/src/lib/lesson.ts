export type LessonStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
export type LessonAction =
  | 'submit'
  | 'publish'
  | 'request-changes'
  | 'revise'
  | 'archive';

export interface LessonFields {
  title: string;
  topicId: string;
  bodyMarkdown: string;
}

interface LessonBase extends LessonFields {
  id: string;
  courseId: string;
}

export interface StaffLesson extends LessonBase {
  status: LessonStatus;
  publishedVersion: number | null;
  reviewComment: string | null;
  authorId: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface PublishedLesson extends LessonBase {
  version: number;
  publishedAt: string;
}

export type Lesson = StaffLesson | PublishedLesson;
