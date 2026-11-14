package io.github.samarthshubham.tutor.identity.api;

import java.util.UUID;

public record UserSummary(UUID id, String displayName, Role role) {}
