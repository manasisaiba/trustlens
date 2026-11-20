package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OwnerApprovalRepository extends JpaRepository<OwnerApproval, UUID> {
    java.util.List<OwnerApproval> findByWebsitePreviewIdOrderByReviewedAtDesc(UUID previewId);
}
