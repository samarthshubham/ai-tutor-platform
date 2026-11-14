package io.github.samarthshubham.tutor.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tutor_assignment")
public class TutorAssignment {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID tutorId;

  @Column(nullable = false)
  private UUID studentId;

  @Column(nullable = false)
  private Instant createdAt;

  protected TutorAssignment() {}

  public TutorAssignment(UUID tutorId, UUID studentId, Instant now) {
    this.id = UUID.randomUUID();
    this.tutorId = tutorId;
    this.studentId = studentId;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
