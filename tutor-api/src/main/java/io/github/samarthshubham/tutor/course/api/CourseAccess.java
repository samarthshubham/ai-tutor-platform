package io.github.samarthshubham.tutor.course.api;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import java.util.UUID;

public interface CourseAccess {

  boolean canSeeCourse(CurrentUser user, UUID courseId);

  void requireCourseAccess(CurrentUser user, UUID courseId);

  boolean canManageCourse(CurrentUser user, UUID courseId);

  void requireCourseManagement(CurrentUser user, UUID courseId);

  boolean canSeeStudent(CurrentUser user, UUID studentId);

  void requireStudentAccess(CurrentUser user, UUID studentId);
}
