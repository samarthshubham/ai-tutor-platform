package io.github.samarthshubham.tutor.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "topic")
public class Topic {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID courseId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private Instant createdAt;

  protected Topic() {}

  public Topic(UUID courseId, String name, int position, Instant now) {
    this.id = UUID.randomUUID();
    this.courseId = courseId;
    this.name = name;
    this.position = position;
    this.createdAt = now;
  }

  public void update(String name, int position) {
    this.name = name;
    this.position = position;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public String getName() {
    return name;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
