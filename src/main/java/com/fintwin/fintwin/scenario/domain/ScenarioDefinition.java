package com.fintwin.fintwin.scenario.domain;

import java.util.List;

public record ScenarioDefinition(String name, List<FinancialEvent> events, List<String> warnings) {
    public ScenarioDefinition {
        events = List.copyOf(events);
        warnings = List.copyOf(warnings);
    }
}
