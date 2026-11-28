package io.github.samarthshubham.tutor.course.api;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CourseQuery {

  Set<UUID> visibleCourseIds(CurrentUser user);

  Optional<CourseRef> findCourse(UUID courseId);

  Optional<TopicRef> findTopic(UUID topicId);

  List<TopicRef> topicsOf(UUID courseId);

  Set<UUID> studentIdsOf(UUID courseId);

  boolean isEnrolled(UUID courseId, UUID studentId);
}
