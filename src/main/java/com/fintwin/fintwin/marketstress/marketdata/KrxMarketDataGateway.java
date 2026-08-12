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
import java.time.format.DateTimeFormatter;

@Component
public class KrxMarketDataGateway implements MarketDataGateway {
    private static final String SOURCE = "KRX_OPEN_API";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KrxMarketDataGateway(@Qualifier("krxMarketDataRestClient") RestClient restClient,
                                MarketDataProperties properties, ObjectMapper objectMapper,
                                Clock marketDataClock) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = marketDataClock;
    }

    @Override
    public boolean supports(MarketIndicator indicator) {
        return indicator == MarketIndicator.KOSPI_INDEX;
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
        if (properties.getKrxApiKey() == null || properties.getKrxApiKey().isBlank()) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.CREDENTIAL_MISSING,
                    checkedAt, SOURCE);
        }
        try {
            LocalDate requestedBusinessDate = asOfDate.minusDays(1);
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/svc/apis/idx/kospi_dd_trd")
                            .queryParam("basDd", requestedBusinessDate.format(BASIC_DATE)).build())
                    .header("AUTH_KEY", properties.getKrxApiKey())
                    .exchange((request, response) -> readSuccessfulBody(response.getStatusCode(),
                            response.getBody()));
            return parse(body, indicator, checkedAt);
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

    private MarketObservation parse(byte[] body, MarketIndicator indicator, Instant checkedAt) {
        try {
            JsonNode rows = objectMapper.readTree(body).path("OutBlock_1");
            if (!rows.isArray()) {
                return MarketObservation.unavailable(indicator, MarketDataIssueCode.INVALID_PROVIDER_RESPONSE,
                        checkedAt, SOURCE);
            }
            for (JsonNode row : rows) {
                String name = row.path("IDX_NM").asText("");
                if ("코스피".equals(name) || "KOSPI".equalsIgnoreCase(name)) {
                    String rawValue = row.path("CLSPRC_IDX").asText("").replace(",", "");
                    LocalDate observedOn = LocalDate.parse(row.path("BAS_DD").asText(), BASIC_DATE);
                    return MarketObservation.available(indicator, new java.math.BigDecimal(rawValue), "index",
                            observedOn, checkedAt, SOURCE);
                }
            }
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.DATA_NOT_FOUND,
                    checkedAt, SOURCE);
        } catch (RuntimeException exception) {
            return MarketObservation.unavailable(indicator, MarketDataIssueCode.INVALID_PROVIDER_RESPONSE,
                    checkedAt, SOURCE);
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

    private static final class MarketProviderResponseException extends RuntimeException {
    }
}
