package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebsitePreviewRepository extends JpaRepository<WebsitePreview, UUID> {
    java.util.Optional<WebsitePreview> findFirstByAuditSessionIdOrderByCreatedAtDesc(UUID auditSessionId);
}
