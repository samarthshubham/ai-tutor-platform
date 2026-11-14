package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserProvisioningService {

  public static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final Duration LAST_SEEN_INTERVAL = Duration.ofMinutes(1);
  static final String USERNAME_TAKEN_MESSAGE =
      "The username belongs to another account; ask an admin to resolve it";

  private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

  private final AppUserRepository users;
  private final Clock clock;

  public UserProvisioningService(AppUserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  public AppUser provision(TokenUser token) {
    Instant now = clock.instant();
    return users
        .findByKeycloakId(token.keycloakId())
        .map(user -> refresh(user, token, now))
        .orElseGet(() -> insert(token, now));
  }

  private AppUser insert(TokenUser token, Instant now) {
    AppUser user =
        new AppUser(
            token.keycloakId(),
            token.username(),
            token.displayName(),
            token.email(),
            token.role(),
            SCHOOL_ID,
            now);
    try {
      AppUser saved = users.saveAndFlush(user);
      log.info("Provisioned app_user {}", saved.getId());
      return saved;
    } catch (DataIntegrityViolationException e) {
      return users
          .findByKeycloakId(token.keycloakId())
          .map(existing -> refresh(existing, token, now))
          .orElseThrow(() -> usernameClashOr(token, e));
    }
  }

  private RuntimeException usernameClashOr(TokenUser token, DataIntegrityViolationException e) {
    return users
        .findByUsername(token.username())
        .<RuntimeException>map(owner -> {
          log.warn(
              "Token subject is new but its username belongs to app_user {}; not relinking",
              owner.getId());
          return new ConflictException(USERNAME_TAKEN_MESSAGE);
        })
        .orElse(e);
  }

  private AppUser refresh(AppUser user, TokenUser token, Instant now) {
    boolean claimsChanged =
        !Objects.equals(user.getDisplayName(), token.displayName())
            || !Objects.equals(user.getEmail(), token.email())
            || user.getRole() != token.role();
    boolean lastSeenStale = !user.getLastSeenAt().isAfter(now.minus(LAST_SEEN_INTERVAL));
    if (!claimsChanged && !lastSeenStale) {
      return user;
    }
    user.update(token.displayName(), token.email(), token.role(), now);
    return users.save(user);
  }
}
