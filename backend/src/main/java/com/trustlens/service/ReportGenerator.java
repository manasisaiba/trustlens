package com.trustlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.trustlens.ai.ClaudeClient;
import com.trustlens.ai.PromptLibrary;
import com.trustlens.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Turns recorded browser evidence into audit findings. Every finding must reference real evidence rows;
 * AI findings that cite unknown evidence are discarded.
 */
@Service
public class ReportGenerator {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);
    private final ClaudeClient claude;
    private final PromptLibrary prompts;
    private final PersonaRepository personas;
    private final JourneyTaskRepository tasks;
    private final BrowserEvidenceRepository evidence;
    private final AuditFindingRepository findings;
    private final AuditSessionRepository audits;

    public ReportGenerator(ClaudeClient claude, PromptLibrary prompts, PersonaRepository personas, JourneyTaskRepository tasks,
                           BrowserEvidenceRepository evidence, AuditFindingRepository findings, AuditSessionRepository audits) {
        this.claude = claude;
        this.prompts = prompts;
        this.personas = personas;
        this.tasks = tasks;
        this.evidence = evidence;
        this.findings = findings;
        this.audits = audits;
    }

    record Journey(Persona persona, JourneyTask task, List<BrowserEvidence> evidence) {}

    public void generate(AuditSession audit, Business business) {
        List<Journey> journeys = new ArrayList<>();
        for (Persona p : personas.findByAuditSessionIdOrderByOrderIndex(audit.id)) {
            JourneyTask t = tasks.findByPersonaId(p.id).orElseThrow();
            journeys.add(new Journey(p, t, evidence.findByJourneyTaskIdOrderByStepIndex(t.id)));
        }
        findings.findByAuditSessionId(audit.id).forEach(findings::delete);

        List<AuditFinding> result;
        String summary;
        if (Status.LIVE.equals(audit.mode)) {
            try {
                var live = generateLive(audit, business, journeys);
                result = live.getKey();
                summary = live.getValue();
            } catch (Exception e) {
                log.warn("Live finding generation failed; using evidence-rule findings: {}", e.getMessage());
                result = generateRules(audit, journeys);
                summary = "Claude could not generate the narrative report (" + e.getMessage()
                        + "). Findings below were derived directly from journey outcomes and browser evidence.";
            }
        } else {
            result = generateRules(audit, journeys);
            summary = demoSummary(journeys);
        }
        findings.saveAll(result);
        score(audit, journeys, result);
        audit.reportSummary = summary;
        audits.save(audit);
    }

    private Map.Entry<List<AuditFinding>, String> generateLive(AuditSession audit, Business business, List<Journey> journeys) {
        Map<String, BrowserEvidence> refs = new LinkedHashMap<>();
        Map<UUID, Journey> byTask = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("BUSINESS: ").append(business.name).append(" (").append(business.category).append(") ").append(business.websiteUrl).append("\n\n");
        int n = 1;
        for (Journey j : journeys) {
            byTask.put(j.task.id, j);
            sb.append("=== PERSONA: ").append(j.persona.name).append("\nTASK: ").append(j.task.description)
                    .append("\nOUTCOME: ").append(j.task.status).append(" — ").append(Objects.toString(j.task.outcomeSummary, ""))
                    .append("\nACTIONS USED: ").append(j.task.actionCount).append("\n");
            for (BrowserEvidence e : j.evidence) {
                String ref = "E" + (n++);
                refs.put(ref, e);
                sb.append("[").append(ref).append("] step ").append(e.stepIndex).append(" | ").append(e.pageUrl)
                        .append(" | ").append(e.action).append(e.actionInput == null ? "" : " (" + e.actionInput + ")")
                        .append("\n   observed: ").append(e.observation).append("\n");
            }
            sb.append("\n");
        }
        ArrayNode content = claude.content();
        claude.addText(content, sb.toString());
        JsonNode out = claude.callTool(prompts.system("audit_findings"), content, "save_audit_findings",
                "Save the evidence-backed audit findings.", prompts.schema("audit_findings"), 4000);

        List<AuditFinding> list = new ArrayList<>();
        for (JsonNode f : out.path("findings")) {
            List<BrowserEvidence> cited = new ArrayList<>();
            for (JsonNode id : f.path("evidenceIds")) {
                BrowserEvidence e = refs.get(id.asText().trim().toUpperCase(Locale.ROOT));
                if (e != null) cited.add(e);
            }
            if (cited.isEmpty()) {
                log.info("Discarding finding without valid evidence: {}", f.path("title").asText());
                continue;
            }
            String sev = f.path("severity").asText("medium").toLowerCase(Locale.ROOT);
            if (!Set.of("high", "medium", "low").contains(sev)) sev = "medium";
            Journey j = byTask.get(cited.get(0).journeyTaskId);
            AuditFinding af = base(audit, cited);
            af.title = TestingOrchestrator.trunc(f.path("title").asText(), 290);
            af.description = f.path("description").asText();
            af.severity = sev;
            af.personaName = j != null ? j.persona.name : f.path("personaName").asText(null);
            String url = f.path("affectedUrl").asText("");
            boolean urlObserved = cited.stream().anyMatch(e -> url.equals(e.pageUrl));
            af.affectedUrl = urlObserved ? url : cited.get(cited.size() - 1).pageUrl;
            af.evidenceSummary = f.path("evidenceSummary").asText();
            af.frictionReason = f.path("frictionReason").asText();
            af.recommendation = f.path("recommendation").asText();
            list.add(af);
        }
        if (list.isEmpty() && journeys.stream().anyMatch(j -> !Status.SUCCESS.equals(j.task.status))) {
            list = generateRules(audit, journeys);
        }
        return Map.entry(list, out.path("summary").asText(""));
    }

    private AuditFinding base(AuditSession audit, List<BrowserEvidence> cited) {
        AuditFinding af = new AuditFinding();
        af.auditSessionId = audit.id;
        af.source = "SIMULATED";
        af.createdAt = Instant.now();
        af.evidenceIds = String.join(",", cited.stream().map(e -> e.id.toString()).toList());
        af.screenshotPath = cited.stream().map(e -> e.screenshotPath).filter(Objects::nonNull).reduce((a, b) -> b).orElse(null);
        return af;
    }

    /** Deterministic findings derived only from recorded journey outcomes and evidence (demo mode / fallback). */
    List<AuditFinding> generateRules(AuditSession audit, List<Journey> journeys) {
        List<AuditFinding> list = new ArrayList<>();
        for (Journey j : journeys) {
            if (j.evidence.isEmpty()) continue;
            String st = j.task.status;
            List<BrowserEvidence> cited = j.evidence.subList(Math.max(0, j.evidence.size() - 3), j.evidence.size());
            BrowserEvidence last = j.evidence.get(j.evidence.size() - 1);
            Set<String> visited = new LinkedHashSet<>();
            j.evidence.forEach(e -> { if (e.pageUrl != null) visited.add(e.pageUrl); });
            String visitedStr = String.join(", ", visited);
            AuditFinding af = base(audit, cited);
            af.personaName = j.persona.name;
            af.affectedUrl = last.pageUrl;
            af.evidenceSummary = "The persona visited " + visited.size() + " page(s): " + visitedStr + ". Last observation: " + last.observation;
            if (Status.SUCCESS.equals(st)) {
                if (j.task.actionCount < 4) continue;
                af.severity = "low";
                af.title = "\"" + j.persona.name + "\" needed " + j.task.actionCount + " actions to complete their task";
                af.description = "The task \"" + j.task.description + "\" was completed, but only after " + j.task.actionCount + " browser actions.";
                af.frictionReason = "Each extra click or page increases the chance a real visitor gives up before finding what they need.";
                af.recommendation = "Surface this information closer to the homepage: " + recommendationFor(j) + "";
            } else {
                boolean error = Status.ERROR.equals(st);
                af.severity = Status.JOURNEY_FAILED.equals(st) || error ? "high" : "medium";
                af.title = error ? "Browser journey for \"" + j.persona.name + "\" could not run"
                        : (Status.PARTIAL.equals(st) ? "Partially completed: " : "Not completed: ") + shortTask(j.task.description);
                af.description = "Simulated persona \"" + j.persona.name + "\" tried to: " + j.task.description + ". Outcome: "
                        + Objects.toString(j.task.outcomeSummary, st) + "";
                af.frictionReason = error ? "The page could not be loaded or interacted with reliably during the test."
                        : "A customer with this goal would have to search harder, guess, or contact the business to get basic information.";
                af.recommendation = error ? "Check that the page loads reliably and quickly for automated and real visitors (no blocking interstitials)."
                        : recommendationFor(j);
            }
            list.add(af);
        }
        return list;
    }

    private static String shortTask(String t) {
        return t.length() > 110 ? t.substring(0, 107) + "…" : t;
    }

    private static String recommendationFor(Journey j) {
        String k = (j.persona.lookFor + " " + j.task.description).toLowerCase(Locale.ROOT);
        if (k.contains("price") || k.contains("fee") || k.contains("cost")) return "Add a clear Pricing section or page linked from the main navigation, with starting prices or price ranges for key services.";
        if (k.contains("hour") || k.contains("weekend") || k.contains("evening")) return "Show opening hours (including evenings/weekends) and the address in the header or footer of every page.";
        if (k.contains("emergency") || k.contains("urgent") || k.contains("call") || k.contains("contact")) return "Add a prominent click-to-call phone number and a visible 'Urgent / same-day' notice near the top of the homepage.";
        if (k.contains("book") || k.contains("appointment") || k.contains("service")) return "List core services with short descriptions on the homepage and add a primary 'Book / Enquire' call-to-action button.";
        return "Make this information reachable from the main navigation within one click of the homepage.";
    }

    private static String demoSummary(List<Journey> journeys) {
        long ok = journeys.stream().filter(j -> Status.SUCCESS.equals(j.task.status)).count();
        return "DEMO MODE: " + journeys.size() + " simulated journeys were executed in a real browser using a keyword heuristic instead of Claude. "
                + ok + " of " + journeys.size() + " completed their task. Findings are derived directly from recorded journey outcomes.";
    }

    private void score(AuditSession audit, List<Journey> journeys, List<AuditFinding> fs) {
        if (journeys.isEmpty()) return;
        double sum = 0;
        for (Journey j : journeys) {
            sum += switch (j.task.status) {
                case Status.SUCCESS -> j.task.actionCount <= 3 ? 100 : 85;
                case Status.PARTIAL -> 55;
                default -> 15;
            };
        }
        double avg = sum / journeys.size();
        long high = fs.stream().filter(f -> "high".equals(f.severity)).count();
        long med = fs.stream().filter(f -> "medium".equals(f.severity)).count();
        int score = (int) Math.max(0, Math.min(100, Math.round(avg - high * 4 - med * 2)));
        audit.overallScore = score;
        audit.scoreExplanation = "Heuristic score based only on the " + journeys.size() + " simulated journeys tested: each journey scores 100 "
                + "(success in ≤3 actions), 85 (success with more steps), 55 (partial) or 15 (failed); the average is reduced by 4 per high "
                + "and 2 per medium finding. It is not a measured customer-satisfaction metric.";
    }
}
