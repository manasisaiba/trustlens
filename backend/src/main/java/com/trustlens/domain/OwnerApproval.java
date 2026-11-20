package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owner_approval")
public class OwnerApproval {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID websitePreviewId;
    public String status;
    public String notes;
    public Instant reviewedAt;
}
