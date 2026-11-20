package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, UUID> {
    java.util.List<AuditFinding> findByAuditSessionId(UUID auditSessionId);
    void deleteByAuditSessionId(UUID auditSessionId);
}
