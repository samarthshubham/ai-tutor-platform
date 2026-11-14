package io.github.samarthshubham.tutor.identity.service;

import java.util.UUID;

public record TutorAssignmentResponse(UUID id, UUID tutorId, UUID studentId) {}
