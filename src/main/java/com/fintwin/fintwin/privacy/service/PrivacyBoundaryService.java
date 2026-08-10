package com.fintwin.fintwin.privacy.service;

import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.domain.PersonalIdentifierType;
import com.fintwin.fintwin.privacy.domain.PrivacyMode;
import com.fintwin.fintwin.privacy.domain.PrivacyStatus;
import com.fintwin.fintwin.privacy.domain.ReferenceType;
import com.fintwin.fintwin.privacy.domain.ScenarioPrivacyEnvelope;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.FinancialTokenizationResult;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Service
public class PrivacyBoundaryService {
    private static final int MAX_SCENARIO_CODE_POINTS = 500;

    private final PersonalIdentifierDetector identifierDetector;
    private final FinancialValueTokenizer tokenizer;
    private final OutboundPayloadGuard outboundPayloadGuard;
    private final Clock clock;

    public PrivacyBoundaryService(PersonalIdentifierDetector identifierDetector,
                                  FinancialValueTokenizer tokenizer,
                                  OutboundPayloadGuard outboundPayloadGuard,
                                  Clock clock) {
        this.identifierDetector = identifierDetector;
        this.tokenizer = tokenizer;
        this.outboundPayloadGuard = outboundPayloadGuard;
        this.clock = clock;
    }

    public ScenarioPrivacyEnvelope createPreview(String scenarioText) {
        String normalized = validateInput(scenarioText);
        List<PersonalIdentifierType> blockedTypes = identifierDetector.detect(normalized);
        if (!blockedTypes.isEmpty()) {
            return new ScenarioPrivacyEnvelope(PrivacyMode.STRICT, PrivacyStatus.BLOCKED, null,
                    List.of(), blockedTypes, FinancialReferenceVault.empty());
        }

        FinancialTokenizationResult tokenized;
        try {
            tokenized = tokenizer.tokenize(normalized);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new InvalidRequestException("scenarioText contains an unsupported financial value");
        }
        ExternalAiScenarioRequest externalRequest = new ExternalAiScenarioRequest(
                ExternalAiScenarioRequest.SCHEMA_VERSION,
                ExternalAiScenarioRequest.PURPOSE,
                ExternalAiScenarioRequest.LOCALE,
                YearMonth.now(clock),
                tokenized.sanitizedText(),
                List.of(FinancialEventType.values()),
                List.of(ReferenceType.values()),
                ExternalAiScenarioRequest.OUTPUT_CONTRACT_VERSION);
        outboundPayloadGuard.validate(externalRequest);
        return new ScenarioPrivacyEnvelope(PrivacyMode.STRICT, PrivacyStatus.SAFE, externalRequest,
                tokenized.references(), List.of(), tokenized.referenceVault());
    }

    private String validateInput(String scenarioText) {
        if (scenarioText == null || scenarioText.isBlank()) {
            throw new InvalidRequestException("scenarioText is required");
        }
        if (scenarioText.codePointCount(0, scenarioText.length()) > MAX_SCENARIO_CODE_POINTS) {
            throw new InvalidRequestException("scenarioText must be at most 500 characters");
        }
        return scenarioText.strip();
    }
}
