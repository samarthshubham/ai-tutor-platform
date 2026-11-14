package io.github.samarthshubham.tutor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class UserProvisioningServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
  private static final TokenUser TEACHER =
      new TokenUser("kc-1", "teacher1", "Tara Teacher", "teacher1@tutor.local", Role.TEACHER);

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final UserProvisioningService service =
      new UserProvisioningService(users, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void firstRequestInsertsTheUserInTheSeededSchool() {
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
    when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

    AppUser user = service.provision(TEACHER);

    assertThat(user.getId()).isNotNull();
    assertThat(user.getKeycloakId()).isEqualTo("kc-1");
    assertThat(user.getUsername()).isEqualTo("teacher1");
    assertThat(user.getDisplayName()).isEqualTo("Tara Teacher");
    assertThat(user.getEmail()).isEqualTo("teacher1@tutor.local");
    assertThat(user.getRole()).isEqualTo(Role.TEACHER);
    assertThat(user.getSchoolId()).isEqualTo(UserProvisioningService.SCHOOL_ID);
    assertThat(user.getCreatedAt()).isEqualTo(NOW);
    assertThat(user.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void unchangedUserSeenWithinAMinuteIsNotWritten() {
    AppUser existing = existing(TEACHER, NOW.minusSeconds(30));
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));

    assertThat(service.provision(TEACHER)).isSameAs(existing);

    verify(users, never()).save(any());
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void lastSeenIsUpdatedOnceAMinuteHasPassed() {
    AppUser existing = existing(TEACHER, NOW.minus(Duration.ofMinutes(1)));
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
    when(users.save(existing)).thenReturn(existing);

    service.provision(TEACHER);

    assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
    verify(users).save(existing);
  }

  @Test
  void changedClaimsAreWrittenAtOnce() {
    AppUser existing = existing(TEACHER, NOW.minusSeconds(5));
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
    when(users.save(existing)).thenReturn(existing);

    service.provision(
        new TokenUser("kc-1", "teacher1", "Tara T. Teacher", "tara@tutor.local", Role.TUTOR));

    assertThat(existing.getDisplayName()).isEqualTo("Tara T. Teacher");
    assertThat(existing.getEmail()).isEqualTo("tara@tutor.local");
    assertThat(existing.getRole()).isEqualTo(Role.TUTOR);
    assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void aRoleChangeAloneIsWritten() {
    AppUser existing = existing(TEACHER, NOW.minusSeconds(5));
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
    when(users.save(existing)).thenReturn(existing);

    service.provision(
        new TokenUser("kc-1", "teacher1", "Tara Teacher", "teacher1@tutor.local", Role.ADMIN));

    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void aConcurrentInsertIsResolvedByReadingTheRowAgain() {
    AppUser winner = existing(TEACHER, NOW);
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.empty(), Optional.of(winner));
    when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThat(service.provision(TEACHER)).isSameAs(winner);
  }

  @Test
  void aUsernameHeldByAnotherKeycloakIdIsAConflictAndIsNotRelinked() {
    AppUser previousOwner =
        existing(
            new TokenUser("kc-old", "teacher1", "Tara Teacher", null, Role.TEACHER),
            NOW.minusSeconds(5));
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
    when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("username"));
    when(users.findByUsername("teacher1")).thenReturn(Optional.of(previousOwner));

    assertThatThrownBy(() -> service.provision(TEACHER))
        .isInstanceOf(ConflictException.class)
        .hasMessage(UserProvisioningService.USERNAME_TAKEN_MESSAGE);
    verify(users, never()).save(any());
    assertThat(previousOwner.getKeycloakId()).isEqualTo("kc-old");
  }

  @Test
  void anyOtherConstraintViolationIsRethrown() {
    DataIntegrityViolationException violation = new DataIntegrityViolationException("other");
    when(users.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
    when(users.saveAndFlush(any())).thenThrow(violation);
    when(users.findByUsername("teacher1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.provision(TEACHER)).isSameAs(violation);
  }

  private static AppUser existing(TokenUser token, Instant lastSeenAt) {
    return new AppUser(
        token.keycloakId(),
        token.username(),
        token.displayName(),
        token.email(),
        token.role(),
        UserProvisioningService.SCHOOL_ID,
        lastSeenAt);
  }
}
