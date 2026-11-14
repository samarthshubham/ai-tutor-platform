package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.identity.api.Role;

public record TokenUser(
    String keycloakId, String username, String displayName, String email, Role role) {}
