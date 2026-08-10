package com.fintwin.fintwin.privacy.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record FinancialReference(String referenceId, ReferenceType referenceType) {
    private static final Pattern REFERENCE_ID = Pattern.compile("^(MONEY|PERCENT|DURATION|DATE)_[1-9][0-9]*$");

    public FinancialReference {
        Objects.requireNonNull(referenceId);
        Objects.requireNonNull(referenceType);
        if (!REFERENCE_ID.matcher(referenceId).matches()
                || !referenceId.startsWith(referenceType.name() + "_")) {
            throw new IllegalArgumentException("Invalid financial reference descriptor");
        }
    }
}
