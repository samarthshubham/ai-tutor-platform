package io.github.samarthshubham.tutor.course.service;

import java.util.UUID;

public record EnrollmentResponse(UUID courseId, UUID studentId) {}
