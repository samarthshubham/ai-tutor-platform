package io.github.samarthshubham.tutor.identity.domain;

import io.github.samarthshubham.tutor.identity.api.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String keycloakId;

  @Column(nullable = false, unique = true, length = 100)
  private String username;

  @Column(nullable = false, length = 200)
  private String displayName;

  @Column(length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Role role;

  @Column(nullable = false)
  private UUID schoolId;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant lastSeenAt;

  protected AppUser() {}

  public AppUser(
      String keycloakId,
      String username,
      String displayName,
      String email,
      Role role,
      UUID schoolId,
      Instant now) {
    this.id = UUID.randomUUID();
    this.keycloakId = keycloakId;
    this.username = username;
    this.displayName = displayName;
    this.email = email;
    this.role = role;
    this.schoolId = schoolId;
    this.createdAt = now;
    this.lastSeenAt = now;
  }

  public void update(String displayName, String email, Role role, Instant lastSeenAt) {
    this.displayName = displayName;
    this.email = email;
    this.role = role;
    this.lastSeenAt = lastSeenAt;
  }

  public UUID getId() {
    return id;
  }

  public String getKeycloakId() {
    return keycloakId;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  public UUID getSchoolId() {
    return schoolId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }
}
