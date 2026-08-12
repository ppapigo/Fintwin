package com.fintwin.fintwin.marketstress.marketdata;

import com.fintwin.fintwin.marketstress.config.MarketDataProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Component
public class BokEcosMarketDataGateway implements MarketDataGateway {
    private static final String SOURCE = "BANK_OF_KOREA_ECOS";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final RestClient restClient;
    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BokEcosMarketDataGateway(@Qualifier("bokMarketDataRestClient") RestClient restClient,
                                    MarketDataProperties properties, ObjectMapper objectMapper,
                                    Clock marketDataClock) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = marketDataClock;
    }

    @Override
    public boolean supports(MarketIndicator indicator) {
        return indicator == MarketIndicator.KRW_USD_EXCHANGE_RATE
                || indicator == MarketIndicator.BOK_BASE_RATE;
    }

    @Override
    public MarketObservation fetchLatest(MarketIndicator indicator, LocalDate asOfDate) {
        if (!supports(indicator)) {
            throw new IllegalArgumentException("Unsupported market indicator");
        }
        Instant checkedAt = clock.instant();
        if (!properties.isEnabled()) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.FEATURE_DISABLED,
                    checkedAt, SOURCE);
        }
        if (properties.getBokEcosApiKey() == null || properties.getBokEcosApiKey().isBlank()) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.CREDENTIAL_MISSING,
                    checkedAt, SOURCE);
        }
        Query query = Query.forIndicator(indicator, asOfDate);
        try {
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.pathSegment("api", "StatisticSearch",
                                    properties.getBokEcosApiKey(), "json", "kr", "1", "100",
                                    query.statCode(), query.cycle(), query.start(), query.end(), query.itemCode())
                            .build())
                    .exchange((request, response) -> readSuccessfulBody(response.getStatusCode(),
                            response.getBody()));
            return parse(body, query, checkedAt);
        } catch (ResourceAccessException exception) {
            MarketDataIssueCode code = hasTimeoutCause(exception)
                    ? MarketDataIssueCode.PROVIDER_TIMEOUT : MarketDataIssueCode.PROVIDER_UNAVAILABLE;
            return MarketObservation.unavailable(indicator, code, checkedAt, SOURCE);
        } catch (MarketProviderResponseException exception) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.PROVIDER_UNAVAILABLE,
                    checkedAt, SOURCE);
        } catch (RuntimeException exception) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.INVALID_PROVIDER_RESPONSE,
                    checkedAt, SOURCE);
        }
    }

    private MarketObservation parse(byte[] body, Query query, Instant checkedAt) {
        try {
            JsonNode rows = objectMapper.readTree(body).path("StatisticSearch").path("row");
            if (!rows.isArray() || rows.isEmpty()) {
                return MarketObservation.unavailable(query.indicator(), MarketDataIssueCode.DATA_NOT_FOUND,
                        checkedAt, SOURCE);
            }
            JsonNode latest = null;
            for (JsonNode row : rows) {
                if (latest == null || row.path("TIME").asText("")
                        .compareTo(latest.path("TIME").asText("")) > 0) {
                    latest = row;
                }
            }
            String time = latest.path("TIME").asText();
            LocalDate observedOn = query.cycle().equals("D")
                    ? LocalDate.parse(time, DAY) : YearMonth.parse(time, MONTH).atDay(1);
            String rawValue = latest.path("DATA_VALUE").asText("").replace(",", "");
            return MarketObservation.available(query.indicator(), new java.math.BigDecimal(rawValue),
                    query.unit(), observedOn, checkedAt, SOURCE);
        } catch (RuntimeException exception) {
            return MarketObservation.unavailable(query.indicator(),
                    MarketDataIssueCode.INVALID_PROVIDER_RESPONSE, checkedAt, SOURCE);
        }
    }

    private byte[] readSuccessfulBody(HttpStatusCode status, java.io.InputStream inputStream) {
        if (!status.is2xxSuccessful()) {
            throw new MarketProviderResponseException();
        }
        try (inputStream) {
            byte[] body = inputStream.readNBytes(properties.getMaxResponseBytes() + 1);
            if (body.length > properties.getMaxResponseBytes()) {
                throw new MarketProviderResponseException();
            }
            return body;
        } catch (IOException exception) {
            throw new MarketProviderResponseException();
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Query(MarketIndicator indicator, String statCode, String cycle,
                         String start, String end, String itemCode, String unit) {
        static Query forIndicator(MarketIndicator indicator, LocalDate asOfDate) {
            if (indicator == MarketIndicator.KRW_USD_EXCHANGE_RATE) {
                return new Query(indicator, "731Y001", "D", asOfDate.minusDays(30).format(DAY),
                        asOfDate.format(DAY), "0000001", "KRW per USD");
            }
            return new Query(indicator, "722Y001", "M", YearMonth.from(asOfDate).minusMonths(12).format(MONTH),
                    YearMonth.from(asOfDate).format(MONTH), "0101000", "percent");
        }
    }

    private static final class MarketProviderResponseException extends RuntimeException {
    }
}
