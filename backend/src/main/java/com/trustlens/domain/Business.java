package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business")
public class Business {
    @Id
    public UUID id = UUID.randomUUID();
    public String name;
    public String websiteUrl;
    public String category;
    public String description;
    public Instant createdAt;
}
