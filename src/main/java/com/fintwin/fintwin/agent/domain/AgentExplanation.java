package com.fintwin.fintwin.agent.domain;

import java.util.List;
import java.util.Objects;

public record AgentExplanation(
        String headline,
        String summary,
        List<AgentEvidence> evidence,
        String assumptionNotice,
        String disclaimer
) {
    public AgentExplanation {
        Objects.requireNonNull(headline);
        Objects.requireNonNull(summary);
        evidence = List.copyOf(evidence);
        Objects.requireNonNull(assumptionNotice);
        Objects.requireNonNull(disclaimer);
    }
}
