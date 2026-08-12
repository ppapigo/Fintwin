package com.fintwin.fintwin.marketstress.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketObservation(
        MarketIndicator indicator,
        BigDecimal value,
        String unit,
        LocalDate observedOn,
        Instant retrievedAt,
        MarketDataStatus status,
        MarketDataIssueCode issueCode,
        String source
) {
    public static MarketObservation available(MarketIndicator indicator, BigDecimal value, String unit,
                                              LocalDate observedOn, Instant retrievedAt, String source) {
        return new MarketObservation(indicator, value, unit, observedOn, retrievedAt,
                MarketDataStatus.AVAILABLE, MarketDataIssueCode.NONE, source);
    }

    public static MarketObservation unavailable(MarketIndicator indicator, MarketDataIssueCode issueCode,
                                                Instant retrievedAt, String source) {
        return new MarketObservation(indicator, null, null, null, retrievedAt,
                MarketDataStatus.UNAVAILABLE, issueCode, source);
    }

    public MarketObservation stale(MarketDataIssueCode staleIssue, Instant checkedAt) {
        return new MarketObservation(indicator, value, unit, observedOn, checkedAt,
                MarketDataStatus.STALE, staleIssue, source);
    }
}
