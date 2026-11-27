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
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.api.TopicUsage;
import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.Topic;
import io.github.samarthshubham.tutor.course.domain.TopicRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

class TopicServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

  private final TopicRepository topics = mock(TopicRepository.class);
  private final CourseRepository courses = mock(CourseRepository.class);
  private final CourseAccess access = mock(CourseAccess.class);
  private final TopicUsage usage = mock(TopicUsage.class);
  private final ObjectProvider<TopicUsage> usages = mock(ObjectProvider.class);
  private final TopicService service =
      new TopicService(
          topics, new WritableCourses(courses, access), usages, Clock.fixed(NOW, ZoneOffset.UTC));

  private final CurrentUser teacher =
      new CurrentUser(UUID.randomUUID(), "kc-t", Role.TEACHER, UUID.randomUUID());
  private final Course course =
      new Course(teacher.schoolId(), teacher.id(), "T", "S", 5, null, NOW);

  @Test
  void addsATopicAtTheGivenPosition() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));

    TopicResponse topic = service.add(teacher, course.getId(), "Cells", 3);

    assertThat(topic.name()).isEqualTo("Cells");
    assertThat(topic.position()).isEqualTo(3);
    verify(topics).saveAndFlush(any(Topic.class));
  }

  @Test
  void aTopicWithoutAPositionGoesLast() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.maxPosition(course.getId())).thenReturn(4);

    assertThat(service.add(teacher, course.getId(), "Cells", null).position()).isEqualTo(5);
  }

  @Test
  void aDuplicateNameIsAConflict() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.existsByCourseIdAndName(course.getId(), "Cells")).thenReturn(true);

    assertThatThrownBy(() -> service.add(teacher, course.getId(), "Cells", 1))
        .isInstanceOf(ConflictException.class);
    verify(topics, never()).saveAndFlush(any());
  }

  @Test
  void aDuplicateLostInARaceIsAConflict() {
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.add(teacher, course.getId(), "Cells", 1))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void anArchivedCourseRejectsTopicWrites() {
    course.archive(NOW);
    Topic topic = new Topic(course.getId(), "Cells", 1, NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.findById(topic.getId())).thenReturn(Optional.of(topic));

    assertThatThrownBy(() -> service.add(teacher, course.getId(), "New", 1))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.update(teacher, topic.getId(), "Renamed", 2))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.delete(teacher, topic.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void renamesAndReordersATopic() {
    Topic topic = new Topic(course.getId(), "Cells", 1, NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.findById(topic.getId())).thenReturn(Optional.of(topic));

    TopicResponse updated = service.update(teacher, topic.getId(), "Organelles", 2);

    assertThat(updated.name()).isEqualTo("Organelles");
    assertThat(updated.position()).isEqualTo(2);
  }

  @Test
  void renamingToAnotherTopicsNameIsAConflict() {
    Topic topic = new Topic(course.getId(), "Cells", 1, NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.findById(topic.getId())).thenReturn(Optional.of(topic));
    when(topics.existsByCourseIdAndNameAndIdNot(course.getId(), "Atoms", topic.getId()))
        .thenReturn(true);

    assertThatThrownBy(() -> service.update(teacher, topic.getId(), "Atoms", 1))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void deletesAnUnusedTopic() {
    Topic topic = new Topic(course.getId(), "Cells", 1, NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.findById(topic.getId())).thenReturn(Optional.of(topic));
    when(usages.stream()).thenAnswer(call -> java.util.stream.Stream.of(usage));
    when(usage.isInUse(topic.getId())).thenReturn(false);

    service.delete(teacher, topic.getId());

    verify(topics).delete(topic);
  }

  @Test
  void aTopicInUseCannotBeDeleted() {
    Topic topic = new Topic(course.getId(), "Cells", 1, NOW);
    when(courses.findById(course.getId())).thenReturn(Optional.of(course));
    when(topics.findById(topic.getId())).thenReturn(Optional.of(topic));
    when(usages.stream()).thenAnswer(call -> java.util.stream.Stream.of(usage));
    when(usage.isInUse(topic.getId())).thenReturn(true);

    assertThatThrownBy(() -> service.delete(teacher, topic.getId()))
        .isInstanceOf(ConflictException.class);
    verify(topics, never()).delete(any(Topic.class));
  }

  @Test
  void aMissingTopicIs404() {
    UUID id = UUID.randomUUID();
    when(topics.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(teacher, id)).isInstanceOf(NotFoundException.class);
  }
}
