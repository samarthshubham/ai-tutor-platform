package io.github.samarthshubham.tutor.course.api;

import java.util.UUID;

public record TopicRef(UUID id, UUID courseId, String name, int position) {}
