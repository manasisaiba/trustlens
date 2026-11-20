CREATE TABLE business (
    id            UUID PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    website_url   VARCHAR(2000) NOT NULL,
    category      VARCHAR(200)  NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE audit_session (
    id                    UUID PRIMARY KEY,
    business_id           UUID         NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    status                VARCHAR(40)  NOT NULL,
    mode                  VARCHAR(10)  NOT NULL,
    overall_score         INTEGER,
    score_explanation     TEXT,
    progress_message      TEXT,
    error_message         TEXT,
    baseline_screenshot   VARCHAR(500),
    report_summary        TEXT,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_business ON audit_session(business_id);

CREATE TABLE persona (
    id                UUID PRIMARY KEY,
    audit_session_id  UUID         NOT NULL REFERENCES audit_session(id) ON DELETE CASCADE,
    order_index       INTEGER      NOT NULL,
    name              VARCHAR(200) NOT NULL,
    background        TEXT         NOT NULL,
    objective         TEXT         NOT NULL,
    look_for          TEXT         NOT NULL
);
CREATE INDEX idx_persona_audit ON persona(audit_session_id);

CREATE TABLE journey_task (
    id                UUID PRIMARY KEY,
    persona_id        UUID         NOT NULL REFERENCES persona(id) ON DELETE CASCADE,
    audit_session_id  UUID         NOT NULL REFERENCES audit_session(id) ON DELETE CASCADE,
    description       TEXT         NOT NULL,
    success_criteria  TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    action_count      INTEGER      NOT NULL DEFAULT 0,
    outcome_summary   TEXT,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_task_audit ON journey_task(audit_session_id);

CREATE TABLE browser_evidence (
    id               UUID PRIMARY KEY,
    journey_task_id  UUID          NOT NULL REFERENCES journey_task(id) ON DELETE CASCADE,
    step_index       INTEGER       NOT NULL,
    page_url         VARCHAR(2000),
    page_title       VARCHAR(500),
    action           VARCHAR(40)   NOT NULL,
    action_input     TEXT,
    reasoning        TEXT,
    observation      TEXT,
    page_text_excerpt TEXT,
    screenshot_path  VARCHAR(500),
    success          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_evidence_task ON browser_evidence(journey_task_id);

CREATE TABLE audit_finding (
    id                 UUID PRIMARY KEY,
    audit_session_id   UUID          NOT NULL REFERENCES audit_session(id) ON DELETE CASCADE,
    title              VARCHAR(300)  NOT NULL,
    description        TEXT          NOT NULL,
    severity           VARCHAR(10)   NOT NULL,
    source             VARCHAR(20)   NOT NULL,
    affected_url       VARCHAR(2000),
    persona_name       VARCHAR(200),
    evidence_ids       TEXT          NOT NULL,
    evidence_summary   TEXT,
    screenshot_path    VARCHAR(500),
    friction_reason    TEXT,
    recommendation     TEXT          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_finding_audit ON audit_finding(audit_session_id);

CREATE TABLE website_preview (
    id                  UUID PRIMARY KEY,
    audit_session_id    UUID         NOT NULL REFERENCES audit_session(id) ON DELETE CASCADE,
    generated_html      TEXT,
    generated_css       TEXT,
    verification_notes  TEXT,
    status              VARCHAR(20)  NOT NULL,
    mode                VARCHAR(10)  NOT NULL,
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_preview_audit ON website_preview(audit_session_id);

CREATE TABLE proposed_change (
    id                  UUID PRIMARY KEY,
    website_preview_id  UUID         NOT NULL REFERENCES website_preview(id) ON DELETE CASCADE,
    order_index         INTEGER      NOT NULL,
    title               VARCHAR(300) NOT NULL,
    description         TEXT         NOT NULL,
    related_finding     VARCHAR(300),
    needs_verification  BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)  NOT NULL
);

CREATE TABLE owner_approval (
    id                  UUID PRIMARY KEY,
    website_preview_id  UUID         NOT NULL REFERENCES website_preview(id) ON DELETE CASCADE,
    status              VARCHAR(20)  NOT NULL,
    notes               TEXT,
    reviewed_at         TIMESTAMPTZ  NOT NULL
);
