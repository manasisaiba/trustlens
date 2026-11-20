package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;

public class ClaudeActionPlannerTestBridge {
    public static PlannedAction validate(JsonNode out, JsonNode state, JourneyContext ctx) {
        return ClaudeActionPlanner.validate(out, state, ctx);
    }
}
