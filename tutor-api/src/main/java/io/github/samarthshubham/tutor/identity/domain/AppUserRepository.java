package io.github.samarthshubham.tutor.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByKeycloakId(String keycloakId);
}
