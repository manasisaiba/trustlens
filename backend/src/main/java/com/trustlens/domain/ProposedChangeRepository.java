package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProposedChangeRepository extends JpaRepository<ProposedChange, UUID> {
    java.util.List<ProposedChange> findByWebsitePreviewIdOrderByOrderIndex(UUID previewId);
}
