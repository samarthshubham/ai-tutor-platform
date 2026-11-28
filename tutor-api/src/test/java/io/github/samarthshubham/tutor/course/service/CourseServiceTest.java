package io.github.samarthshubham.tutor.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.api.CourseStatus;
import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.Topic;
import io.github.samarthshubham.tutor.course.domain.TopicRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class CourseServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
  private static final UUID SCHOOL = UUID.randomUUID();

  private final CourseRepository courses = mock(CourseRepository.class);
  private final TopicRepository topics = mock(TopicRepository.class);
  private final CourseAccess access = mock(CourseAccess.class);
  private final CourseVisibility visibility = mock(CourseVisibility.class);
  private final RelationshipQuery relationships = mock(RelationshipQuery.class);
  private final CourseService service =
      new CourseService(
          courses,
          topics,
          access,
          visibility,
          new WritableCourses(courses, access),
          relationships,
          Clock.fixed(NOW, ZoneOffset.UTC));

  {
    when(visibility.visibleTo(any())).thenReturn(Specification.unrestricted());
    when(visibility.withId(any())).thenReturn(Specification.unrestricted());
  }

  private final CurrentUser teacher =
      new CurrentUser(UUID.randomUUID(), "kc-t", Role.TEACHER, SCHOOL);
  private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), "kc-a", Role.ADMIN, SCHOOL);

  @Test
  void aTeacherBecomesTheTeacherOfTheCourseTheyCreate() {
    UUID ignored = UUID.randomUUID();
    when(relationships.findUsers(List.of(teacher.id())))
        .thenReturn(List.of(new UserSummary(teacher.id(), "Tara Teacher", Role.TEACHER)));

    CourseResponse created =
        service.create(teacher, "Grade 8 Science", "Science", 8, null, ignored);

    assertThat(created.teacherId()).isEqualTo(teacher.id());
    assertThat(created.teacherName()).isEqualTo("Tara Teacher");
    assertThat(created.status()).isEqualTo(CourseStatus.ACTIVE);
    assertThat(created.topics()).isEmpty();
    verify(courses).save(any(Course.class));
  }

  @Test
  void anAdminCreatesACourseForANamedTeacherInTheSchool() {
    UUID teacherId = UUID.randomUUID();
    when(relationships.hasRoleInSchool(teacherId, Role.TEACHER, SCHOOL)).thenReturn(true);

    CourseResponse created = service.create(admin, "Maths", "Maths", 6, "d", teacherId);

    assertThat(created.teacherId()).isEqualTo(teacherId);
  }

  @Test
  void anAdminMustNameATeacher() {
    assertThatThrownBy(() -> service.create(admin, "Maths", "Maths", 6, null, null))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e -> assertThat(e.fields()).containsEntry("teacherId", "is required"));
  }

  @Test
  void theNamedTeacherMustBeATeacherInTheSchool() {
    UUID notATeacher = UUID.randomUUID();
    assertThatThrownBy(() -> service.create(admin, "Maths", "Maths", 6, null, notATeacher))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.fields()).containsKey("teacherId"));
  }

  @Test
  void listingLoadsTopicsAndTeacherNamesInOneQueryEach() {
    Course first = course(teacher.id());
    Course second = course(teacher.id());
    when(courses.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(first, second)));
    when(topics.findByCourseIdInOrderByPositionAscNameAsc(any()))
        .thenReturn(List.of(new Topic(first.getId(), "Cells", 1, NOW)));
    when(relationships.findUsers(List.of(teacher.id())))
        .thenReturn(List.of(new UserSummary(teacher.id(), "Tara Teacher", Role.TEACHER)));

    PageResponse<CourseResponse> page = service.list(teacher, 0, 20);

    assertThat(page.content()).hasSize(2);
    assertThat(page.content().get(0).topics())
        .extracting(TopicResponse::name)
        .containsExactly("Cells");
    assertThat(page.content().get(1).topics()).isEmpty();
    verify(topics).findByCourseIdInOrderByPositionAscNameAsc(any());
  }

  @Test
  void anEmptyPageSkipsTheTopicQuery() {
    when(courses.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    assertThat(service.list(teacher, 0, 20).content()).isEmpty();
  }

  @Test
  void aHiddenCourseIs404() {
    UUID id = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new NotFoundException("Course not found"))
        .when(access)
        .requireCourseAccess(teacher, id);

    assertThatThrownBy(() -> service.get(teacher, id)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updatesAnActiveCourse() {
    Course course = course(teacher.id());
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));

    CourseResponse updated = service.update(teacher, course.getId(), "New", "Sub", 9, "desc");

    assertThat(updated.title()).isEqualTo("New");
    assertThat(updated.gradeLevel()).isEqualTo(9);
  }

  @Test
  void archivesACourseAndRejectsFurtherWrites() {
    Course course = course(teacher.id());
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));

    assertThat(service.archive(teacher, course.getId()).status()).isEqualTo(CourseStatus.ARCHIVED);
    assertThatThrownBy(() -> service.update(teacher, course.getId(), "x", "y", 1, null))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.archive(teacher, course.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void aMissingCourseIs404() {
    UUID id = UUID.randomUUID();
    when(courses.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.archive(teacher, id)).isInstanceOf(NotFoundException.class);
  }

  private static Course course(UUID teacherId) {
    return new Course(SCHOOL, teacherId, "Title", "Subject", 7, null, NOW);
  }
}
