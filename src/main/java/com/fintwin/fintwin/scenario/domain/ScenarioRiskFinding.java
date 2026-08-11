package com.fintwin.fintwin.scenario.domain;

import java.time.YearMonth;

public record ScenarioRiskFinding(
        String code,
        String message,
        YearMonth affectedYearMonth
) {
}
