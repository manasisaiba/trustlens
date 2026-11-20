package com.trustlens.web;

import com.trustlens.browser.WorkerClient;
import com.trustlens.config.AppProperties;
import com.trustlens.domain.AuditSession;
import com.trustlens.domain.Business;
import com.trustlens.domain.Status;
import com.trustlens.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuditService service;
    private final AppProperties props;
    private final WorkerClient worker;

    public ApiController(AuditService service, AppProperties props, WorkerClient worker) {
        this.service = service;
        this.props = props;
        this.worker = worker;
    }

    public record CreateBusinessRequest(@NotBlank @Size(max = 200) String name,
                                        @NotBlank @Size(max = 2000) String websiteUrl,
                                        @NotBlank @Size(max = 200) String category,
                                        @Size(max = 2000) String description) {}

    public record CreateAuditRequest(@NotNull UUID businessId, String mode) {}

    public record DecisionRequest(@Size(max = 2000) String notes) {}

    public record ChangeDecisionRequest(@NotBlank String status) {}

    @GetMapping("/system/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claudeConfigured", props.claudeConfigured());
        m.put("defaultMode", props.claudeConfigured() ? Status.LIVE : Status.DEMO);
        m.put("model", props.claudeConfigured() ? props.anthropicModel() : null);
        m.put("workerHealthy", worker.healthy());
        m.put("maxActionsPerJourney", props.maxActionsPerJourney());
        return m;
    }

    @PostMapping("/businesses")
    public ResponseEntity<Business> createBusiness(@Valid @RequestBody CreateBusinessRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBusiness(r.name(), r.websiteUrl(), r.category(), r.description()));
    }

    @GetMapping("/businesses/{id}")
    public Business business(@PathVariable UUID id) {
        return service.business(id);
    }

    @GetMapping("/businesses/{id}/audits")
    public List<Map<String, Object>> businessAudits(@PathVariable UUID id) {
        return service.auditsForBusiness(id);
    }

    @GetMapping("/audits")
    public List<Map<String, Object>> recentAudits() {
        return service.recentAudits();
    }

    @PostMapping("/audits")
    public ResponseEntity<AuditSession> createAudit(@Valid @RequestBody CreateAuditRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAudit(r.businessId(), r.mode()));
    }

    @GetMapping("/audits/{id}")
    public Map<String, Object> audit(@PathVariable UUID id) {
        return service.auditView(id);
    }

    @PostMapping("/audits/{id}/generate-personas")
    public ResponseEntity<AuditSession> generatePersonas(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.startPersonaGeneration(id));
    }

    @PostMapping("/audits/{id}/start-testing")
    public ResponseEntity<AuditSession> startTesting(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.startTesting(id));
    }

    @GetMapping("/audits/{id}/progress")
    public Map<String, Object> progress(@PathVariable UUID id) {
        return service.progress(id);
    }

    @GetMapping("/audits/{id}/report")
    public Map<String, Object> report(@PathVariable UUID id) {
        return service.report(id);
    }

    @PostMapping("/audits/{id}/generate-preview")
    public ResponseEntity<Map<String, Object>> generatePreview(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.previewView(service.startPreview(id)));
    }

    @GetMapping("/audits/{id}/preview")
    public Map<String, Object> preview(@PathVariable UUID id) {
        return service.latestPreview(id);
    }

    @PostMapping("/previews/{id}/approve")
    public Map<String, Object> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest r) {
        return service.decide(id, Status.APPROVED, r == null ? null : r.notes());
    }

    @PostMapping("/previews/{id}/reject")
    public Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest r) {
        return service.decide(id, Status.REJECTED, r == null ? null : r.notes());
    }

    @PatchMapping("/previews/{id}/changes/{changeId}")
    public Map<String, Object> decideChange(@PathVariable UUID id, @PathVariable UUID changeId, @Valid @RequestBody ChangeDecisionRequest r) {
        return service.decideChange(id, changeId, r.status());
    }

    /** Renders the generated page for a sandboxed iframe. CSP forbids scripts, plugins, framing by others and form submission. */
    @GetMapping(value = "/previews/{id}/render", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> render(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src https: data:; font-src https: data:; form-action 'none'; frame-ancestors 'self'; sandbox allow-same-origin")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(service.renderPreview(id));
    }

    @GetMapping(value = "/previews/{id}/export")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trustlens-preview-" + id.toString().substring(0, 8) + ".html\"")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(service.renderPreview(id));
    }

    /** Serves screenshots captured by the browser worker (shared volume). Path traversal is rejected. */
    @GetMapping("/screenshots/{folder}/{file}")
    public ResponseEntity<Resource> screenshot(@PathVariable String folder, @PathVariable String file) {
        if (!folder.matches("[a-zA-Z0-9_-]+") || !file.matches("[a-zA-Z0-9_.-]+\\.jpg")) return ResponseEntity.badRequest().build();
        Path base = Path.of(props.screenshotDir()).toAbsolutePath().normalize();
        Path p = base.resolve(folder).resolve(file).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(new FileSystemResource(p));
    }
}
