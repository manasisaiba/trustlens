package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journey_task")
public class JourneyTask {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID personaId;
    public UUID auditSessionId;
    public String description;
    public String successCriteria;
    public String status;
    public int actionCount;
    public String outcomeSummary;
    public Instant startedAt;
    public Instant completedAt;
}
