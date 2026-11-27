package io.github.samarthshubham.tutor.course.service;

import java.util.UUID;

public record TopicResponse(UUID id, String name, int position) {}
