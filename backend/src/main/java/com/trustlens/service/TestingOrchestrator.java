package com.trustlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trustlens.ai.*;
import com.trustlens.browser.WorkerClient;
import com.trustlens.config.AppProperties;
import com.trustlens.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the controlled browser-agent loop for every persona of an audit:
 * observe page -> planner (Claude or demo heuristic) picks ONE permitted action -> worker executes it in Chromium
 * -> evidence (URL, observation, screenshot) persisted -> repeat until finish / action limit / timeout.
 */
@Service
public class TestingOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(TestingOrchestrator.class);

    private final AppProperties props;
    private final WorkerClient worker;
    private final ClaudeActionPlanner claudePlanner;
    private final HeuristicActionPlanner demoPlanner;
    private final ClaudeClient claude;
    private final PromptLibrary prompts;
    private final ObjectMapper mapper;
    private final AuditSessionRepository audits;
    private final BusinessRepository businesses;
    private final PersonaRepository personas;
    private final JourneyTaskRepository tasks;
    private final BrowserEvidenceRepository evidence;
    private final ThreadPoolTaskExecutor journeyExecutor;

    public TestingOrchestrator(AppProperties props, WorkerClient worker, ClaudeActionPlanner claudePlanner,
                               HeuristicActionPlanner demoPlanner, ClaudeClient claude, PromptLibrary prompts, ObjectMapper mapper,
                               AuditSessionRepository audits, BusinessRepository businesses, PersonaRepository personas,
                               JourneyTaskRepository tasks, BrowserEvidenceRepository evidence,
                               @Qualifier("journeyExecutor") ThreadPoolTaskExecutor journeyExecutor) {
        this.props = props;
        this.worker = worker;
        this.claudePlanner = claudePlanner;
        this.demoPlanner = demoPlanner;
        this.claude = claude;
        this.prompts = prompts;
        this.mapper = mapper;
        this.audits = audits;
        this.businesses = businesses;
        this.personas = personas;
        this.tasks = tasks;
        this.evidence = evidence;
        this.journeyExecutor = journeyExecutor;
    }

    /** Captures a full-page screenshot of the original homepage. Throws if the site cannot be loaded. */
    public void captureBaseline(AuditSession audit, Business business) {
        JsonNode created = worker.createSession(business.websiteUrl, audit.id.toString(), "baseline");
        String sid = created.path("sessionId").asText();
        try {
            ObjectNode a = worker.newAction("take_screenshot");
            a.put("fullPage", true);
            JsonNode r = worker.action(sid, a);
            audit.baselineScreenshot = r.path("state").path("screenshot").asText(null);
            audits.save(audit);
        } finally {
            worker.close(sid);
        }
    }

    public void runAllJourneys(AuditSession audit, Business business) {
        List<Persona> ps = personas.findByAuditSessionIdOrderByOrderIndex(audit.id);
        List<Future<?>> futures = new ArrayList<>();
        for (Persona p : ps) {
            JourneyTask t = tasks.findByPersonaId(p.id).orElseThrow();
            futures.add(journeyExecutor.submit(() -> runJourneySafely(business, audit, p, t)));
        }
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(props.journeyTimeoutSeconds() + 60L).toMillis();
        for (Future<?> f : futures) {
            try {
                f.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                f.cancel(true);
                log.warn("Journey did not finish in time: {}", e.toString());
            }
        }
        // Anything still not terminal is marked as an error (e.g. hard timeout)
        for (JourneyTask t : tasks.findByAuditSessionId(audit.id)) {
            if (Status.PENDING.equals(t.status) || Status.RUNNING.equals(t.status)) {
                t.status = Status.ERROR;
                t.outcomeSummary = "Journey exceeded the overall time limit and was stopped.";
                t.completedAt = Instant.now();
                tasks.save(t);
            }
        }
    }

    private void runJourneySafely(Business business, AuditSession audit, Persona persona, JourneyTask task) {
        try {
            runJourney(business, audit, persona, task);
        } catch (Exception e) {
            log.error("Journey {} crashed", task.id, e);
            task.status = Status.ERROR;
            task.outcomeSummary = "Browser test could not be completed: " + e.getMessage();
            task.completedAt = Instant.now();
            tasks.save(task);
        }
    }

    void runJourney(Business business, AuditSession audit, Persona persona, JourneyTask task) {
        boolean live = Status.LIVE.equals(audit.mode);
        List<String> lookFor = readList(persona.lookFor);
        JourneyContext ctx = new JourneyContext(business, persona, task, lookFor, props.maxActionsPerJourney());
        long deadline = System.currentTimeMillis() + props.journeyTimeoutSeconds() * 1000L;

        task.status = Status.RUNNING;
        task.startedAt = Instant.now();
        task.actionCount = 0;
        tasks.save(task);

        JsonNode created;
        try {
            created = worker.createSession(business.websiteUrl, audit.id.toString(), "p" + persona.orderIndex);
        } catch (WorkerClient.WorkerException e) {
            saveEvidence(task, 0, business.websiteUrl, null, "navigate_to_page", business.websiteUrl, null,
                    "Website failed to load: " + e.getMessage(), null, null, false);
            finishTask(task, Status.JOURNEY_FAILED, "The website could not be loaded in the browser: " + e.getMessage());
            return;
        }
        String sid = created.path("sessionId").asText();
        JsonNode state = created.path("state");
        String finalOutcome = null;
        String finalSummary = null;
        try {
            for (int step = 1; step <= props.maxActionsPerJourney() + 1; step++) {
                ctx.step = Math.min(step, props.maxActionsPerJourney());
                if (System.currentTimeMillis() > deadline) {
                    finalOutcome = "failed";
                    finalSummary = "Journey timed out after " + props.journeyTimeoutSeconds() + "s.";
                    break;
                }
                PlannedAction plan = live ? claudePlanner.next(ctx, state) : demoPlanner.next(ctx, state);
                if (plan.isFinish()) {
                    saveEvidence(task, step, state.path("url").asText(), state.path("title").asText(), "finish",
                            plan.outcome(), plan.reasoning(), plan.observation(), state.path("text").asText(), state.path("screenshot").asText(null), true);
                    finalOutcome = plan.outcome();
                    finalSummary = plan.summary();
                    break;
                }
                ObjectNode action = worker.newAction(plan.action());
                String input = null;
                switch (plan.action()) {
                    case "click_element" -> {
                        action.put("elementId", plan.elementId());
                        input = plan.elementId() + " \"" + elementText(state, plan.elementId()) + "\"";
                    }
                    case "navigate_to_page" -> {
                        action.put("url", plan.url());
                        input = plan.url();
                    }
                    case "fill_form" -> {
                        ArrayNode arr = action.putArray("fields");
                        List<String> parts = new ArrayList<>();
                        for (PlannedAction.Field f : plan.fields()) {
                            arr.addObject().put("elementId", f.elementId()).put("value", f.value());
                            parts.add(f.elementId() + "=" + f.value());
                        }
                        input = String.join(", ", parts);
                    }
                    default -> { }
                }
                action.put("includeImage", live);
                JsonNode result = worker.action(sid, action);
                String note = result.path("note").asText("");
                boolean ok = result.path("ok").asBoolean(true);
                // Evidence row: what was observed on the page BEFORE the action (with that page's screenshot) + the action result
                saveEvidence(task, step, state.path("url").asText(), state.path("title").asText(), plan.action(), input,
                        plan.reasoning(), plan.observation() + (note.isEmpty() ? "" : " → Result: " + note),
                        state.path("text").asText(), state.path("screenshot").asText(null), ok);
                ctx.history.add(plan.action() + (input == null ? "" : " " + input) + " on " + state.path("url").asText()
                        + " → " + (ok ? "ok" : "failed") + (note.isEmpty() ? "" : " (" + note + ")")
                        + (result.path("navigated").asBoolean() ? " → now at " + result.path("state").path("url").asText() : ""));
                task.actionCount = step;
                tasks.save(task);
                state = result.path("state");
            }
        } finally {
            worker.close(sid);
        }
        if (finalOutcome == null) {
            finalOutcome = "failed";
            finalSummary = "Reached the maximum of " + props.maxActionsPerJourney() + " actions without completing the task.";
        }
        // Live mode: independent analysis of the full evidence log decides the final outcome (prevents over-claiming)
        if (live) {
            try {
                JsonNode analysis = analyseJourney(ctx, task);
                finalOutcome = analysis.path("outcome").asText(finalOutcome);
                String s = analysis.path("summary").asText("");
                if (!s.isBlank()) finalSummary = s;
            } catch (Exception e) {
                log.warn("Journey analysis failed, using planner outcome: {}", e.getMessage());
            }
        }
        String status = switch (finalOutcome) {
            case "success" -> Status.SUCCESS;
            case "partial" -> Status.PARTIAL;
            default -> Status.JOURNEY_FAILED;
        };
        finishTask(task, status, finalSummary);
    }

    private JsonNode analyseJourney(JourneyContext ctx, JourneyTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("PERSONA: ").append(ctx.persona.name).append("\nTASK: ").append(task.description)
                .append("\nSUCCESS CRITERIA: ").append(task.successCriteria).append("\n\nEVIDENCE LOG:\n");
        for (BrowserEvidence e : evidence.findByJourneyTaskIdOrderByStepIndex(task.id)) {
            sb.append("Step ").append(e.stepIndex).append(" | ").append(e.pageUrl).append(" | action=").append(e.action)
                    .append(e.actionInput == null ? "" : " (" + e.actionInput + ")").append("\n  observed: ").append(e.observation).append("\n");
        }
        ArrayNode content = claude.content();
        claude.addText(content, sb.toString());
        return claude.callTool(prompts.system("journey_analysis"), content, "classify_journey",
                "Classify the journey outcome from the evidence.", prompts.schema("journey_analysis"), 1200);
    }

    private void finishTask(JourneyTask task, String status, String summary) {
        task.status = status;
        task.outcomeSummary = summary;
        task.completedAt = Instant.now();
        tasks.save(task);
    }

    private void saveEvidence(JourneyTask task, int step, String url, String title, String action, String input, String reasoning,
                              String observation, String pageText, String screenshot, boolean success) {
        BrowserEvidence e = new BrowserEvidence();
        e.journeyTaskId = task.id;
        e.stepIndex = step;
        e.pageUrl = trunc(url, 1900);
        e.pageTitle = trunc(title, 480);
        e.action = action;
        e.actionInput = trunc(input, 2000);
        e.reasoning = trunc(reasoning, 2000);
        e.observation = trunc(observation, 4000);
        e.pageTextExcerpt = trunc(pageText, 4000);
        e.screenshotPath = screenshot;
        e.success = success;
        e.createdAt = Instant.now();
        evidence.save(e);
    }

    private static String elementText(JsonNode state, String id) {
        for (JsonNode e : state.path("elements")) if (e.path("id").asText().equals(id)) return e.path("text").asText();
        return "";
    }

    private List<String> readList(String json) {
        try {
            return Arrays.asList(mapper.readValue(json, String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    static String trunc(String s, int n) {
        return s == null ? null : (s.length() <= n ? s : s.substring(0, n));
    }
}
