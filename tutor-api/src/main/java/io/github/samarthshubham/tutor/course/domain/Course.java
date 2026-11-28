package io.github.samarthshubham.tutor.course.domain;

import io.github.samarthshubham.tutor.course.api.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course")
public class Course {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID schoolId;

  @Column(nullable = false)
  private UUID teacherId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 100)
  private String subject;

  @Column(nullable = false)
  private int gradeLevel;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CourseStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected Course() {}

  public Course(
      UUID schoolId,
      UUID teacherId,
      String title,
      String subject,
      int gradeLevel,
      String description,
      Instant now) {
    this.id = UUID.randomUUID();
    this.schoolId = schoolId;
    this.teacherId = teacherId;
    this.title = title;
    this.subject = subject;
    this.gradeLevel = gradeLevel;
    this.description = description;
    this.status = CourseStatus.ACTIVE;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(
      String title, String subject, int gradeLevel, String description, Instant now) {
    this.title = title;
    this.subject = subject;
    this.gradeLevel = gradeLevel;
    this.description = description;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    this.status = CourseStatus.ARCHIVED;
    this.updatedAt = now;
  }

  public boolean isArchived() {
    return status == CourseStatus.ARCHIVED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSchoolId() {
    return schoolId;
  }

  public UUID getTeacherId() {
    return teacherId;
  }

  public String getTitle() {
    return title;
  }

  public String getSubject() {
    return subject;
  }

  public int getGradeLevel() {
    return gradeLevel;
  }

  public String getDescription() {
    return description;
  }

  public CourseStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
