package com.fintwin.fintwin.marketstress.dto;

import com.fintwin.fintwin.marketstress.marketdata.MarketContext;
import com.fintwin.fintwin.marketstress.marketdata.MarketContextStatus;
import com.fintwin.fintwin.marketstress.marketdata.MarketDataIssueCode;
import com.fintwin.fintwin.marketstress.marketdata.MarketDataStatus;
import com.fintwin.fintwin.marketstress.marketdata.MarketIndicator;
import com.fintwin.fintwin.marketstress.marketdata.MarketObservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarketContextResponse(
        MarketContextStatus status,
        Instant checkedAt,
        List<ObservationResponse> observations,
        String usageBoundary
) {
    public static MarketContextResponse from(MarketContext context) {
        return new MarketContextResponse(context.status(), context.checkedAt(),
                context.observations().stream().map(ObservationResponse::from).toList(),
                "Current observations are context only and are never used to predict returns or alter stress assumptions.");
    }

    public record ObservationResponse(
            MarketIndicator indicator,
            BigDecimal value,
            String unit,
            LocalDate observedOn,
            Instant retrievedAt,
            MarketDataStatus status,
            MarketDataIssueCode issueCode,
            String source
    ) {
        static ObservationResponse from(MarketObservation observation) {
            return new ObservationResponse(observation.indicator(), observation.value(), observation.unit(),
                    observation.observedOn(), observation.retrievedAt(), observation.status(),
                    observation.issueCode(), observation.source());
        }
    }
}
