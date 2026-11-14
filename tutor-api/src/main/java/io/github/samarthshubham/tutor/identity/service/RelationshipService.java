package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.RelationshipQuery;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import io.github.samarthshubham.tutor.identity.domain.AppUserRepository;
import io.github.samarthshubham.tutor.identity.domain.GuardianLink;
import io.github.samarthshubham.tutor.identity.domain.GuardianLinkRepository;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignment;
import io.github.samarthshubham.tutor.identity.domain.TutorAssignmentRepository;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RelationshipService implements RelationshipQuery {

  static final String DUPLICATE_GUARDIAN_LINK = "This parent is already linked to this student";
  static final String DUPLICATE_TUTOR_ASSIGNMENT = "This tutor is already assigned to this student";

  private static final Sort OLDEST_FIRST = Sort.by("createdAt", "id");
  private static final Comparator<UserSummary> BY_DISPLAY_NAME =
      Comparator.comparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(UserSummary::id);

  private final AppUserRepository users;
  private final GuardianLinkRepository guardianLinks;
  private final TutorAssignmentRepository tutorAssignments;
  private final Clock clock;

  public RelationshipService(
      AppUserRepository users,
      GuardianLinkRepository guardianLinks,
      TutorAssignmentRepository tutorAssignments,
      Clock clock) {
    this.users = users;
    this.guardianLinks = guardianLinks;
    this.tutorAssignments = tutorAssignments;
    this.clock = clock;
  }

  @Transactional
  public GuardianLinkResponse createGuardianLink(UUID parentId, UUID studentId, UUID schoolId) {
    requirePair("parentId", parentId, Role.PARENT, studentId, schoolId);
    if (guardianLinks.existsByParentIdAndStudentId(parentId, studentId)) {
      throw new ConflictException(DUPLICATE_GUARDIAN_LINK);
    }
    GuardianLink link = new GuardianLink(parentId, studentId, Instant.now(clock));
    saveUnique(() -> guardianLinks.saveAndFlush(link), DUPLICATE_GUARDIAN_LINK);
    return toResponse(link);
  }

  @Transactional
  public TutorAssignmentResponse createTutorAssignment(
      UUID tutorId, UUID studentId, UUID schoolId) {
    requirePair("tutorId", tutorId, Role.TUTOR, studentId, schoolId);
    if (tutorAssignments.existsByTutorIdAndStudentId(tutorId, studentId)) {
      throw new ConflictException(DUPLICATE_TUTOR_ASSIGNMENT);
    }
    TutorAssignment assignment = new TutorAssignment(tutorId, studentId, Instant.now(clock));
    saveUnique(() -> tutorAssignments.saveAndFlush(assignment), DUPLICATE_TUTOR_ASSIGNMENT);
    return toResponse(assignment);
  }

  @Transactional(readOnly = true)
  public PageResponse<GuardianLinkResponse> listGuardianLinks(
      UUID schoolId, UUID parentId, UUID studentId, int page, int size) {
    Specification<GuardianLink> filter =
        Specification.allOf(
            studentInSchool(schoolId),
            equalTo("parentId", parentId),
            equalTo("studentId", studentId));
    return PageResponse.from(
        guardianLinks
            .findAll(filter, PageRequest.of(page, size, OLDEST_FIRST))
            .map(RelationshipService::toResponse));
  }

  @Transactional(readOnly = true)
  public PageResponse<TutorAssignmentResponse> listTutorAssignments(
      UUID schoolId, UUID tutorId, UUID studentId, int page, int size) {
    Specification<TutorAssignment> filter =
        Specification.allOf(
            studentInSchool(schoolId),
            equalTo("tutorId", tutorId),
            equalTo("studentId", studentId));
    return PageResponse.from(
        tutorAssignments
            .findAll(filter, PageRequest.of(page, size, OLDEST_FIRST))
            .map(RelationshipService::toResponse));
  }

  @Transactional
  public void deleteGuardianLink(UUID id, UUID schoolId) {
    GuardianLink link =
        guardianLinks
            .findById(id)
            .filter(found -> isInSchool(found.getStudentId(), schoolId))
            .orElseThrow(() -> new NotFoundException("Guardian link not found"));
    guardianLinks.delete(link);
  }

  @Transactional
  public void deleteTutorAssignment(UUID id, UUID schoolId) {
    TutorAssignment assignment =
        tutorAssignments
            .findById(id)
            .filter(found -> isInSchool(found.getStudentId(), schoolId))
            .orElseThrow(() -> new NotFoundException("Tutor assignment not found"));
    tutorAssignments.delete(assignment);
  }

  @Transactional(readOnly = true)
  public List<UserSummary> studentsOf(CurrentUser user) {
    Set<UUID> studentIds =
        switch (user.role()) {
          case TUTOR -> studentIdsOfTutor(user.id());
          case PARENT -> studentIdsOfParent(user.id());
          default -> throw new ForbiddenException("Access denied");
        };
    return findUsers(studentIds);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> studentIdsOfParent(UUID parentId) {
    return ids(guardianLinks.findByParentId(parentId), GuardianLink::getStudentId);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> studentIdsOfTutor(UUID tutorId) {
    return ids(tutorAssignments.findByTutorId(tutorId), TutorAssignment::getStudentId);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> parentIdsOfStudent(UUID studentId) {
    return ids(guardianLinks.findByStudentId(studentId), GuardianLink::getParentId);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> tutorIdsOfStudent(UUID studentId) {
    return ids(tutorAssignments.findByStudentId(studentId), TutorAssignment::getTutorId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserSummary> findUser(UUID id) {
    return users.findById(id).map(RelationshipService::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummary> findUsers(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return users.findAllById(Set.copyOf(ids)).stream()
        .map(RelationshipService::toSummary)
        .sorted(BY_DISPLAY_NAME)
        .toList();
  }

  private void requirePair(
      String otherField, UUID otherId, Role otherRole, UUID studentId, UUID schoolId) {
    AppUser other = requireExisting(otherId);
    AppUser student = requireExisting(studentId);
    Map<String, String> fields = new LinkedHashMap<>();
    if (!hasRoleInSchool(other, otherRole, schoolId)) {
      fields.put(otherField, mustBe(otherRole));
    }
    if (!hasRoleInSchool(student, Role.STUDENT, schoolId)) {
      fields.put("studentId", mustBe(Role.STUDENT));
    }
    if (!fields.isEmpty()) {
      throw new ValidationException("Request is not valid", fields);
    }
  }

  private AppUser requireExisting(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private boolean isInSchool(UUID userId, UUID schoolId) {
    return users.findById(userId).filter(user -> user.getSchoolId().equals(schoolId)).isPresent();
  }

  private static boolean hasRoleInSchool(AppUser user, Role role, UUID schoolId) {
    return user.getRole() == role && user.getSchoolId().equals(schoolId);
  }

  private static String mustBe(Role role) {
    return "must be a " + role.name() + " in your school";
  }

  private static void saveUnique(Runnable save, String duplicateMessage) {
    try {
      save.run();
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(duplicateMessage);
    }
  }

  private static <T> Specification<T> studentInSchool(UUID schoolId) {
    return (root, query, cb) -> {
      Subquery<UUID> students = query.subquery(UUID.class);
      var student = students.from(AppUser.class);
      students.select(student.get("id")).where(cb.equal(student.get("schoolId"), schoolId));
      return root.get("studentId").in(students);
    };
  }

  private static <T> Specification<T> equalTo(String attribute, UUID value) {
    return value == null
        ? Specification.unrestricted()
        : (root, query, cb) -> cb.equal(root.get(attribute), value);
  }

  private static <T> Set<UUID> ids(List<T> rows, Function<T, UUID> id) {
    return rows.stream().map(id).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static GuardianLinkResponse toResponse(GuardianLink link) {
    return new GuardianLinkResponse(link.getId(), link.getParentId(), link.getStudentId());
  }

  private static TutorAssignmentResponse toResponse(TutorAssignment assignment) {
    return new TutorAssignmentResponse(
        assignment.getId(), assignment.getTutorId(), assignment.getStudentId());
  }

  static UserSummary toSummary(AppUser user) {
    return new UserSummary(user.getId(), user.getDisplayName(), user.getRole());
  }
}
