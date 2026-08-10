package com.fintwin.fintwin.agent.domain;

import java.time.YearMonth;
import java.util.Objects;

public record RiskFinding(
        RiskCode code,
        RiskSeverity severity,
        String evidenceField,
        YearMonth affectedYearMonth,
        String summary
) {
    public RiskFinding {
        Objects.requireNonNull(code);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(evidenceField);
        Objects.requireNonNull(summary);
    }
}
