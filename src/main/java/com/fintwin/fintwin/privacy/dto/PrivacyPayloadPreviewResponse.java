package com.fintwin.fintwin.privacy.dto;

import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.domain.FinancialReference;
import com.fintwin.fintwin.privacy.domain.ScenarioPrivacyEnvelope;

import java.util.List;

public record PrivacyPayloadPreviewResponse(
        String status,
        ExternalAiScenarioRequest externalPayload,
        List<FinancialReference> references,
        List<String> blockedIdentifierTypes,
        String privacyNotice
) {
    private static final String NOTICE = "Exact financial values and user financial profiles are not included "
            + "in the external AI payload.";

    public PrivacyPayloadPreviewResponse {
        references = List.copyOf(references);
        blockedIdentifierTypes = List.copyOf(blockedIdentifierTypes);
    }

    public static PrivacyPayloadPreviewResponse from(ScenarioPrivacyEnvelope envelope) {
        return new PrivacyPayloadPreviewResponse(envelope.status().name(), envelope.externalRequest(),
                envelope.references(), envelope.blockedIdentifierTypes().stream().map(Enum::name).toList(), NOTICE);
    }
}
