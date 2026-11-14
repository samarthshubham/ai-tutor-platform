package io.github.samarthshubham.tutor.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository
    extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

  Optional<AppUser> findByKeycloakId(String keycloakId);

  Optional<AppUser> findByUsername(String username);
}
