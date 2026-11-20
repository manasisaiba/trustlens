package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposed_change")
public class ProposedChange {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID websitePreviewId;
    public int orderIndex;
    public String title;
    public String description;
    public String relatedFinding;
    public boolean needsVerification;
    public String status;
}
