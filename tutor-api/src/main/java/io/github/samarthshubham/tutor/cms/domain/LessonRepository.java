package io.github.samarthshubham.tutor.cms.domain;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

  boolean existsByTopicId(UUID topicId);

  Page<Lesson> findByCourseId(UUID courseId, Pageable pageable);

  Page<Lesson> findByCourseIdAndStatus(UUID courseId, LessonStatus status, Pageable pageable);

  Page<Lesson> findByCourseIdInAndStatus(
      Set<UUID> courseIds, LessonStatus status, Pageable pageable);
}
