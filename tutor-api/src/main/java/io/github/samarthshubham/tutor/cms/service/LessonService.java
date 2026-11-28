package io.github.samarthshubham.tutor.cms.service;

import static io.github.samarthshubham.tutor.cms.api.LessonStatus.ARCHIVED;
import static io.github.samarthshubham.tutor.cms.api.LessonStatus.IN_REVIEW;
import static io.github.samarthshubham.tutor.cms.api.LessonStatus.PUBLISHED;

import io.github.samarthshubham.tutor.cms.api.LessonArchived;
import io.github.samarthshubham.tutor.cms.api.LessonPublished;
import io.github.samarthshubham.tutor.cms.api.LessonQuery;
import io.github.samarthshubham.tutor.cms.api.LessonRef;
import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.cms.api.PublishedLesson;
import io.github.samarthshubham.tutor.cms.domain.Lesson;
import io.github.samarthshubham.tutor.cms.domain.LessonRepository;
import io.github.samarthshubham.tutor.cms.domain.LessonVersion;
import io.github.samarthshubham.tutor.cms.domain.LessonVersionRepository;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.api.CourseQuery;
import io.github.samarthshubham.tutor.course.api.TopicUsage;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LessonService implements LessonQuery, TopicUsage {

  private final LessonRepository lessons;
  private final LessonVersionRepository versions;
  private final CourseAccess access;
  private final CourseQuery courses;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  LessonService(
      LessonRepository lessons,
      LessonVersionRepository versions,
      CourseAccess access,
      CourseQuery courses,
      ApplicationEventPublisher events,
      Clock clock) {
    this.lessons = lessons;
    this.versions = versions;
    this.access = access;
    this.courses = courses;
    this.events = events;
    this.clock = clock;
  }

  public PageResponse<? extends LessonView> list(
      CurrentUser user, UUID courseId, LessonStatus status, int page, int size) {
    access.requireCourseAccess(user, courseId);
    if (isStaff(user)) {
      Pageable pageable = staffPage(page, size);
      Page<Lesson> result =
          status == null
              ? lessons.findByCourseId(courseId, pageable)
              : lessons.findByCourseIdAndStatus(courseId, status, pageable);
      return PageResponse.from(result.map(LessonView::staff));
    }
    Pageable pageable = PageRequest.of(page, size);
    if (status != null && status != PUBLISHED) {
      return PageResponse.from(Page.empty(pageable));
    }
    return PageResponse.from(
        versions
            .findPublishedForCourse(courseId, ARCHIVED, pageable)
            .map(version -> LessonView.published(courseId, version)));
  }

  public PageResponse<LessonView.Staff> reviewQueue(CurrentUser user, int page, int size) {
    var courseIds = courses.visibleCourseIds(user);
    Pageable pageable = staffPage(page, size);
    if (courseIds.isEmpty()) {
      return PageResponse.from(Page.empty(pageable));
    }
    return PageResponse.from(
        lessons.findByCourseIdInAndStatus(courseIds, IN_REVIEW, pageable).map(LessonView::staff));
  }

  public LessonView get(CurrentUser user, UUID id) {
    Lesson lesson = find(id);
    if (!access.canSeeCourse(user, lesson.getCourseId())) {
      throw notFound();
    }
    if (isStaff(user)) {
      return LessonView.staff(lesson);
    }
    return currentVersion(lesson)
        .map(version -> LessonView.published(lesson.getCourseId(), version))
        .orElseThrow(LessonService::notFound);
  }

  public PageResponse<LessonView.Published> versions(
      CurrentUser user, UUID id, int page, int size) {
    Lesson lesson = requireManaged(user, id);
    return PageResponse.from(
        versions
            .findByLessonIdOrderByVersionDesc(id, PageRequest.of(page, size))
            .map(version -> LessonView.published(lesson.getCourseId(), version)));
  }

  @Transactional
  public LessonView.Staff create(
      CurrentUser user, UUID courseId, UUID topicId, String title, String bodyMarkdown) {
    access.requireCourseManagement(user, courseId);
    requireTopic(courseId, topicId);
    Lesson lesson = new Lesson(courseId, topicId, user.id(), title, bodyMarkdown, clock.instant());
    return LessonView.staff(lessons.save(lesson));
  }

  @Transactional
  public LessonView.Staff edit(
      CurrentUser user, UUID id, UUID topicId, String title, String bodyMarkdown) {
    Lesson lesson = requireManaged(user, id);
    lesson.edit(topicId, title, bodyMarkdown, clock.instant());
    requireTopic(lesson.getCourseId(), topicId);
    flushLesson();
    return LessonView.staff(lesson);
  }

  @Transactional
  public LessonView.Staff submit(CurrentUser user, UUID id) {
    Lesson lesson = requireManaged(user, id);
    lesson.submit(clock.instant());
    flushLesson();
    return LessonView.staff(lesson);
  }

  @Transactional
  public LessonView.Staff requestChanges(CurrentUser user, UUID id, String comment) {
    Lesson lesson = requireManaged(user, id);
    lesson.requestChanges(comment, clock.instant());
    flushLesson();
    return LessonView.staff(lesson);
  }

  @Transactional
  public LessonView.Staff publish(CurrentUser user, UUID id) {
    Lesson lesson = requireManaged(user, id);
    LessonVersion version = lesson.publish(user.id(), clock.instant());
    flushLesson();
    versions.save(version);
    events.publishEvent(
        new LessonPublished(id, version.getVersion(), lesson.getCourseId(), version.getTopicId()));
    return LessonView.staff(lesson);
  }

  @Transactional
  public LessonView.Staff revise(CurrentUser user, UUID id) {
    Lesson lesson = requireManaged(user, id);
    lesson.revise(clock.instant());
    flushLesson();
    return LessonView.staff(lesson);
  }

  @Transactional
  public LessonView.Staff archive(CurrentUser user, UUID id) {
    Lesson lesson = requireManaged(user, id);
    lesson.archive(clock.instant());
    flushLesson();
    events.publishEvent(new LessonArchived(id));
    return LessonView.staff(lesson);
  }

  @Override
  public Optional<PublishedLesson> findPublished(UUID id) {
    return lessons
        .findById(id)
        .flatMap(lesson ->
            currentVersion(lesson)
                .map(version ->
                    new PublishedLesson(
                        id,
                        version.getVersion(),
                        version.getTitle(),
                        lesson.getCourseId(),
                        version.getTopicId(),
                        version.getBodyMarkdown())));
  }

  @Override
  public Optional<LessonRef> findLesson(UUID id) {
    return lessons
        .findById(id)
        .map(lesson ->
            new LessonRef(
                id,
                lesson.getCourseId(),
                lesson.getTopicId(),
                lesson.getTitle(),
                lesson.getStatus(),
                lesson.getPublishedVersion()));
  }

  @Override
  public boolean isInUse(UUID topicId) {
    return lessons.existsByTopicId(topicId) || versions.existsByTopicId(topicId);
  }

  private Optional<LessonVersion> currentVersion(Lesson lesson) {
    if (lesson.getStatus() == ARCHIVED || lesson.getPublishedVersion() == null) {
      return Optional.empty();
    }
    return versions.findByLessonIdAndVersion(lesson.getId(), lesson.getPublishedVersion());
  }

  private Lesson find(UUID id) {
    return lessons.findById(id).orElseThrow(LessonService::notFound);
  }

  private Lesson requireManaged(CurrentUser user, UUID id) {
    Lesson lesson = find(id);
    if (!access.canManageCourse(user, lesson.getCourseId())) {
      throw notFound();
    }
    return lesson;
  }

  private void requireTopic(UUID courseId, UUID topicId) {
    if (courses.findTopic(topicId).filter(topic -> topic.courseId().equals(courseId)).isEmpty()) {
      throw new ValidationException(
          "Request is not valid", Map.of("topicId", "Must belong to this course"));
    }
  }

  private void flushLesson() {
    try {
      lessons.flush();
    } catch (OptimisticLockingFailureException e) {
      throw new ConflictException("The lesson was changed by another request");
    }
  }

  private static Pageable staffPage(int page, int size) {
    return PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
  }

  private static boolean isStaff(CurrentUser user) {
    return user.role() == Role.ADMIN || user.role() == Role.TEACHER;
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Lesson not found");
  }
}
