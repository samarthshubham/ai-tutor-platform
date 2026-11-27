package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.course.api.TopicUsage;
import io.github.samarthshubham.tutor.course.domain.Topic;
import io.github.samarthshubham.tutor.course.domain.TopicRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicService {

  static final String DUPLICATE_TOPIC = "This course already has a topic with this name";

  private final TopicRepository topics;
  private final WritableCourses writable;
  private final ObjectProvider<TopicUsage> usages;
  private final Clock clock;

  TopicService(
      TopicRepository topics,
      WritableCourses writable,
      ObjectProvider<TopicUsage> usages,
      Clock clock) {
    this.topics = topics;
    this.writable = writable;
    this.usages = usages;
    this.clock = clock;
  }

  @Transactional
  public TopicResponse add(CurrentUser user, UUID courseId, String name, Integer position) {
    writable.require(user, courseId);
    if (topics.existsByCourseIdAndName(courseId, name)) {
      throw new ConflictException(DUPLICATE_TOPIC);
    }
    int resolved = position != null ? position : topics.maxPosition(courseId) + 1;
    Topic topic = new Topic(courseId, name, resolved, Instant.now(clock));
    saveUnique(topic);
    return toResponse(topic);
  }

  @Transactional
  public TopicResponse update(CurrentUser user, UUID topicId, String name, int position) {
    Topic topic = find(topicId);
    writable.require(user, topic.getCourseId());
    if (topics.existsByCourseIdAndNameAndIdNot(topic.getCourseId(), name, topicId)) {
      throw new ConflictException(DUPLICATE_TOPIC);
    }
    topic.update(name, position);
    saveUnique(topic);
    return toResponse(topic);
  }

  @Transactional
  public void delete(CurrentUser user, UUID topicId) {
    Topic topic = find(topicId);
    writable.require(user, topic.getCourseId());
    if (usages.stream().anyMatch(usage -> usage.isInUse(topicId))) {
      throw new ConflictException("Topic is in use");
    }
    topics.delete(topic);
  }

  private Topic find(UUID topicId) {
    return topics.findById(topicId).orElseThrow(() -> new NotFoundException("Topic not found"));
  }

  private void saveUnique(Topic topic) {
    try {
      topics.saveAndFlush(topic);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(DUPLICATE_TOPIC);
    }
  }

  private static TopicResponse toResponse(Topic topic) {
    return new TopicResponse(topic.getId(), topic.getName(), topic.getPosition());
  }
}
