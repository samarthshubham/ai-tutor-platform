package io.github.samarthshubham.tutor.cms.api;

import java.util.UUID;

public record LessonRef(
    UUID id,
    UUID courseId,
    UUID topicId,
    String title,
    LessonStatus status,
    Integer publishedVersion) {}
