package io.github.samarthshubham.tutor.cms.api;

import java.util.UUID;

public record LessonPublished(UUID lessonId, int version, UUID courseId, UUID topicId) {}
