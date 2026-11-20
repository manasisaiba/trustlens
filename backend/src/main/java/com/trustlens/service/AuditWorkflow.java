package com.trustlens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustlens.ai.PersonaGenerator;
import com.trustlens.browser.WorkerClient;
import com.trustlens.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Long-running audit steps, executed asynchronously. The frontend polls progress. */
@Service
public class AuditWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AuditWorkflow.class);

    private final AuditSessionRepository audits;
    private final BusinessRepository businesses;
    private final PersonaRepository personas;
    private final JourneyTaskRepository tasks;
    private final WebsitePreviewRepository previews;
    private final PersonaGenerator personaGenerator;
    private final TestingOrchestrator orchestrator;
    private final ReportGenerator reports;
    private final PreviewGenerator previewGenerator;
    private final WorkerClient worker;
    private final ObjectMapper mapper;

    public AuditWorkflow(AuditSessionRepository audits, BusinessRepository businesses, PersonaRepository personas,
                         JourneyTaskRepository tasks, WebsitePreviewRepository previews, PersonaGenerator personaGenerator,
                         TestingOrchestrator orchestrator, ReportGenerator reports, PreviewGenerator previewGenerator,
                         WorkerClient worker, ObjectMapper mapper) {
        this.audits = audits;
        this.businesses = businesses;
        this.personas = personas;
        this.tasks = tasks;
        this.previews = previews;
        this.personaGenerator = personaGenerator;
        this.orchestrator = orchestrator;
        this.reports = reports;
        this.previewGenerator = previewGenerator;
        this.worker = worker;
        this.mapper = mapper;
    }

    @Async("auditExecutor")
    public void generatePersonas(UUID auditId) {
        AuditSession audit = audits.findById(auditId).orElseThrow();
        Business business = businesses.findById(audit.businessId).orElseThrow();
        try {
            progress(audit, "Generating customer personas for a " + business.category + "…");
            List<PersonaGenerator.GeneratedPersona> generated = Status.LIVE.equals(audit.mode)
                    ? personaGenerator.generateLive(business, null)
                    : personaGenerator.generateDemo(business);
            for (Persona old : personas.findByAuditSessionIdOrderByOrderIndex(audit.id)) personas.delete(old);
            int i = 1;
            for (var g : generated) {
                Persona p = new Persona();
                p.auditSessionId = audit.id;
                p.orderIndex = i++;
                p.name = g.name();
                p.background = g.background();
                p.objective = g.objective();
                p.lookFor = mapper.writeValueAsString(g.lookFor());
                personas.save(p);
                JourneyTask t = new JourneyTask();
                t.personaId = p.id;
                t.auditSessionId = audit.id;
                t.description = g.taskDescription();
                t.successCriteria = g.successCriteria();
                t.status = Status.PENDING;
                tasks.save(t);
            }
            audit.status = Status.PERSONAS_READY;
            audit.progressMessage = generated.size() + " personas ready.";
            audit.errorMessage = null;
            audits.save(audit);
        } catch (Exception e) {
            fail(audit, "Persona generation failed: " + e.getMessage());
        }
    }

    @Async("auditExecutor")
    public void runTesting(UUID auditId) {
        AuditSession audit = audits.findById(auditId).orElseThrow();
        Business business = businesses.findById(audit.businessId).orElseThrow();
        try {
            audit.startedAt = Instant.now();
            progress(audit, "Opening " + business.websiteUrl + " in Chromium…");
            if (!worker.healthy()) throw new IllegalStateException("The browser worker is not reachable. Check the worker container.");
            try {
                orchestrator.captureBaseline(audit, business);
            } catch (WorkerClient.WorkerException e) {
                throw new IllegalStateException("The website could not be opened in the browser: " + e.getMessage());
            }
            progress(audit, "Running persona journeys in the browser…");
            orchestrator.runAllJourneys(audit, business);
            audit.status = Status.ANALYZING;
            progress(audit, Status.LIVE.equals(audit.mode) ? "Claude is analysing the browser evidence…" : "Building findings from browser evidence…");
            reports.generate(audit, business);
            audit = audits.findById(auditId).orElseThrow();
            audit.status = Status.REPORT_READY;
            audit.completedAt = Instant.now();
            audit.progressMessage = "Report ready.";
            audits.save(audit);
        } catch (Exception e) {
            log.error("Audit {} failed", auditId, e);
            fail(audits.findById(auditId).orElse(audit), e.getMessage());
        }
    }

    @Async("auditExecutor")
    public void generatePreview(UUID auditId, UUID previewId) {
        AuditSession audit = audits.findById(auditId).orElseThrow();
        Business business = businesses.findById(audit.businessId).orElseThrow();
        WebsitePreview preview = previews.findById(previewId).orElseThrow();
        try {
            previewGenerator.generate(audit, business, preview);
        } catch (Exception e) {
            log.error("Preview generation failed", e);
            preview.status = Status.FAILED;
            preview.errorMessage = "Preview generation failed: " + e.getMessage();
            previews.save(preview);
        }
    }

    private void progress(AuditSession audit, String msg) {
        audit.progressMessage = msg;
        audits.save(audit);
    }

    private void fail(AuditSession audit, String msg) {
        audit.status = Status.FAILED;
        audit.errorMessage = msg;
        audit.progressMessage = null;
        audits.save(audit);
    }
}
