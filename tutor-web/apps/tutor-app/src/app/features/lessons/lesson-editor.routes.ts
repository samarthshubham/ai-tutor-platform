import { Routes } from '@angular/router';
import { canLeaveLesson, LessonEditor } from './lesson-editor';

export const lessonEditorRoutes: Routes = [
  { path: '', component: LessonEditor, canDeactivate: [canLeaveLesson] },
];
