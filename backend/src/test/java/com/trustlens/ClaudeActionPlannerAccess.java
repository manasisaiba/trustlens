package com.trustlens;

import com.fasterxml.jackson.databind.JsonNode;
import com.trustlens.ai.JourneyContext;
import com.trustlens.ai.PlannedAction;

class ClaudeActionPlannerAccess {
    static PlannedAction validate(JsonNode out, JsonNode state, JourneyContext ctx) {
        return com.trustlens.ai.ClaudeActionPlannerTestBridge.validate(out, state, ctx);
    }
}
