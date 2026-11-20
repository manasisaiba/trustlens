package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JourneyTaskRepository extends JpaRepository<JourneyTask, UUID> {
    java.util.List<JourneyTask> findByAuditSessionId(UUID auditSessionId);
    java.util.Optional<JourneyTask> findByPersonaId(UUID personaId);
}
