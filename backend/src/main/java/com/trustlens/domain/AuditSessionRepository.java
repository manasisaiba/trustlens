package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditSessionRepository extends JpaRepository<AuditSession, UUID> {
    java.util.List<AuditSession> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);
    java.util.List<AuditSession> findTop20ByOrderByCreatedAtDesc();
}
