package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "browser_evidence")
public class BrowserEvidence {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID journeyTaskId;
    public int stepIndex;
    public String pageUrl;
    public String pageTitle;
    public String action;
    public String actionInput;
    public String reasoning;
    public String observation;
    public String pageTextExcerpt;
    public String screenshotPath;
    public boolean success;
    public Instant createdAt;
}
