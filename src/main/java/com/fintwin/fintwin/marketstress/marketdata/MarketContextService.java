package com.fintwin.fintwin.marketstress.marketdata;

import com.fintwin.fintwin.marketstress.config.MarketDataProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketContextService {
    private final List<MarketDataGateway> gateways;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<MarketIndicator, CacheEntry> cache = new EnumMap<>(MarketIndicator.class);

    public MarketContextService(List<MarketDataGateway> gateways, MarketDataProperties properties,
                                Clock marketDataClock) {
        this.gateways = List.copyOf(gateways);
        this.properties = properties;
        this.clock = marketDataClock;
    }

    public synchronized MarketContext currentContext() {
        Instant checkedAt = clock.instant();
        LocalDate asOf = LocalDate.now(clock);
        List<MarketObservation> observations = new ArrayList<>();
        for (MarketIndicator indicator : MarketIndicator.values()) {
            observations.add(load(indicator, asOf, checkedAt));
        }
        long available = observations.stream()
                .filter(item -> item.status() == MarketDataStatus.AVAILABLE).count();
        long unavailable = observations.stream()
                .filter(item -> item.status() == MarketDataStatus.UNAVAILABLE).count();
        MarketContextStatus status = available == observations.size()
                ? MarketContextStatus.AVAILABLE
                : unavailable == observations.size() ? MarketContextStatus.UNAVAILABLE : MarketContextStatus.PARTIAL;
        return new MarketContext(status, checkedAt, observations);
    }

    private MarketObservation load(MarketIndicator indicator, LocalDate asOf, Instant checkedAt) {
        CacheEntry cached = cache.get(indicator);
        if (cached != null && checkedAt.isBefore(cached.cachedAt().plus(properties.getCacheTtl()))) {
            return applyFreshness(cached.observation(), asOf, checkedAt);
        }
        MarketDataGateway gateway = gateways.stream().filter(item -> item.supports(indicator)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No market data gateway for " + indicator));
        MarketObservation fetched = gateway.fetchLatest(indicator, asOf);
        if (fetched.status() == MarketDataStatus.AVAILABLE) {
            cache.put(indicator, new CacheEntry(fetched, checkedAt));
            return applyFreshness(fetched, asOf, checkedAt);
        }
        if (cached != null && cached.observation().value() != null) {
            return cached.observation().stale(MarketDataIssueCode.STALE_CACHE_FALLBACK, checkedAt);
        }
        return fetched;
    }

    private MarketObservation applyFreshness(MarketObservation observation, LocalDate asOf, Instant checkedAt) {
        if (observation.value() == null || observation.observedOn() == null) {
            return observation;
        }
        int limit = observation.indicator() == MarketIndicator.BOK_BASE_RATE
                ? properties.getBaseRateObservationStaleDays()
                : properties.getMarketObservationStaleDays();
        long ageDays = ChronoUnit.DAYS.between(observation.observedOn(), asOf);
        return ageDays > limit
                ? observation.stale(MarketDataIssueCode.OBSERVATION_TOO_OLD, checkedAt)
                : observation;
    }

    private record CacheEntry(MarketObservation observation, Instant cachedAt) {
    }
}
