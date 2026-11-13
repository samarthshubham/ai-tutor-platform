package io.github.samarthshubham.tutor.ai.log;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {}
