package com.fintwin.fintwin.marketstress.marketdata;

import java.time.Instant;
import java.util.List;

public record MarketContext(MarketContextStatus status, Instant checkedAt, List<MarketObservation> observations) {
    public MarketContext {
        observations = List.copyOf(observations);
    }
}
