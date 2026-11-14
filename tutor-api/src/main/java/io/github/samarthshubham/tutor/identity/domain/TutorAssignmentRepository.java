package io.github.samarthshubham.tutor.identity.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TutorAssignmentRepository
    extends JpaRepository<TutorAssignment, UUID>, JpaSpecificationExecutor<TutorAssignment> {

  boolean existsByTutorIdAndStudentId(UUID tutorId, UUID studentId);

  List<TutorAssignment> findByTutorId(UUID tutorId);

  List<TutorAssignment> findByStudentId(UUID studentId);
}
