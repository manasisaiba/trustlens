package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_finding")
public class AuditFinding {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID auditSessionId;
    public String title;
    public String description;
    public String severity;
    public String source;
    public String affectedUrl;
    public String personaName;
    public String evidenceIds;
    public String evidenceSummary;
    public String screenshotPath;
    public String frictionReason;
    public String recommendation;
    public Instant createdAt;
}
