package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_session")
public class AuditSession {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID businessId;
    public String status;
    public String mode;
    public Integer overallScore;
    public String scoreExplanation;
    public String progressMessage;
    public String errorMessage;
    public String baselineScreenshot;
    public String reportSummary;
    public Instant startedAt;
    public Instant completedAt;
    public Instant createdAt;
}
