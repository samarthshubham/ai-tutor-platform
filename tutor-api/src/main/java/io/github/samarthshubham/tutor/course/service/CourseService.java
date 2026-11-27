package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

  private static final Sort BY_TITLE = Sort.by("title", "id");

  private final CourseRepository courses;
  private final TopicRepository topics;
  private final CourseAccess access;
  private final CourseVisibility visibility;
  private final WritableCourses writable;
  private final RelationshipQuery relationships;
  private final Clock clock;

  CourseService(
      CourseRepository courses,
      TopicRepository topics,
      CourseAccess access,
      CourseVisibility visibility,
      WritableCourses writable,
      RelationshipQuery relationships,
      Clock clock) {
    this.courses = courses;
    this.topics = topics;
    this.access = access;
    this.visibility = visibility;
    this.writable = writable;
    this.relationships = relationships;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<CourseResponse> list(CurrentUser user, int page, int size) {
    Page<Course> found =
        courses.findAll(visibility.visibleTo(user), PageRequest.of(page, size, BY_TITLE));
    return PageResponse.from(found.map(toResponses(found.getContent())));
  }

  @Transactional(readOnly = true)
  public CourseResponse get(CurrentUser user, UUID courseId) {
    access.requireCourseAccess(user, courseId);
    Course course =
        courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
    return toResponses(List.of(course)).apply(course);
  }

  @Transactional
  public CourseResponse create(
      CurrentUser user,
      String title,
      String subject,
      int gradeLevel,
      String description,
      UUID teacherId) {
    UUID owner = user.role() == Role.TEACHER ? user.id() : requireTeacher(teacherId, user);
    Course course =
        new Course(
            user.schoolId(), owner, title, subject, gradeLevel, description, Instant.now(clock));
    courses.save(course);
    return toResponses(List.of(course)).apply(course);
  }

  @Transactional
  public CourseResponse update(
      CurrentUser user,
      UUID courseId,
      String title,
      String subject,
      int gradeLevel,
      String description) {
    Course course = writable.require(user, courseId);
    course.update(title, subject, gradeLevel, description, Instant.now(clock));
    return toResponses(List.of(course)).apply(course);
  }

  @Transactional
  public CourseResponse archive(CurrentUser user, UUID courseId) {
    Course course = writable.require(user, courseId);
    course.archive(Instant.now(clock));
    return toResponses(List.of(course)).apply(course);
  }

  private UUID requireTeacher(UUID teacherId, CurrentUser user) {
    if (teacherId == null) {
      throw new ValidationException("Request is not valid", Map.of("teacherId", "is required"));
    }
    if (!relationships.hasRoleInSchool(teacherId, Role.TEACHER, user.schoolId())) {
      throw new ValidationException(
          "Request is not valid", Map.of("teacherId", "must be a TEACHER in your school"));
    }
    return teacherId;
  }

  private Function<Course, CourseResponse> toResponses(Collection<Course> found) {
    List<UUID> courseIds = found.stream().map(Course::getId).toList();
    Map<UUID, List<TopicResponse>> topicsByCourse =
        courseIds.isEmpty()
            ? Map.of()
            : topics.findByCourseIdInOrderByPositionAscNameAsc(courseIds).stream()
                .collect(
                    Collectors.groupingBy(
                        Topic::getCourseId,
                        Collectors.mapping(
                            topic ->
                                new TopicResponse(
                                    topic.getId(), topic.getName(), topic.getPosition()),
                            Collectors.toList())));
    Map<UUID, String> teacherNames =
        relationships
            .findUsers(found.stream().map(Course::getTeacherId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(UserSummary::id, UserSummary::displayName));
    return course ->
        new CourseResponse(
            course.getId(),
            course.getTitle(),
            course.getSubject(),
            course.getGradeLevel(),
            course.getDescription(),
            course.getTeacherId(),
            teacherNames.get(course.getTeacherId()),
            course.getStatus(),
            topicsByCourse.getOrDefault(course.getId(), List.of()));
  }
}
