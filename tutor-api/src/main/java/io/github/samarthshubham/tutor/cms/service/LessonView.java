package io.github.samarthshubham.tutor.cms.service;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.cms.domain.Lesson;
import io.github.samarthshubham.tutor.cms.domain.LessonVersion;
import java.time.Instant;
import java.util.UUID;

public sealed interface LessonView permits LessonView.Staff, LessonView.Published {

  record Staff(
      UUID id,
      UUID courseId,
      UUID topicId,
      String title,
      LessonStatus status,
      String bodyMarkdown,
      Integer publishedVersion,
      String reviewComment,
      UUID authorId,
      Instant updatedAt,
      Instant publishedAt)
      implements LessonView {}

  record Published(
      UUID id,
      UUID courseId,
      UUID topicId,
      String title,
      String bodyMarkdown,
      int version,
      Instant publishedAt)
      implements LessonView {}

  static Staff staff(Lesson lesson) {
    return new Staff(
        lesson.getId(),
        lesson.getCourseId(),
        lesson.getTopicId(),
        lesson.getTitle(),
        lesson.getStatus(),
        lesson.getBodyMarkdown(),
        lesson.getPublishedVersion(),
        lesson.getReviewComment(),
        lesson.getAuthorId(),
        lesson.getUpdatedAt(),
        lesson.getPublishedAt());
  }

  static Published published(UUID courseId, LessonVersion version) {
    return new Published(
        version.getLessonId(),
        courseId,
        version.getTopicId(),
        version.getTitle(),
        version.getBodyMarkdown(),
        version.getVersion(),
        version.getCreatedAt());
  }
}
