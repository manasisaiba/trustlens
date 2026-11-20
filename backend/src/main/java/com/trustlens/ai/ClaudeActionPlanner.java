package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Live mode: Claude decides the next permitted browser action from the observed page state. */
@Component
public class ClaudeActionPlanner {
    private static final Set<String> ALLOWED = Set.of("click_element", "navigate_to_page", "fill_form", "scroll", "extract_visible_text", "finish");
    private final ClaudeClient claude;
    private final PromptLibrary prompts;

    public ClaudeActionPlanner(ClaudeClient claude, PromptLibrary prompts) {
        this.claude = claude;
        this.prompts = prompts;
    }

    public PlannedAction next(JourneyContext ctx, JsonNode state) {
        ArrayNode content = claude.content();
        StringBuilder sb = new StringBuilder();
        sb.append("BUSINESS: ").append(ctx.business.name).append(" (").append(ctx.business.category).append(")\n");
        sb.append("PERSONA: ").append(ctx.persona.name).append(" — ").append(ctx.persona.background).append("\n");
        sb.append("TASK: ").append(ctx.task.description).append("\n");
        sb.append("SUCCESS CRITERIA: ").append(ctx.task.successCriteria).append("\n");
        sb.append("LOOK FOR: ").append(String.join(", ", ctx.lookFor)).append("\n");
        sb.append("STEP: ").append(ctx.step).append(" of max ").append(ctx.maxActions)
                .append(ctx.step >= ctx.maxActions ? " (LAST STEP: you must finish now)" : "").append("\n\n");
        sb.append("HISTORY:\n");
        if (ctx.history.isEmpty()) sb.append("(none yet)\n");
        ctx.history.forEach(h -> sb.append("- ").append(h).append("\n"));
        sb.append("\nCURRENT PAGE\nURL: ").append(state.path("url").asText()).append("\nTITLE: ").append(state.path("title").asText()).append("\n");
        sb.append("INTERACTIVE ELEMENTS (id | tag | text | href/type):\n");
        int n = 0;
        for (JsonNode e : state.path("elements")) {
            if (n++ >= 100) break;
            sb.append(e.path("id").asText()).append(" | ").append(e.path("tag").asText()).append(" | ")
                    .append(e.path("text").asText()).append(" | ")
                    .append(e.has("href") ? e.path("href").asText() : e.path("type").asText(""))
                    .append(e.path("belowFold").asBoolean() ? " | below fold" : "").append("\n");
        }
        String text = state.path("text").asText();
        sb.append("\nVISIBLE TEXT (excerpt):\n").append(text, 0, Math.min(text.length(), 5000));
        claude.addText(content, sb.toString());
        String img = state.path("imageBase64").asText("");
        if (!img.isEmpty()) claude.addImage(content, "image/jpeg", img);

        JsonNode out = claude.callTool(prompts.system("browser_action"), content, "decide_browser_action",
                "Choose the single next browser action for the persona.", prompts.schema("browser_action"), 1200);
        return validate(out, state, ctx);
    }

    /** Validates Claude's output before anything is executed. Invalid choices become a safe fallback. */
    static PlannedAction validate(JsonNode out, JsonNode state, JourneyContext ctx) {
        String action = out.path("action").asText("");
        String observation = out.path("observation").asText("");
        String reasoning = out.path("reasoning").asText("");
        if (!ALLOWED.contains(action)) {
            return new PlannedAction(observation, "Model returned unsupported action '" + action + "'; scrolling instead.", "scroll", null, null, List.of(), null, null);
        }
        if (ctx.step >= ctx.maxActions && !"finish".equals(action)) {
            return new PlannedAction(observation, reasoning + " (action limit reached)", "finish", null, null, List.of(), "failed",
                    "Action limit reached before the task was completed.");
        }
        String elementId = out.path("elementId").asText(null);
        if ("click_element".equals(action)) {
            boolean exists = false;
            for (JsonNode e : state.path("elements")) if (e.path("id").asText().equals(elementId)) exists = true;
            if (!exists) return new PlannedAction(observation, "Model chose unknown element '" + elementId + "'; re-reading page.", "extract_visible_text", null, null, List.of(), null, null);
        }
        List<PlannedAction.Field> fields = new ArrayList<>();
        for (JsonNode f : out.path("fields")) fields.add(new PlannedAction.Field(f.path("elementId").asText(), f.path("value").asText()));
        String outcome = out.path("outcome").asText(null);
        if ("finish".equals(action) && (outcome == null || !Set.of("success", "partial", "failed").contains(outcome))) outcome = "partial";
        return new PlannedAction(observation, reasoning, action, elementId, out.path("url").asText(null), fields, outcome, out.path("summary").asText(null));
    }
}
