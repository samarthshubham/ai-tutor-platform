package io.github.samarthshubham.tutor.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollment")
@IdClass(EnrollmentId.class)
public class Enrollment {

  @Id private UUID courseId;

  @Id private UUID studentId;

  @Column(nullable = false)
  private Instant createdAt;

  protected Enrollment() {}

  public Enrollment(UUID courseId, UUID studentId, Instant now) {
    this.courseId = courseId;
    this.studentId = studentId;
    this.createdAt = now;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
