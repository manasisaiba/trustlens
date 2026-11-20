package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PersonaRepository extends JpaRepository<Persona, UUID> {
    java.util.List<Persona> findByAuditSessionIdOrderByOrderIndex(UUID auditSessionId);
    void deleteByAuditSessionId(UUID auditSessionId);
}
