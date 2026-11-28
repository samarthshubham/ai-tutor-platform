package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.EnrollmentRepository;
import io.github.samarthshubham.tutor.identity.api.AccessPolicy;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CourseAccessPolicy implements CourseAccess {

  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final CourseVisibility visibility;
  private final AccessPolicy studentAccess;

  CourseAccessPolicy(
      CourseRepository courses,
      EnrollmentRepository enrollments,
      CourseVisibility visibility,
      AccessPolicy studentAccess) {
    this.courses = courses;
    this.enrollments = enrollments;
    this.visibility = visibility;
    this.studentAccess = studentAccess;
  }

  @Override
  public boolean canSeeCourse(CurrentUser user, UUID courseId) {
    return courses.exists(
        Specification.allOf(visibility.withId(courseId), visibility.visibleTo(user)));
  }

  @Override
  public void requireCourseAccess(CurrentUser user, UUID courseId) {
    if (!canSeeCourse(user, courseId)) {
      throw new NotFoundException("Course not found");
    }
  }

  @Override
  public boolean canManageCourse(CurrentUser user, UUID courseId) {
    return switch (user.role()) {
      case ADMIN, TEACHER -> canSeeCourse(user, courseId);
      default -> false;
    };
  }

  @Override
  public void requireCourseManagement(CurrentUser user, UUID courseId) {
    if (!canManageCourse(user, courseId)) {
      throw new NotFoundException("Course not found");
    }
  }

  @Override
  public boolean canSeeStudent(CurrentUser user, UUID studentId) {
    if (studentAccess.canSeeStudent(user, studentId)) {
      return true;
    }
    return user.role() == Role.TEACHER && enrollments.isStudentOfTeacher(user.id(), studentId);
  }

  @Override
  public void requireStudentAccess(CurrentUser user, UUID studentId) {
    if (!canSeeStudent(user, studentId)) {
      throw new NotFoundException("Student not found");
    }
  }
}
