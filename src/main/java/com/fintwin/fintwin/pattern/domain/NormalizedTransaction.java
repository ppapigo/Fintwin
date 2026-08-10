package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record NormalizedTransaction(
        LocalDate transactionDate,
        TransactionType type,
        BigDecimal amount,
        TransactionCategory category,
        String description,
        String transactionId
) {
    public NormalizedTransaction {
        Objects.requireNonNull(transactionDate);
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(category);
        Objects.requireNonNull(description);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
