package com.fintwin.fintwin.agent.domain;

import java.util.Objects;

public record MissingInformation(
        MissingInformationCode code,
        String field,
        String question,
        AgentIntent requiredForIntent
) {
    public MissingInformation {
        Objects.requireNonNull(code);
        Objects.requireNonNull(field);
        Objects.requireNonNull(question);
        Objects.requireNonNull(requiredForIntent);
    }
}
