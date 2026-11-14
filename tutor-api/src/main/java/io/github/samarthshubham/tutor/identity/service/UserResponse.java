package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.UUID;

public record UserResponse(
    UUID id, String username, String displayName, String email, Role role, UUID schoolId) {}
