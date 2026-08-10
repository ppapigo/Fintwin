package com.fintwin.fintwin.agent.domain;

import java.util.Objects;

public record AgentTraceStep(int sequence, AgentState state, String component, String outcomeCode) {
    public AgentTraceStep {
        if (sequence < 1) {
            throw new IllegalArgumentException("Trace sequence must be positive");
        }
        Objects.requireNonNull(state);
        Objects.requireNonNull(component);
        Objects.requireNonNull(outcomeCode);
    }
}
