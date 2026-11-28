package io.github.samarthshubham.tutor.cms.api;

public enum LessonStatus {
  DRAFT,
  IN_REVIEW,
  PUBLISHED,
  ARCHIVED;

  public boolean canMoveTo(LessonStatus next) {
    return switch (this) {
      case DRAFT -> next == IN_REVIEW || next == ARCHIVED;
      case IN_REVIEW -> next == DRAFT || next == PUBLISHED || next == ARCHIVED;
      case PUBLISHED -> next == DRAFT || next == ARCHIVED;
      case ARCHIVED -> false;
    };
  }
}
