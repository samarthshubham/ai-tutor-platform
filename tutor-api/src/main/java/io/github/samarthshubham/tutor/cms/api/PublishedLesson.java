package io.github.samarthshubham.tutor.cms.api;

import java.util.UUID;

public record PublishedLesson(
    UUID lessonId, int version, String title, UUID courseId, UUID topicId, String bodyMarkdown) {}
