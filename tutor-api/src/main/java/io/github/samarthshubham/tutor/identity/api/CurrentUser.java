package io.github.samarthshubham.tutor.identity.api;

import java.util.UUID;

public record CurrentUser(UUID id, String keycloakId, Role role, UUID schoolId) {}
