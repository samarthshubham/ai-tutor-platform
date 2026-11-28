package io.github.samarthshubham.tutor.cms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_version")
public class LessonVersion {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID lessonId;

  @Column(nullable = false)
  private int version;

  @Column(nullable = false)
  private UUID topicId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String bodyMarkdown;

  @Column(nullable = false)
  private UUID publishedBy;

  @Column(nullable = false)
  private Instant createdAt;

  protected LessonVersion() {}

  LessonVersion(
      UUID lessonId,
      int version,
      UUID topicId,
      String title,
      String bodyMarkdown,
      UUID publishedBy,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.lessonId = lessonId;
    this.version = version;
    this.topicId = topicId;
    this.title = title;
    this.bodyMarkdown = bodyMarkdown;
    this.publishedBy = publishedBy;
    this.createdAt = createdAt;
  }

  public UUID getLessonId() {
    return lessonId;
  }

  public int getVersion() {
    return version;
  }

  public UUID getTopicId() {
    return topicId;
  }

  public String getTitle() {
    return title;
  }

  public String getBodyMarkdown() {
    return bodyMarkdown;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
