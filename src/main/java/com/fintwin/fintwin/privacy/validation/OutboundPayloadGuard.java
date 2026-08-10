package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.domain.ReferenceType;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OutboundPayloadGuard {
    private static final Set<String> ALLOWED_COMPONENTS = Set.of(
            "schemaVersion", "purpose", "locale", "currentYearMonth", "sanitizedScenarioText",
            "supportedEventTypes", "supportedReferenceTypes", "outputContractVersion");

    private final PersonalIdentifierDetector identifierDetector;
    private final FinancialValueTokenizer tokenizer;

    public OutboundPayloadGuard(PersonalIdentifierDetector identifierDetector,
                                FinancialValueTokenizer tokenizer) {
        this.identifierDetector = identifierDetector;
        this.tokenizer = tokenizer;
    }

    public void validate(Object payload) {
        if (payload == null || payload.getClass() != ExternalAiScenarioRequest.class) {
            throw new InvalidRequestException("Outbound AI payload type is not allowed");
        }
        validateRecordShape(payload.getClass());
        ExternalAiScenarioRequest request = (ExternalAiScenarioRequest) payload;
        if (!ExternalAiScenarioRequest.SCHEMA_VERSION.equals(request.schemaVersion())
                || !ExternalAiScenarioRequest.PURPOSE.equals(request.purpose())
                || !ExternalAiScenarioRequest.LOCALE.equals(request.locale())
                || !ExternalAiScenarioRequest.OUTPUT_CONTRACT_VERSION.equals(request.outputContractVersion())) {
            throw new InvalidRequestException("Outbound AI payload contract is invalid");
        }
        if (request.sanitizedScenarioText().isBlank()
                || request.sanitizedScenarioText().codePointCount(0,
                request.sanitizedScenarioText().length()) > 500) {
            throw new InvalidRequestException("Sanitized scenario text is invalid");
        }
        if (!identifierDetector.detect(request.sanitizedScenarioText()).isEmpty()) {
            throw new InvalidRequestException("Outbound AI payload contains a blocked identifier type");
        }
        if (!tokenizer.tokenize(request.sanitizedScenarioText()).references().isEmpty()) {
            throw new InvalidRequestException("Outbound AI payload contains an untokenized financial value");
        }
        if (!request.supportedEventTypes().equals(List.of(FinancialEventType.values()))
                || !request.supportedReferenceTypes().equals(List.of(ReferenceType.values()))) {
            throw new InvalidRequestException("Outbound AI payload capability allowlist is invalid");
        }
    }

    private void validateRecordShape(Class<?> payloadType) {
        if (!payloadType.isRecord()) {
            throw new InvalidRequestException("Outbound AI payload must be an immutable record");
        }
        Set<String> actualComponents = Arrays.stream(payloadType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
        if (!actualComponents.equals(ALLOWED_COMPONENTS)) {
            throw new InvalidRequestException("Outbound AI payload contains a non-allowlisted field");
        }
        boolean containsMap = Arrays.stream(payloadType.getRecordComponents())
                .map(RecordComponent::getType)
                .anyMatch(java.util.Map.class::isAssignableFrom);
        if (containsMap) {
            throw new InvalidRequestException("Outbound AI payload must not contain arbitrary maps");
        }
    }
}
