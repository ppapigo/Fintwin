package com.fintwin.fintwin.privacy.domain;

import java.util.List;

public record ExternalAiScenarioDraft(
        String intent,
        List<EventDraft> events,
        List<String> missingFields
) {
    public ExternalAiScenarioDraft {
        events = events == null ? null : List.copyOf(events);
        missingFields = missingFields == null ? null : List.copyOf(missingFields);
    }

    public record EventDraft(
            String eventId,
            String eventType,
            String changeDirection,
            String effectiveDateExpression,
            String effectiveDateReference,
            String startDateExpression,
            String startDateReference,
            String endDateExpression,
            String endDateReference,
            String durationReference,
            String amountReference,
            String monthlyDeltaReference,
            String description
    ) {
    }
}
