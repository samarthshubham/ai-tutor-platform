package io.github.samarthshubham.tutor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final UserService service = new UserService(users);

  @Test
  void returnsTheUserShape() {
    AppUser user =
        new AppUser(
            "kc-1",
            "parent1",
            "Pat Parent",
            "parent1@tutor.local",
            Role.PARENT,
            UserProvisioningService.SCHOOL_ID,
            Instant.parse("2026-11-14T10:00:00Z"));
    when(users.findById(user.getId())).thenReturn(Optional.of(user));

    assertThat(service.get(user.getId()))
        .isEqualTo(
            new UserResponse(
                user.getId(),
                "parent1",
                "Pat Parent",
                "parent1@tutor.local",
                Role.PARENT,
                UserProvisioningService.SCHOOL_ID));
  }

  @Test
  void unknownIdIsNotFound() {
    UUID id = UUID.randomUUID();
    when(users.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
  }
}
