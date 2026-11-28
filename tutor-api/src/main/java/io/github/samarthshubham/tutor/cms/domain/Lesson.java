package io.github.samarthshubham.tutor.cms.domain;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Lesson {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID courseId;

  @Column(nullable = false)
  private UUID topicId;

  @Column(nullable = false)
  private UUID authorId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String bodyMarkdown;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LessonStatus status;

  private Integer publishedVersion;

  @Column(length = 2000)
  private String reviewComment;

  private Instant publishedAt;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Lesson() {}

  public Lesson(
      UUID courseId, UUID topicId, UUID authorId, String title, String bodyMarkdown, Instant now) {
    this.id = UUID.randomUUID();
    this.courseId = courseId;
    this.topicId = topicId;
    this.authorId = authorId;
    this.title = title;
    this.bodyMarkdown = bodyMarkdown;
    this.status = LessonStatus.DRAFT;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void requireDraft() {
    requireStatus(LessonStatus.DRAFT, "edit");
  }

  public void edit(UUID topicId, String title, String bodyMarkdown, Instant now) {
    requireDraft();
    this.topicId = topicId;
    this.title = title;
    this.bodyMarkdown = bodyMarkdown;
    this.updatedAt = now;
  }

  public void submit(Instant now) {
    moveTo(LessonStatus.IN_REVIEW, "submit", now);
    reviewComment = null;
  }

  public void requestChanges(String comment, Instant now) {
    requireStatus(LessonStatus.IN_REVIEW, "request changes for");
    moveTo(LessonStatus.DRAFT, "request changes for", now);
    reviewComment = comment;
  }

  public LessonVersion publish(UUID publisherId, Instant now) {
    moveTo(LessonStatus.PUBLISHED, "publish", now);
    publishedVersion = publishedVersion == null ? 1 : publishedVersion + 1;
    publishedAt = now;
    return new LessonVersion(id, publishedVersion, topicId, title, bodyMarkdown, publisherId, now);
  }

  public void revise(Instant now) {
    requireStatus(LessonStatus.PUBLISHED, "revise");
    moveTo(LessonStatus.DRAFT, "revise", now);
  }

  public void archive(Instant now) {
    moveTo(LessonStatus.ARCHIVED, "archive", now);
  }

  private void requireStatus(LessonStatus expected, String action) {
    if (status != expected) {
      throw conflict(action);
    }
  }

  private void moveTo(LessonStatus next, String action, Instant now) {
    if (!status.canMoveTo(next)) {
      throw conflict(action);
    }
    status = next;
    updatedAt = now;
  }

  private ConflictException conflict(String action) {
    return new ConflictException("Cannot " + action + " a lesson in status " + status);
  }

  public UUID getId() {
    return id;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public UUID getTopicId() {
    return topicId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getTitle() {
    return title;
  }

  public String getBodyMarkdown() {
    return bodyMarkdown;
  }

  public LessonStatus getStatus() {
    return status;
  }

  public Integer getPublishedVersion() {
    return publishedVersion;
  }

  public String getReviewComment() {
    return reviewComment;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
