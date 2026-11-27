package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.course.api.CourseQuery;
import io.github.samarthshubham.tutor.course.api.CourseRef;
import io.github.samarthshubham.tutor.course.api.TopicRef;
import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.course.domain.Enrollment;
import io.github.samarthshubham.tutor.course.domain.EnrollmentId;
import io.github.samarthshubham.tutor.course.domain.EnrollmentRepository;
import io.github.samarthshubham.tutor.course.domain.Topic;
import io.github.samarthshubham.tutor.course.domain.TopicRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CourseQueryService implements CourseQuery {

  private final CourseRepository courses;
  private final TopicRepository topics;
  private final EnrollmentRepository enrollments;
  private final CourseVisibility visibility;

  CourseQueryService(
      CourseRepository courses,
      TopicRepository topics,
      EnrollmentRepository enrollments,
      CourseVisibility visibility) {
    this.courses = courses;
    this.topics = topics;
    this.enrollments = enrollments;
    this.visibility = visibility;
  }

  @Override
  public Set<UUID> visibleCourseIds(CurrentUser user) {
    return courses.findAll(visibility.visibleTo(user)).stream()
        .map(Course::getId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Optional<CourseRef> findCourse(UUID courseId) {
    return courses.findById(courseId).map(CourseQueryService::toRef);
  }

  @Override
  public Optional<TopicRef> findTopic(UUID topicId) {
    return topics.findById(topicId).map(CourseQueryService::toRef);
  }

  @Override
  public List<TopicRef> topicsOf(UUID courseId) {
    return topics.findByCourseIdOrderByPositionAscNameAsc(courseId).stream()
        .map(CourseQueryService::toRef)
        .toList();
  }

  @Override
  public Set<UUID> studentIdsOf(UUID courseId) {
    return enrollments.findByCourseId(courseId).stream()
        .map(Enrollment::getStudentId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public boolean isEnrolled(UUID courseId, UUID studentId) {
    return enrollments.existsById(new EnrollmentId(courseId, studentId));
  }

  private static CourseRef toRef(Course course) {
    return new CourseRef(
        course.getId(), course.getTitle(), course.getTeacherId(), course.getStatus());
  }

  private static TopicRef toRef(Topic topic) {
    return new TopicRef(topic.getId(), topic.getCourseId(), topic.getName(), topic.getPosition());
  }
}
