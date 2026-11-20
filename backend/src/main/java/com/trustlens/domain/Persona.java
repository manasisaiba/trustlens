package com.trustlens.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "persona")
public class Persona {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID auditSessionId;
    public int orderIndex;
    public String name;
    public String background;
    public String objective;
    public String lookFor;
}
