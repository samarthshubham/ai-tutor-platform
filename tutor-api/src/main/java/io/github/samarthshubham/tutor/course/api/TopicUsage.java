package io.github.samarthshubham.tutor.course.api;

import java.util.UUID;

public interface TopicUsage {

  boolean isInUse(UUID topicId);
}
