package io.github.samarthshubham.tutor.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.EnrollmentRepository;
import io.github.samarthshubham.tutor.identity.api.AccessPolicy;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.domain.Specification;

class CourseAccessTest {

  private final CourseRepository courses = mock(CourseRepository.class);
  private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
  private final CourseVisibility visibility = mock(CourseVisibility.class);
  private final AccessPolicy studentAccess = mock(AccessPolicy.class);
  private final CourseAccessPolicy policy =
      new CourseAccessPolicy(courses, enrollments, visibility, studentAccess);

  {
    when(visibility.visibleTo(any())).thenReturn(Specification.unrestricted());
    when(visibility.withId(any())).thenReturn(Specification.unrestricted());
  }

  private final UUID courseId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();

  private static CurrentUser user(Role role) {
    return new CurrentUser(UUID.randomUUID(), "kc-" + role, role, UUID.randomUUID());
  }

  @Test
  void aVisibleCourseIsSeenAndAHiddenOneIs404() {
    CurrentUser student = user(Role.STUDENT);
    when(courses.exists(any(Specification.class))).thenReturn(true, false);

    assertThat(policy.canSeeCourse(student, courseId)).isTrue();
    assertThatThrownBy(() -> policy.requireCourseAccess(student, courseId))
        .isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"STUDENT", "TUTOR", "PARENT"})
  void readOnlyRolesNeverManageACourse(Role role) {
    when(courses.exists(any(Specification.class))).thenReturn(true);

    assertThat(policy.canManageCourse(user(role), courseId)).isFalse();
    assertThatThrownBy(() -> policy.requireCourseManagement(user(role), courseId))
        .isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"ADMIN", "TEACHER"})
  void adminsAndTheCoursesTeacherManageAVisibleCourse(Role role) {
    when(courses.exists(any(Specification.class))).thenReturn(true, false);

    assertThat(policy.canManageCourse(user(role), courseId)).isTrue();
    assertThat(policy.canManageCourse(user(role), courseId)).isFalse();
  }

  @Test
  void studentAccessDelegatesToTheIdentityPolicy() {
    CurrentUser tutor = user(Role.TUTOR);
    when(studentAccess.canSeeStudent(tutor, studentId)).thenReturn(true);

    assertThat(policy.canSeeStudent(tutor, studentId)).isTrue();
    policy.requireStudentAccess(tutor, studentId);
  }

  @Test
  void aTeacherSeesAStudentOnlyWhenEnrolledInOneOfTheirCourses() {
    CurrentUser teacher = user(Role.TEACHER);
    when(enrollments.isStudentOfTeacher(teacher.id(), studentId)).thenReturn(true);

    assertThat(policy.canSeeStudent(teacher, studentId)).isTrue();
    assertThat(policy.canSeeStudent(teacher, UUID.randomUUID())).isFalse();
    assertThatThrownBy(() -> policy.requireStudentAccess(teacher, UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"ADMIN", "STUDENT", "TUTOR", "PARENT"})
  void otherRolesGetNoTeacherEnrollmentShortcut(Role role) {
    CurrentUser caller = user(role);
    when(enrollments.isStudentOfTeacher(caller.id(), studentId)).thenReturn(true);

    assertThat(policy.canSeeStudent(caller, studentId)).isFalse();
  }
}
