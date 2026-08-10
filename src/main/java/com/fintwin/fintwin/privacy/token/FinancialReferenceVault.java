package com.fintwin.fintwin.privacy.token;

import com.fintwin.fintwin.privacy.domain.ReferenceType;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FinancialReferenceVault {
    private final Map<String, StoredReference> references;

    private FinancialReferenceVault(Map<String, StoredReference> references) {
        this.references = Map.copyOf(references);
    }

    public static FinancialReferenceVault empty() {
        return new FinancialReferenceVault(Map.of());
    }

    static Builder builder() {
        return new Builder();
    }

    public int size() {
        return references.size();
    }

    public boolean contains(String referenceId) {
        return references.containsKey(referenceId);
    }

    public ReferenceType typeOf(String referenceId) {
        return require(referenceId).type;
    }

    public BigDecimal requireMoney(String referenceId) {
        return (BigDecimal) require(referenceId, ReferenceType.MONEY).value;
    }

    public BigDecimal requirePercent(String referenceId) {
        return (BigDecimal) require(referenceId, ReferenceType.PERCENT).value;
    }

    public int requireDurationMonths(String referenceId) {
        return (Integer) require(referenceId, ReferenceType.DURATION).value;
    }

    public YearMonth requireDate(String referenceId) {
        return (YearMonth) require(referenceId, ReferenceType.DATE).value;
    }

    private StoredReference require(String referenceId, ReferenceType expectedType) {
        StoredReference stored = require(referenceId);
        if (stored.type != expectedType) {
            throw new IllegalArgumentException("Financial reference type does not match the target field");
        }
        return stored;
    }

    private StoredReference require(String referenceId) {
        StoredReference stored = references.get(referenceId);
        if (stored == null) {
            throw new IllegalArgumentException("Financial reference does not exist in this request");
        }
        return stored;
    }

    @Override
    public String toString() {
        return "FinancialReferenceVault[request-scoped, referenceCount=" + references.size() + "]";
    }

    static final class Builder {
        private final Map<String, StoredReference> references = new LinkedHashMap<>();

        void put(String referenceId, ReferenceType type, Object value) {
            Objects.requireNonNull(referenceId);
            Objects.requireNonNull(type);
            Objects.requireNonNull(value);
            if (references.putIfAbsent(referenceId, new StoredReference(type, value)) != null) {
                throw new IllegalArgumentException("Duplicate financial reference ID");
            }
        }

        FinancialReferenceVault build() {
            return new FinancialReferenceVault(references);
        }
    }

    private static final class StoredReference {
        private final ReferenceType type;
        private final Object value;

        private StoredReference(ReferenceType type, Object value) {
            this.type = type;
            this.value = value;
        }
    }
}
