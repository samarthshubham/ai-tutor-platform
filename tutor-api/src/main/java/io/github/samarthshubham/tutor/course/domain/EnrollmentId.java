package io.github.samarthshubham.tutor.course.domain;

import java.io.Serializable;
import java.util.UUID;

public record EnrollmentId(UUID courseId, UUID studentId) implements Serializable {}
