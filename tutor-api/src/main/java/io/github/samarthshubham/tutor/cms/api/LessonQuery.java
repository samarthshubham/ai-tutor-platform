package io.github.samarthshubham.tutor.cms.api;

import java.util.Optional;
import java.util.UUID;

public interface LessonQuery {

  Optional<PublishedLesson> findPublished(UUID lessonId);

  Optional<LessonRef> findLesson(UUID lessonId);
}
