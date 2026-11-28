package io.github.samarthshubham.tutor.cms.domain;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LessonVersionRepository extends JpaRepository<LessonVersion, UUID> {

  boolean existsByTopicId(UUID topicId);

  Optional<LessonVersion> findByLessonIdAndVersion(UUID lessonId, int version);

  Page<LessonVersion> findByLessonIdOrderByVersionDesc(UUID lessonId, Pageable pageable);

  @Query(
      """
      select v from LessonVersion v join Lesson l
        on l.id = v.lessonId and l.publishedVersion = v.version
      where l.courseId = :courseId and l.status <> :archived
      order by v.createdAt desc, v.lessonId
      """)
  Page<LessonVersion> findPublishedForCourse(
      UUID courseId, LessonStatus archived, Pageable pageable);
}
