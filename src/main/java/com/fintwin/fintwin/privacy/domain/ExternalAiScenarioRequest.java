package com.fintwin.fintwin.privacy.domain;

import com.fintwin.fintwin.scenario.domain.FinancialEventType;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record ExternalAiScenarioRequest(
        String schemaVersion,
        String purpose,
        String locale,
        YearMonth currentYearMonth,
        String sanitizedScenarioText,
        List<FinancialEventType> supportedEventTypes,
        List<ReferenceType> supportedReferenceTypes,
        String outputContractVersion
) {
    public static final String SCHEMA_VERSION = "1";
    public static final String PURPOSE = "SCENARIO_EVENT_EXTRACTION";
    public static final String LOCALE = "ko-KR";
    public static final String OUTPUT_CONTRACT_VERSION = "1";

    public ExternalAiScenarioRequest {
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(locale);
        Objects.requireNonNull(currentYearMonth);
        Objects.requireNonNull(sanitizedScenarioText);
        supportedEventTypes = List.copyOf(supportedEventTypes);
        supportedReferenceTypes = List.copyOf(supportedReferenceTypes);
        Objects.requireNonNull(outputContractVersion);
    }
}
