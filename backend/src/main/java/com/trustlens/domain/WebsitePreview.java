package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_preview")
public class WebsitePreview {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID auditSessionId;
    public String generatedHtml;
    public String generatedCss;
    public String verificationNotes;
    public String status;
    public String mode;
    public String errorMessage;
    public Instant createdAt;
}
