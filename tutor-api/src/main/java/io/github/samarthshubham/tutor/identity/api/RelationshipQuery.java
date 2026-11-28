package io.github.samarthshubham.tutor.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RelationshipQuery {

  Set<UUID> studentIdsOfParent(UUID parentId);

  Set<UUID> studentIdsOfTutor(UUID tutorId);

  Set<UUID> parentIdsOfStudent(UUID studentId);

  Set<UUID> tutorIdsOfStudent(UUID studentId);

  boolean hasRoleInSchool(UUID userId, Role role, UUID schoolId);

  Optional<UserSummary> findUser(UUID id);

  List<UserSummary> searchStudents(UUID schoolId, String query);

  List<UserSummary> findUsers(Collection<UUID> ids);
}
