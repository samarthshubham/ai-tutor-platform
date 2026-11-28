package io.github.samarthshubham.tutor.course.api;

import java.util.UUID;

public record CourseRef(UUID id, String title, UUID teacherId, CourseStatus status) {}
