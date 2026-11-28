package io.github.samarthshubham.tutor.course.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

  boolean existsByCourseIdAndName(UUID courseId, String name);

  boolean existsByCourseIdAndNameAndIdNot(UUID courseId, String name, UUID id);

  List<Topic> findByCourseIdInOrderByPositionAscNameAsc(Collection<UUID> courseIds);

  List<Topic> findByCourseIdOrderByPositionAscNameAsc(UUID courseId);

  @Query("select coalesce(max(t.position), 0) from Topic t where t.courseId = :courseId")
  int maxPosition(UUID courseId);
}
