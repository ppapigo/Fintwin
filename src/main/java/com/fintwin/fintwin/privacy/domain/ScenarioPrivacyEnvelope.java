package com.fintwin.fintwin.privacy.domain;

import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;

import java.util.List;
import java.util.Objects;

public record ScenarioPrivacyEnvelope(
        PrivacyMode privacyMode,
        PrivacyStatus status,
        ExternalAiScenarioRequest externalRequest,
        List<FinancialReference> references,
        List<PersonalIdentifierType> blockedIdentifierTypes,
        FinancialReferenceVault referenceVault
) {
    public ScenarioPrivacyEnvelope {
        Objects.requireNonNull(privacyMode);
        Objects.requireNonNull(status);
        references = List.copyOf(references);
        blockedIdentifierTypes = List.copyOf(blockedIdentifierTypes);
        Objects.requireNonNull(referenceVault);
        if (status == PrivacyStatus.SAFE && externalRequest == null) {
            throw new IllegalArgumentException("Safe privacy envelope requires an external request");
        }
        if (status == PrivacyStatus.BLOCKED && externalRequest != null) {
            throw new IllegalArgumentException("Blocked privacy envelope must not expose an external request");
        }
    }
}
