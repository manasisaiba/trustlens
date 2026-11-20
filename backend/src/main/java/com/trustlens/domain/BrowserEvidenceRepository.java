package com.trustlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BrowserEvidenceRepository extends JpaRepository<BrowserEvidence, UUID> {
    java.util.List<BrowserEvidence> findByJourneyTaskIdOrderByStepIndex(UUID journeyTaskId);
}
