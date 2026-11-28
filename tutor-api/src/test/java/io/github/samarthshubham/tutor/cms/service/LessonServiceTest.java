package io.github.samarthshubham.tutor.cms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.cms.api.LessonPublished;
import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.cms.domain.Lesson;
import io.github.samarthshubham.tutor.cms.domain.LessonRepository;
import io.github.samarthshubham.tutor.cms.domain.LessonVersion;
import io.github.samarthshubham.tutor.cms.domain.LessonVersionRepository;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.api.CourseQuery;
import io.github.samarthshubham.tutor.course.api.TopicRef;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-28T06:23:00Z");
  private static final UUID SCHOOL = UUID.randomUUID();
  private static final UUID COURSE = UUID.randomUUID();
  private static final UUID TOPIC = UUID.randomUUID();
  private static final CurrentUser TEACHER =
      new CurrentUser(UUID.randomUUID(), "teacher", Role.TEACHER, SCHOOL);
  private static final CurrentUser ADMIN =
      new CurrentUser(UUID.randomUUID(), "admin", Role.ADMIN, SCHOOL);

  @Mock LessonRepository lessons;
  @Mock LessonVersionRepository versions;
  @Mock CourseAccess access;
  @Mock CourseQuery courses;
  @Mock ApplicationEventPublisher events;

  private LessonService service;
  private final Lesson draft = new Lesson(COURSE, TOPIC, TEACHER.id(), "Title", "Body", NOW);

  @BeforeEach
  void setUp() {
    service =
        new LessonService(
            lessons, versions, access, courses, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createRejectsATopicOwnedByAnotherCourse() {
    when(courses.findTopic(TOPIC))
        .thenReturn(Optional.of(new TopicRef(TOPIC, UUID.randomUUID(), "Other", 1)));
    assertThatThrownBy(() -> service.create(TEACHER, COURSE, TOPIC, "Title", "Body"))
        .isInstanceOfSatisfying(
            ValidationException.class, error -> assertThat(error.fields()).containsKey("topicId"));
    verifyNoInteractions(lessons);
  }

  @Test
  void editHidesLessonsTheTeacherCannotManage() {
    when(lessons.findById(draft.getId())).thenReturn(Optional.of(draft));
    assertThatThrownBy(() -> service.edit(TEACHER, draft.getId(), TOPIC, "New", "New body"))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(courses);
  }

  @Test
  void publishingFlushesTheOptimisticLockBeforeInsertingTheVersionAndRecordingTheEvent() {
    draft.submit(NOW);
    allowManagement(ADMIN);
    assertThat(service.publish(ADMIN, draft.getId()).publishedVersion()).isEqualTo(1);
    var order = inOrder(lessons, versions, events);
    order.verify(lessons).flush();
    order.verify(versions).save(any(LessonVersion.class));
    order.verify(events).publishEvent(new LessonPublished(draft.getId(), 1, COURSE, TOPIC));
  }

  @Test
  void aStalePublisherGetsConflictWithoutWritingASnapshotOrEvent() {
    draft.submit(NOW);
    allowManagement(ADMIN);
    doThrow(new OptimisticLockingFailureException("stale")).when(lessons).flush();
    assertThatThrownBy(() -> service.publish(ADMIN, draft.getId()))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(versions, events);
  }

  @Test
  void schoolWithNoCoursesHasAnEmptyReviewQueue() {
    when(courses.visibleCourseIds(ADMIN)).thenReturn(Set.of());
    assertThat(service.reviewQueue(ADMIN, 0, 20).content()).isEmpty();
    verifyNoInteractions(lessons);
  }

  @Test
  void publishedVersionLookupRejectsArchivedLessonsButKeepsTheLessonReference() {
    draft.submit(NOW);
    draft.publish(ADMIN.id(), NOW);
    draft.archive(NOW);
    when(lessons.findById(draft.getId())).thenReturn(Optional.of(draft));
    assertThat(service.findPublished(draft.getId())).isEmpty();
    assertThat(service.findLesson(draft.getId()).orElseThrow().status())
        .isEqualTo(LessonStatus.ARCHIVED);
    verifyNoInteractions(versions);
  }

  private void allowManagement(CurrentUser user) {
    when(lessons.findById(draft.getId())).thenReturn(Optional.of(draft));
    when(access.canManageCourse(user, COURSE)).thenReturn(true);
  }
}
