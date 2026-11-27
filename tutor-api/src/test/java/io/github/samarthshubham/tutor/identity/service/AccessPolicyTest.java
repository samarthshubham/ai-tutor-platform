package io.github.samarthshubham.tutor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.identity.api.AccessPolicy;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import io.github.samarthshubham.tutor.identity.domain.GuardianLinkRepository;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccessPolicyTest {
  private final UUID school = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID otherStudent = UUID.randomUUID();
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final GuardianLinkRepository links = mock(GuardianLinkRepository.class);
  private final TutorAssignmentRepository assignments = mock(TutorAssignmentRepository.class);
  private final AccessPolicy policy = new StudentAccessPolicy(users, links, assignments);

  @BeforeEach
  void stubStudentsInSchool() {
    when(users.existsByIdAndSchoolIdAndRole(student, school, Role.STUDENT)).thenReturn(true);
    when(users.existsByIdAndSchoolIdAndRole(otherStudent, school, Role.STUDENT)).thenReturn(true);
  }

  @Test
  void adminCanSeeAnExistingStudentInTheirSchool() {
    policy.requireStudentAccess(caller(Role.ADMIN), student);
  }

  @Test
  void studentCanSeeOnlyTheirOwnStudentRecord() {
    CurrentUser caller = new CurrentUser(student, "self", Role.STUDENT, school);
    assertThat(policy.canSeeStudent(caller, student)).isTrue();
    assertThat(policy.canSeeStudent(caller, otherStudent)).isFalse();
  }

  @Test
  void tutorCanSeeOnlyAssignedStudents() {
    CurrentUser tutor = caller(Role.TUTOR);
    when(assignments.existsByTutorIdAndStudentId(tutor.id(), student)).thenReturn(true);
    assertThat(policy.canSeeStudent(tutor, student)).isTrue();
    assertThat(policy.canSeeStudent(tutor, otherStudent)).isFalse();
  }

  @Test
  void parentCanSeeOnlyLinkedChildren() {
    CurrentUser parent = caller(Role.PARENT);
    when(links.existsByParentIdAndStudentId(parent.id(), student)).thenReturn(true);
    assertThat(policy.canSeeStudent(parent, student)).isTrue();
    assertThat(policy.canSeeStudent(parent, otherStudent)).isFalse();
  }

  @Test
  void teacherHasNoIdentityBasedStudentAccess() {
    assertThat(policy.canSeeStudent(caller(Role.TEACHER), student)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void otherSchoolsStudentsAreHiddenEvenWithARelationship(Role role) {
    CurrentUser outsider = new CurrentUser(student, "outside", role, UUID.randomUUID());
    when(links.existsByParentIdAndStudentId(outsider.id(), student)).thenReturn(true);
    when(assignments.existsByTutorIdAndStudentId(outsider.id(), student)).thenReturn(true);
    assertThat(policy.canSeeStudent(outsider, student)).isFalse();
  }

  @Test
  void hiddenAndMissingIdsRaiseTheSameError() {
    CurrentUser tutor = caller(Role.TUTOR);
    for (UUID id : List.of(otherStudent, UUID.randomUUID())) {
      assertThatThrownBy(() -> policy.requireStudentAccess(tutor, id))
          .isInstanceOf(NotFoundException.class)
          .hasMessage("Student not found");
    }
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void requireSelfChecksTheCallersIdForEveryRole(Role role) {
    CurrentUser caller = caller(role);
    policy.requireSelf(caller, caller.id());
    assertThatThrownBy(() -> policy.requireSelf(caller, student))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("User not found");
  }

  private CurrentUser caller(Role role) {
    return new CurrentUser(UUID.randomUUID(), "caller", role, school);
  }
}
