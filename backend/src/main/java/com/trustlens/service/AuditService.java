package com.trustlens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustlens.config.AppProperties;
import com.trustlens.domain.*;
import com.trustlens.security.UrlGuard;
import com.trustlens.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class AuditService {
    private final AppProperties props;
    private final UrlGuard urlGuard;
    private final ObjectMapper mapper;
    private final BusinessRepository businesses;
    private final AuditSessionRepository audits;
    private final PersonaRepository personas;
    private final JourneyTaskRepository tasks;
    private final BrowserEvidenceRepository evidence;
    private final AuditFindingRepository findings;
    private final WebsitePreviewRepository previews;
    private final ProposedChangeRepository changes;
    private final OwnerApprovalRepository approvals;
    private final AuditWorkflow workflow;

    public AuditService(AppProperties props, UrlGuard urlGuard, ObjectMapper mapper, BusinessRepository businesses,
                        AuditSessionRepository audits, PersonaRepository personas, JourneyTaskRepository tasks,
                        BrowserEvidenceRepository evidence, AuditFindingRepository findings, WebsitePreviewRepository previews,
                        ProposedChangeRepository changes, OwnerApprovalRepository approvals, AuditWorkflow workflow) {
        this.props = props;
        this.urlGuard = urlGuard;
        this.mapper = mapper;
        this.businesses = businesses;
        this.audits = audits;
        this.personas = personas;
        this.tasks = tasks;
        this.evidence = evidence;
        this.findings = findings;
        this.previews = previews;
        this.changes = changes;
        this.approvals = approvals;
        this.workflow = workflow;
    }

    // ------------------------------------------------------------ commands

    public Business createBusiness(String name, String websiteUrl, String category, String description) {
        Business b = new Business();
        b.name = name.trim();
        b.websiteUrl = urlGuard.normalizeAndValidate(websiteUrl);
        b.category = category.trim();
        b.description = description == null ? null : description.trim();
        b.createdAt = Instant.now();
        return businesses.save(b);
    }

    public AuditSession createAudit(UUID businessId, String requestedMode) {
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business"));
        String mode = requestedMode == null || requestedMode.isBlank() ? (props.claudeConfigured() ? Status.LIVE : Status.DEMO)
                : requestedMode.toUpperCase(Locale.ROOT);
        if (!Set.of(Status.LIVE, Status.DEMO).contains(mode)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODE", "mode must be LIVE or DEMO");
        if (Status.LIVE.equals(mode) && !props.claudeConfigured()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAUDE_NOT_CONFIGURED",
                    "ANTHROPIC_API_KEY is not configured on the server. Configure it, or start the audit in Demo mode.");
        }
        AuditSession a = new AuditSession();
        a.businessId = b.id;
        a.status = Status.CREATED;
        a.mode = mode;
        a.createdAt = Instant.now();
        a.progressMessage = "Audit created.";
        return audits.save(a);
    }

    public AuditSession startPersonaGeneration(UUID auditId) {
        AuditSession a = audit(auditId);
        if (!Set.of(Status.CREATED, Status.PERSONAS_READY, Status.FAILED).contains(a.status)) {
            throw ApiException.conflict("Personas can only be (re)generated before testing starts. Current status: " + a.status);
        }
        a.status = Status.GENERATING_PERSONAS;
        a.errorMessage = null;
        a.progressMessage = "Generating personas…";
        audits.save(a);
        workflow.generatePersonas(a.id);
        return a;
    }

    public AuditSession startTesting(UUID auditId) {
        AuditSession a = audit(auditId);
        if (!Set.of(Status.PERSONAS_READY, Status.REPORT_READY, Status.FAILED).contains(a.status)) {
            throw ApiException.conflict("Testing can start once personas are ready. Current status: " + a.status);
        }
        List<JourneyTask> ts = tasks.findByAuditSessionId(a.id);
        if (ts.isEmpty()) throw ApiException.conflict("Generate personas first.");
        // reset journeys for a (re)run
        for (JourneyTask t : ts) {
            evidence.findByJourneyTaskIdOrderByStepIndex(t.id).forEach(evidence::delete);
            t.status = Status.PENDING;
            t.actionCount = 0;
            t.outcomeSummary = null;
            t.startedAt = null;
            t.completedAt = null;
            tasks.save(t);
        }
        findings.findByAuditSessionId(a.id).forEach(findings::delete);
        a.status = Status.TESTING;
        a.errorMessage = null;
        a.overallScore = null;
        a.completedAt = null;
        a.progressMessage = "Starting browser…";
        audits.save(a);
        workflow.runTesting(a.id);
        return a;
    }

    public WebsitePreview startPreview(UUID auditId) {
        AuditSession a = audit(auditId);
        if (!Status.REPORT_READY.equals(a.status)) throw ApiException.conflict("The audit report must be ready before generating a preview.");
        Optional<WebsitePreview> existing = previews.findFirstByAuditSessionIdOrderByCreatedAtDesc(a.id);
        if (existing.isPresent() && Status.GENERATING.equals(existing.get().status)) return existing.get();
        WebsitePreview p = new WebsitePreview();
        p.auditSessionId = a.id;
        p.status = Status.GENERATING;
        p.mode = a.mode;
        p.createdAt = Instant.now();
        previews.save(p);
        workflow.generatePreview(a.id, p.id);
        return p;
    }

    public Map<String, Object> decide(UUID previewId, String status, String notes) {
        WebsitePreview p = previews.findById(previewId).orElseThrow(() -> ApiException.notFound("Preview"));
        if (Status.GENERATING.equals(p.status) || Status.FAILED.equals(p.status)) throw ApiException.conflict("Preview is not ready for review.");
        p.status = status;
        previews.save(p);
        OwnerApproval oa = new OwnerApproval();
        oa.websitePreviewId = p.id;
        oa.status = status;
        oa.notes = notes;
        oa.reviewedAt = Instant.now();
        approvals.save(oa);
        return previewView(p);
    }

    public Map<String, Object> decideChange(UUID previewId, UUID changeId, String status) {
        WebsitePreview p = previews.findById(previewId).orElseThrow(() -> ApiException.notFound("Preview"));
        ProposedChange c = changes.findById(changeId).filter(x -> x.websitePreviewId.equals(p.id))
                .orElseThrow(() -> ApiException.notFound("Change"));
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!Set.of(Status.APPROVED, Status.REJECTED, "PENDING").contains(s)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status must be APPROVED, REJECTED or PENDING");
        c.status = s;
        changes.save(c);
        return previewView(p);
    }

    // ------------------------------------------------------------ queries

    public AuditSession audit(UUID id) {
        return audits.findById(id).orElseThrow(() -> ApiException.notFound("Audit"));
    }

    public Business business(UUID id) {
        return businesses.findById(id).orElseThrow(() -> ApiException.notFound("Business"));
    }

    public WebsitePreview preview(UUID id) {
        return previews.findById(id).orElseThrow(() -> ApiException.notFound("Preview"));
    }

    public List<Map<String, Object>> auditsForBusiness(UUID businessId) {
        business(businessId);
        return audits.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(this::auditSummary).toList();
    }

    public List<Map<String, Object>> recentAudits() {
        return audits.findTop20ByOrderByCreatedAtDesc().stream().map(a -> {
            Map<String, Object> m = auditSummary(a);
            businesses.findById(a.businessId).ifPresent(b -> m.put("business", b));
            return m;
        }).toList();
    }

    private Map<String, Object> auditSummary(AuditSession a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("status", a.status);
        m.put("mode", a.mode);
        m.put("overallScore", a.overallScore);
        m.put("createdAt", a.createdAt);
        m.put("completedAt", a.completedAt);
        return m;
    }

    public Map<String, Object> auditView(UUID id) {
        AuditSession a = audit(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("audit", a);
        m.put("business", business(a.businessId));
        m.put("personas", personaViews(a));
        previews.findFirstByAuditSessionIdOrderByCreatedAtDesc(a.id).ifPresent(p -> m.put("preview", Map.of("id", p.id, "status", p.status)));
        m.put("maxActions", props.maxActionsPerJourney());
        return m;
    }

    private List<Map<String, Object>> personaViews(AuditSession a) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Persona p : personas.findByAuditSessionIdOrderByOrderIndex(a.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id);
            m.put("orderIndex", p.orderIndex);
            m.put("name", p.name);
            m.put("background", p.background);
            m.put("objective", p.objective);
            m.put("lookFor", readList(p.lookFor));
            tasks.findByPersonaId(p.id).ifPresent(t -> m.put("task", t));
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> progress(UUID id) {
        AuditSession a = audit(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", a.status);
        m.put("mode", a.mode);
        m.put("progressMessage", a.progressMessage);
        m.put("errorMessage", a.errorMessage);
        m.put("maxActions", props.maxActionsPerJourney());
        List<Map<String, Object>> journeys = new ArrayList<>();
        int done = 0, total = 0;
        for (Persona p : personas.findByAuditSessionIdOrderByOrderIndex(a.id)) {
            JourneyTask t = tasks.findByPersonaId(p.id).orElse(null);
            if (t == null) continue;
            total++;
            if (!Status.PENDING.equals(t.status) && !Status.RUNNING.equals(t.status)) done++;
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("personaId", p.id);
            j.put("personaName", p.name);
            j.put("taskId", t.id);
            j.put("task", t.description);
            j.put("status", t.status);
            j.put("actionCount", t.actionCount);
            j.put("outcomeSummary", t.outcomeSummary);
            List<BrowserEvidence> ev = evidence.findByJourneyTaskIdOrderByStepIndex(t.id);
            j.put("steps", ev.stream().map(this::evidenceView).toList());
            journeys.add(j);
        }
        m.put("journeys", journeys);
        m.put("completedJourneys", done);
        m.put("totalJourneys", total);
        return m;
    }

    private Map<String, Object> evidenceView(BrowserEvidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("step", e.stepIndex);
        m.put("url", e.pageUrl);
        m.put("title", e.pageTitle);
        m.put("action", e.action);
        m.put("actionInput", e.actionInput);
        m.put("reasoning", e.reasoning);
        m.put("observation", e.observation);
        m.put("screenshot", e.screenshotPath);
        m.put("success", e.success);
        m.put("createdAt", e.createdAt);
        return m;
    }

    public Map<String, Object> report(UUID id) {
        AuditSession a = audit(id);
        if (!Status.REPORT_READY.equals(a.status)) throw ApiException.conflict("Report is not ready yet. Current status: " + a.status);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("audit", a);
        m.put("business", business(a.businessId));
        Map<String, Object> progress = progress(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> journeys = (List<Map<String, Object>>) progress.get("journeys");
        m.put("journeys", journeys);
        long success = journeys.stream().filter(j -> Status.SUCCESS.equals(j.get("status"))).count();
        m.put("stats", Map.of(
                "journeysTested", journeys.size(),
                "successful", success,
                "partialOrFailed", journeys.size() - success));
        List<AuditFinding> fs = new ArrayList<>(findings.findByAuditSessionId(a.id));
        fs.sort(Comparator.comparingInt(f -> switch (f.severity) { case "high" -> 0; case "medium" -> 1; default -> 2; }));
        Map<UUID, BrowserEvidence> evById = new HashMap<>();
        for (Map<String, Object> j : journeys) {
            for (BrowserEvidence e : evidence.findByJourneyTaskIdOrderByStepIndex((UUID) j.get("taskId"))) evById.put(e.id, e);
        }
        List<Map<String, Object>> fv = new ArrayList<>();
        for (AuditFinding f : fs) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("finding", f);
            List<Map<String, Object>> cited = new ArrayList<>();
            for (String s : f.evidenceIds.split(",")) {
                try {
                    BrowserEvidence e = evById.get(UUID.fromString(s.trim()));
                    if (e != null) cited.add(evidenceView(e));
                } catch (IllegalArgumentException ignored) { }
            }
            x.put("evidence", cited);
            fv.add(x);
        }
        m.put("findings", fv);
        m.put("highPriority", fs.stream().filter(f -> "high".equals(f.severity)).count());
        return m;
    }

    public Map<String, Object> latestPreview(UUID auditId) {
        audit(auditId);
        WebsitePreview p = previews.findFirstByAuditSessionIdOrderByCreatedAtDesc(auditId)
                .orElseThrow(() -> ApiException.notFound("Preview"));
        return previewView(p);
    }

    public Map<String, Object> previewView(WebsitePreview p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("auditSessionId", p.auditSessionId);
        m.put("status", p.status);
        m.put("mode", p.mode);
        m.put("verificationNotes", p.verificationNotes);
        m.put("errorMessage", p.errorMessage);
        m.put("createdAt", p.createdAt);
        m.put("changes", changes.findByWebsitePreviewIdOrderByOrderIndex(p.id));
        m.put("approvals", approvals.findByWebsitePreviewIdOrderByReviewedAtDesc(p.id));
        AuditSession a = audits.findById(p.auditSessionId).orElse(null);
        if (a != null) m.put("baselineScreenshot", a.baselineScreenshot);
        return m;
    }

    public String renderPreview(UUID previewId) {
        WebsitePreview p = preview(previewId);
        if (p.generatedHtml == null) throw ApiException.conflict("Preview is not generated yet.");
        Business b = audits.findById(p.auditSessionId).map(a -> business(a.businessId)).orElse(null);
        String title = b == null ? "Preview" : org.springframework.web.util.HtmlUtils.htmlEscape(b.name) + " — TrustLens preview";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title><style>" + Objects.toString(p.generatedCss, "") + "</style></head><body>"
                + p.generatedHtml + "</body></html>";
    }

    private List<String> readList(String json) {
        try {
            return Arrays.asList(mapper.readValue(json, String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
