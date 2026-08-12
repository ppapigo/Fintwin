package com.fintwin.fintwin.marketstress.marketdata;

import com.fintwin.fintwin.marketstress.config.MarketDataProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketContextServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsUnavailableWithoutInventingValues() {
        MarketDataProperties properties = properties();
        MarketDataGateway unavailable = gateway((indicator, date) -> MarketObservation.unavailable(indicator,
                MarketDataIssueCode.CREDENTIAL_MISSING, NOW, source(indicator)));
        MarketContextService service = new MarketContextService(List.of(unavailable), properties, CLOCK);

        MarketContext context = service.currentContext();

        assertThat(context.status()).isEqualTo(MarketContextStatus.UNAVAILABLE);
        assertThat(context.observations()).allSatisfy(observation -> {
            assertThat(observation.status()).isEqualTo(MarketDataStatus.UNAVAILABLE);
            assertThat(observation.value()).isNull();
            assertThat(observation.issueCode()).isEqualTo(MarketDataIssueCode.CREDENTIAL_MISSING);
        });
    }

    @Test
    void marksOldOfficialObservationAsStale() {
        MarketDataGateway gateway = gateway((indicator, date) -> MarketObservation.available(indicator,
                new BigDecimal("100"), "unit", indicator == MarketIndicator.BOK_BASE_RATE
                        ? LocalDate.of(2026, 7, 1) : LocalDate.of(2026, 7, 1), NOW, source(indicator)));
        MarketContextService service = new MarketContextService(List.of(gateway), properties(), CLOCK);

        MarketContext context = service.currentContext();

        assertThat(context.status()).isEqualTo(MarketContextStatus.PARTIAL);
        assertThat(context.observations()).filteredOn(item -> item.indicator() != MarketIndicator.BOK_BASE_RATE)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(MarketDataStatus.STALE);
                    assertThat(item.issueCode()).isEqualTo(MarketDataIssueCode.OBSERVATION_TOO_OLD);
                });
        assertThat(context.observations()).filteredOn(item -> item.indicator() == MarketIndicator.BOK_BASE_RATE)
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(MarketDataStatus.AVAILABLE));
    }

    @Test
    void usesStaleCachedValueOnlyAfterAProviderFailure() {
        MarketDataProperties properties = properties();
        properties.setCacheTtl(Duration.ZERO);
        MutableGateway gateway = new MutableGateway();
        MarketContextService service = new MarketContextService(List.of(gateway), properties, CLOCK);
        service.currentContext();
        gateway.available = false;

        MarketContext fallback = service.currentContext();

        assertThat(fallback.observations()).allSatisfy(item -> {
            assertThat(item.status()).isEqualTo(MarketDataStatus.STALE);
            assertThat(item.issueCode()).isEqualTo(MarketDataIssueCode.STALE_CACHE_FALLBACK);
            assertThat(item.value()).isEqualByComparingTo("100");
        });
    }

    private MarketDataProperties properties() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setCacheTtl(Duration.ofMinutes(30));
        properties.setMarketObservationStaleDays(7);
        properties.setBaseRateObservationStaleDays(45);
        return properties;
    }

    private MarketDataGateway gateway(Fetch fetch) {
        return new MarketDataGateway() {
            @Override public boolean supports(MarketIndicator indicator) { return true; }
            @Override public MarketObservation fetchLatest(MarketIndicator indicator, LocalDate asOfDate) {
                return fetch.load(indicator, asOfDate);
            }
        };
    }

    private String source(MarketIndicator indicator) {
        return indicator == MarketIndicator.KOSPI_INDEX ? "KRX_OPEN_API" : "BANK_OF_KOREA_ECOS";
    }

    @FunctionalInterface
    private interface Fetch {
        MarketObservation load(MarketIndicator indicator, LocalDate asOfDate);
    }

    private static final class MutableGateway implements MarketDataGateway {
        private boolean available = true;
        @Override public boolean supports(MarketIndicator indicator) { return true; }
        @Override public MarketObservation fetchLatest(MarketIndicator indicator, LocalDate asOfDate) {
            return available
                    ? MarketObservation.available(indicator, new BigDecimal("100"), "unit", asOfDate, NOW, "OFFICIAL")
                    : MarketObservation.unavailable(indicator, MarketDataIssueCode.PROVIDER_UNAVAILABLE, NOW, "OFFICIAL");
        }
    }
}
