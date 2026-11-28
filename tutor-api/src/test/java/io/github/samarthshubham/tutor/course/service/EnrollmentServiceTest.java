package io.github.samarthshubham.tutor.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.Enrollment;
import io.github.samarthshubham.tutor.course.domain.EnrollmentId;
import io.github.samarthshubham.tutor.course.domain.EnrollmentRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.RelationshipQuery;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EnrollmentServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

  private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
  private final CourseRepository courses = mock(CourseRepository.class);
  private final CourseAccess access = mock(CourseAccess.class);
  private final RelationshipQuery relationships = mock(RelationshipQuery.class);
  private final EnrollmentService service =
      new EnrollmentService(
          enrollments,
          access,
          new WritableCourses(courses, access),
          relationships,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private final CurrentUser teacher =
      new CurrentUser(UUID.randomUUID(), "kc-t", Role.TEACHER, UUID.randomUUID());
  private final Course course =
      new Course(teacher.schoolId(), teacher.id(), "T", "S", 5, null, NOW);
  private final UUID student = UUID.randomUUID();

  @Test
  void enrollsAStudentOfTheSchool() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(relationships.hasRoleInSchool(student, Role.STUDENT, teacher.schoolId())).thenReturn(true);

    EnrollmentResponse enrolled = service.enroll(teacher, course.getId(), student);

    assertThat(enrolled).isEqualTo(new EnrollmentResponse(course.getId(), student));
    verify(enrollments).saveAndFlush(any(Enrollment.class));
  }

  @Test
  void enrollingANonStudentIs400NamingTheField() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));

    assertThatThrownBy(() -> service.enroll(teacher, course.getId(), student))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.fields()).containsKey("studentId"));
  }

  @Test
  void enrollingTwiceIsAConflict() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(relationships.hasRoleInSchool(student, Role.STUDENT, teacher.schoolId())).thenReturn(true);
    when(enrollments.existsById(new EnrollmentId(course.getId(), student))).thenReturn(true);

    assertThatThrownBy(() -> service.enroll(teacher, course.getId(), student))
        .isInstanceOf(ConflictException.class);
    verify(enrollments, never()).saveAndFlush(any());
  }

  @Test
  void anEnrollmentLostInARaceIsAConflict() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(relationships.hasRoleInSchool(student, Role.STUDENT, teacher.schoolId())).thenReturn(true);
    when(enrollments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.enroll(teacher, course.getId(), student))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void anArchivedCourseRejectsEnrollmentWrites() {
    course.archive(NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));

    assertThatThrownBy(() -> service.enroll(teacher, course.getId(), student))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.remove(teacher, course.getId(), student))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void removesAnEnrollmentAndAMissingOneIs404() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    EnrollmentId id = new EnrollmentId(course.getId(), student);
    when(enrollments.existsById(id)).thenReturn(true);

    service.remove(teacher, course.getId(), student);
    verify(enrollments).deleteById(id);

    UUID other = UUID.randomUUID();
    assertThatThrownBy(() -> service.remove(teacher, course.getId(), other))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listsTheEnrolledStudents() {
    when(enrollments.findByCourseId(course.getId()))
        .thenReturn(List.of(new Enrollment(course.getId(), student, NOW)));
    UserSummary summary = new UserSummary(student, "Sam Student", Role.STUDENT);
    when(relationships.findUsers(List.of(student))).thenReturn(List.of(summary));

    assertThat(service.students(teacher, course.getId())).containsExactly(summary);
    verify(access).requireCourseManagement(teacher, course.getId());
  }
}
