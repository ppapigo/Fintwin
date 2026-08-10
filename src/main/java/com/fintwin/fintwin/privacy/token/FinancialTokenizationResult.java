package com.fintwin.fintwin.privacy.token;

import com.fintwin.fintwin.privacy.domain.FinancialReference;

import java.util.List;

public record FinancialTokenizationResult(
        String sanitizedText,
        List<FinancialReference> references,
        FinancialReferenceVault referenceVault
) {
    public FinancialTokenizationResult {
        references = List.copyOf(references);
    }
}
