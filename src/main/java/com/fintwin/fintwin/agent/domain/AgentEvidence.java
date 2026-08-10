package com.fintwin.fintwin.agent.domain;

import java.util.Objects;

public record AgentEvidence(String fieldPath, String value) {
    public AgentEvidence {
        Objects.requireNonNull(fieldPath);
        Objects.requireNonNull(value);
    }
}
