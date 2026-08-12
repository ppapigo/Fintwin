package com.fintwin.fintwin.marketstress.marketdata;

import java.time.LocalDate;

public interface MarketDataGateway {
    boolean supports(MarketIndicator indicator);

    MarketObservation fetchLatest(MarketIndicator indicator, LocalDate asOfDate);
}
