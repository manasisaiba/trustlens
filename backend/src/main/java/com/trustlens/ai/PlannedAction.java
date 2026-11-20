package com.trustlens.ai;

import java.util.List;

public record PlannedAction(String observation, String reasoning, String action, String elementId, String url,
                            List<Field> fields, String outcome, String summary) {
    public record Field(String elementId, String value) {}

    public boolean isFinish() {
        return "finish".equals(action);
    }
}
