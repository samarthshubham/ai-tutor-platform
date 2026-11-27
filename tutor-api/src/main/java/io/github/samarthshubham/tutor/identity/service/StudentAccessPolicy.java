package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.identity.api.AccessPolicy;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import io.github.samarthshubham.tutor.identity.domain.GuardianLinkRepository;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class StudentAccessPolicy implements AccessPolicy {

  private final AppUserRepository users;
  private final GuardianLinkRepository guardianLinks;
  private final TutorAssignmentRepository tutorAssignments;

  StudentAccessPolicy(
      AppUserRepository users,
      GuardianLinkRepository guardianLinks,
      TutorAssignmentRepository tutorAssignments) {
    this.users = users;
    this.guardianLinks = guardianLinks;
    this.tutorAssignments = tutorAssignments;
  }

  @Override
  public boolean canSeeStudent(CurrentUser user, UUID studentId) {
    if (!users.existsByIdAndSchoolIdAndRole(studentId, user.schoolId(), Role.STUDENT)) {
      return false;
    }
    return switch (user.role()) {
      case ADMIN -> true;
      case STUDENT -> user.id().equals(studentId);
      case TUTOR -> tutorAssignments.existsByTutorIdAndStudentId(user.id(), studentId);
      case PARENT -> guardianLinks.existsByParentIdAndStudentId(user.id(), studentId);
      case TEACHER -> false;
    };
  }

  @Override
  public void requireStudentAccess(CurrentUser user, UUID studentId) {
    if (!canSeeStudent(user, studentId)) {
      throw new NotFoundException("Student not found");
    }
  }

  @Override
  public void requireSelf(CurrentUser user, UUID userId) {
    if (!user.id().equals(userId)) {
      throw new NotFoundException("User not found");
    }
  }
}
