package io.github.samarthshubham.tutor.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardian_link")
public class GuardianLink {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID parentId;

  @Column(nullable = false)
  private UUID studentId;

  @Column(nullable = false)
  private Instant createdAt;

  protected GuardianLink() {}

  public GuardianLink(UUID parentId, UUID studentId, Instant now) {
    this.id = UUID.randomUUID();
    this.parentId = parentId;
    this.studentId = studentId;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getParentId() {
    return parentId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
