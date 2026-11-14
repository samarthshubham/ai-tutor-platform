package io.github.samarthshubham.tutor.identity.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuardianLinkRepository
    extends JpaRepository<GuardianLink, UUID>, JpaSpecificationExecutor<GuardianLink> {

  boolean existsByParentIdAndStudentId(UUID parentId, UUID studentId);

  List<GuardianLink> findByParentId(UUID parentId);

  List<GuardianLink> findByStudentId(UUID studentId);
}
