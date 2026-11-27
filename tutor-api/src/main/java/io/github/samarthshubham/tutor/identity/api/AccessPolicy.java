package io.github.samarthshubham.tutor.identity.api;

import java.util.UUID;

public interface AccessPolicy {
  boolean canSeeStudent(CurrentUser user, UUID studentId);

  void requireStudentAccess(CurrentUser user, UUID studentId);

  void requireSelf(CurrentUser user, UUID userId);
}
