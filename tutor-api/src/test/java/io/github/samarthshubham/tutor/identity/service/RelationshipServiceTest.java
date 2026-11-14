package io.github.samarthshubham.tutor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import io.github.samarthshubham.tutor.identity.domain.GuardianLink;
import io.github.samarthshubham.tutor.identity.domain.GuardianLinkRepository;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignment;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RelationshipServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
  private static final UUID SCHOOL_ID = UserProvisioningService.SCHOOL_ID;

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final GuardianLinkRepository guardianLinks = mock(GuardianLinkRepository.class);
  private final TutorAssignmentRepository tutorAssignments = mock(TutorAssignmentRepository.class);
  private final RelationshipService service =
      new RelationshipService(
          users, guardianLinks, tutorAssignments, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createsAGuardianLinkBetweenAParentAndAStudent() {
    AppUser parent = user("parent1", "Pat Parent", Role.PARENT, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);

    GuardianLinkResponse link =
        service.createGuardianLink(parent.getId(), student.getId(), SCHOOL_ID);

    assertThat(link.id()).isNotNull();
    assertThat(link.parentId()).isEqualTo(parent.getId());
    assertThat(link.studentId()).isEqualTo(student.getId());
    verify(guardianLinks).saveAndFlush(any(GuardianLink.class));
  }

  @Test
  void aDuplicateGuardianLinkIsAConflict() {
    AppUser parent = user("parent1", "Pat Parent", Role.PARENT, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    when(guardianLinks.existsByParentIdAndStudentId(parent.getId(), student.getId()))
        .thenReturn(true);

    assertThatThrownBy(() -> service.createGuardianLink(parent.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOf(ConflictException.class);
    verify(guardianLinks, never()).saveAndFlush(any());
  }

  @Test
  void aGuardianLinkLostInARaceIsAConflict() {
    AppUser parent = user("parent1", "Pat Parent", Role.PARENT, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    when(guardianLinks.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

    assertThatThrownBy(() -> service.createGuardianLink(parent.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void aNonParentAsParentIdIsAValidationErrorNamingTheField() {
    AppUser teacher = user("teacher1", "Tara Teacher", Role.TEACHER, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);

    assertThatThrownBy(
            () -> service.createGuardianLink(teacher.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.fields()).containsOnlyKeys("parentId"));
  }

  @Test
  void aNonStudentAndAUserOfAnotherSchoolNameBothFields() {
    AppUser outsider = user("parent9", "Other Parent", Role.PARENT, UUID.randomUUID());
    AppUser tutor = user("tutor1", "Tom Tutor", Role.TUTOR, SCHOOL_ID);

    assertThatThrownBy(() -> service.createGuardianLink(outsider.getId(), tutor.getId(), SCHOOL_ID))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e -> assertThat(e.fields()).containsOnlyKeys("parentId", "studentId"));
  }

  @Test
  void anUnknownUserIsNotFound() {
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    UUID unknown = UUID.randomUUID();
    when(users.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createGuardianLink(unknown, student.getId(), SCHOOL_ID))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.createTutorAssignment(unknown, student.getId(), SCHOOL_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void createsATutorAssignment() {
    AppUser tutor = user("tutor1", "Tom Tutor", Role.TUTOR, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);

    TutorAssignmentResponse assignment =
        service.createTutorAssignment(tutor.getId(), student.getId(), SCHOOL_ID);

    assertThat(assignment.tutorId()).isEqualTo(tutor.getId());
    assertThat(assignment.studentId()).isEqualTo(student.getId());
    verify(tutorAssignments).saveAndFlush(any(TutorAssignment.class));
  }

  @Test
  void aDuplicateOrRacedTutorAssignmentIsAConflict() {
    AppUser tutor = user("tutor1", "Tom Tutor", Role.TUTOR, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    when(tutorAssignments.existsByTutorIdAndStudentId(tutor.getId(), student.getId()))
        .thenReturn(true, false);
    when(tutorAssignments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

    assertThatThrownBy(
            () -> service.createTutorAssignment(tutor.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () -> service.createTutorAssignment(tutor.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void aNonTutorAsTutorIdIsAValidationErrorNamingTheField() {
    AppUser parent = user("parent1", "Pat Parent", Role.PARENT, SCHOOL_ID);
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);

    assertThatThrownBy(
            () -> service.createTutorAssignment(parent.getId(), student.getId(), SCHOOL_ID))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e ->
                assertThat(e.fields())
                    .isEqualTo(Map.of("tutorId", "must be a TUTOR in your school")));
  }

  @Test
  void deletesALinkOfTheSchoolAndHidesOthers() {
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    GuardianLink link = new GuardianLink(UUID.randomUUID(), student.getId(), NOW);
    when(guardianLinks.findById(link.getId())).thenReturn(Optional.of(link));

    service.deleteGuardianLink(link.getId(), SCHOOL_ID);
    verify(guardianLinks).delete(link);

    assertThatThrownBy(() -> service.deleteGuardianLink(link.getId(), UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
    UUID missing = UUID.randomUUID();
    when(guardianLinks.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteGuardianLink(missing, SCHOOL_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deletesATutorAssignmentOfTheSchoolAndHidesOthers() {
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    TutorAssignment assignment = new TutorAssignment(UUID.randomUUID(), student.getId(), NOW);
    when(tutorAssignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));

    service.deleteTutorAssignment(assignment.getId(), SCHOOL_ID);
    verify(tutorAssignments).delete(assignment);

    UUID missing = UUID.randomUUID();
    when(tutorAssignments.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteTutorAssignment(missing, SCHOOL_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void queriesReturnLinkedIdsInBothDirections() {
    UUID parent = UUID.randomUUID();
    UUID tutor = UUID.randomUUID();
    UUID student = UUID.randomUUID();
    when(guardianLinks.findByParentId(parent))
        .thenReturn(List.of(new GuardianLink(parent, student, NOW)));
    when(guardianLinks.findByStudentId(student))
        .thenReturn(List.of(new GuardianLink(parent, student, NOW)));
    when(tutorAssignments.findByTutorId(tutor))
        .thenReturn(List.of(new TutorAssignment(tutor, student, NOW)));
    when(tutorAssignments.findByStudentId(student))
        .thenReturn(List.of(new TutorAssignment(tutor, student, NOW)));

    assertThat(service.studentIdsOfParent(parent)).containsExactly(student);
    assertThat(service.parentIdsOfStudent(student)).containsExactly(parent);
    assertThat(service.studentIdsOfTutor(tutor)).containsExactly(student);
    assertThat(service.tutorIdsOfStudent(student)).containsExactly(tutor);
  }

  @Test
  void studentsOfATutorAreSortedByDisplayName() {
    AppUser tutor = user("tutor1", "Tom Tutor", Role.TUTOR, SCHOOL_ID);
    AppUser zoe = user("student2", "Zoe Student", Role.STUDENT, SCHOOL_ID);
    AppUser amy = user("student3", "amy Student", Role.STUDENT, SCHOOL_ID);
    when(tutorAssignments.findByTutorId(tutor.getId()))
        .thenReturn(
            List.of(
                new TutorAssignment(tutor.getId(), zoe.getId(), NOW),
                new TutorAssignment(tutor.getId(), amy.getId(), NOW)));
    when(users.findAllById(Set.of(zoe.getId(), amy.getId()))).thenReturn(List.of(zoe, amy));

    assertThat(service.studentsOf(current(tutor)))
        .containsExactly(
            new UserSummary(amy.getId(), "amy Student", Role.STUDENT),
            new UserSummary(zoe.getId(), "Zoe Student", Role.STUDENT));
  }

  @Test
  void aParentWithoutChildrenGetsAnEmptyListWithoutAUserQuery() {
    AppUser parent = user("parent2", "Paula Parent", Role.PARENT, SCHOOL_ID);
    when(guardianLinks.findByParentId(parent.getId())).thenReturn(List.of());

    assertThat(service.studentsOf(current(parent))).isEmpty();
    verify(users, never()).findAllById(any());
  }

  @Test
  void studentsOfAnyOtherRoleIsForbidden() {
    AppUser teacher = user("teacher1", "Tara Teacher", Role.TEACHER, SCHOOL_ID);

    assertThatThrownBy(() -> service.studentsOf(current(teacher)))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(guardianLinks, tutorAssignments);
  }

  @Test
  void findUserReturnsTheSummaryOrEmpty() {
    AppUser student = user("student1", "Sam Student", Role.STUDENT, SCHOOL_ID);
    UUID missing = UUID.randomUUID();
    when(users.findById(missing)).thenReturn(Optional.empty());

    assertThat(service.findUser(student.getId()))
        .contains(new UserSummary(student.getId(), "Sam Student", Role.STUDENT));
    assertThat(service.findUser(missing)).isEmpty();
  }

  private AppUser user(String username, String displayName, Role role, UUID schoolId) {
    AppUser user = new AppUser("kc-" + username, username, displayName, null, role, schoolId, NOW);
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    return user;
  }

  private static CurrentUser current(AppUser user) {
    return new CurrentUser(user.getId(), user.getKeycloakId(), user.getRole(), user.getSchoolId());
  }
}
