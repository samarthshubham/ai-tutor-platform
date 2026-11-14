package io.github.samarthshubham.tutor.identity.service;

import java.util.UUID;

public record GuardianLinkResponse(UUID id, UUID parentId, UUID studentId) {}
