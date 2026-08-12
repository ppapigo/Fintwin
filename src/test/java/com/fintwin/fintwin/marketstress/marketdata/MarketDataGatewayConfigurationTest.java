package com.fintwin.fintwin.marketstress.marketdata;

import com.fintwin.fintwin.marketstress.config.MarketDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MarketDataGatewayConfigurationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void missingCredentialsReturnUnavailableWithoutNetworkAccess() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setEnabled(true);
        RestClient unused = RestClient.builder().baseUrl("http://127.0.0.1:1").build();

        MarketObservation kospi = new KrxMarketDataGateway(unused, properties, new ObjectMapper(), CLOCK)
                .fetchLatest(MarketIndicator.KOSPI_INDEX, LocalDate.of(2026, 8, 11));
        MarketObservation exchange = new BokEcosMarketDataGateway(unused, properties, new ObjectMapper(), CLOCK)
                .fetchLatest(MarketIndicator.KRW_USD_EXCHANGE_RATE, LocalDate.of(2026, 8, 11));

        assertThat(kospi.status()).isEqualTo(MarketDataStatus.UNAVAILABLE);
        assertThat(kospi.issueCode()).isEqualTo(MarketDataIssueCode.CREDENTIAL_MISSING);
        assertThat(kospi.value()).isNull();
        assertThat(exchange.status()).isEqualTo(MarketDataStatus.UNAVAILABLE);
        assertThat(exchange.issueCode()).isEqualTo(MarketDataIssueCode.CREDENTIAL_MISSING);
        assertThat(properties.toString()).doesNotContain("krxApiKey=,").contains("[REDACTED]");
    }

    @Test
    void parsesOfficialKrxKospiResponseAndSendsOnlyTheOfficialQuery() {
        MarketDataProperties properties = enabledProperties();
        properties.setKrxApiKey("krx-secret");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://data-dbg.krx.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data-dbg.krx.co.kr/svc/apis/idx/kospi_dd_trd?basDd=20260810"))
                .andExpect(header("AUTH_KEY", "krx-secret"))
                .andRespond(withSuccess("""
                        {"OutBlock_1":[{"BAS_DD":"20260810","IDX_NM":"코스피","CLSPRC_IDX":"3,250.25"}]}
                        """, MediaType.APPLICATION_JSON));

        MarketObservation observation = new KrxMarketDataGateway(builder.build(), properties,
                new ObjectMapper(), CLOCK).fetchLatest(MarketIndicator.KOSPI_INDEX,
                LocalDate.of(2026, 8, 11));

        assertThat(observation.status()).isEqualTo(MarketDataStatus.AVAILABLE);
        assertThat(observation.value()).isEqualByComparingTo("3250.25");
        assertThat(observation.observedOn()).isEqualTo(LocalDate.of(2026, 8, 10));
        server.verify();
    }

    @Test
    void parsesOfficialEcosExchangeRateResponseWithoutUserFinancialData() {
        MarketDataProperties properties = enabledProperties();
        properties.setBokEcosApiKey("bok-secret");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ecos.bok.or.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ecos.bok.or.kr/api/StatisticSearch/bok-secret/json/kr/1/100/731Y001/D/20260712/20260811/0000001"))
                .andRespond(withSuccess("""
                        {"StatisticSearch":{"row":[{"TIME":"20260808","DATA_VALUE":"1,380.50"},
                        {"TIME":"20260810","DATA_VALUE":"1,375.25"}]}}
                        """, MediaType.APPLICATION_JSON));

        MarketObservation observation = new BokEcosMarketDataGateway(builder.build(), properties,
                new ObjectMapper(), CLOCK).fetchLatest(MarketIndicator.KRW_USD_EXCHANGE_RATE,
                LocalDate.of(2026, 8, 11));

        assertThat(observation.status()).isEqualTo(MarketDataStatus.AVAILABLE);
        assertThat(observation.value()).isEqualByComparingTo("1375.25");
        assertThat(observation.observedOn()).isEqualTo(LocalDate.of(2026, 8, 10));
        server.verify();
    }

    @Test
    void convertsOfficialProviderFailureToUnavailableWithoutRawDetails() {
        MarketDataProperties properties = enabledProperties();
        properties.setKrxApiKey("krx-secret");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://data-dbg.krx.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data-dbg.krx.co.kr/svc/apis/idx/kospi_dd_trd?basDd=20260810"))
                .andRespond(withServerError());

        MarketObservation observation = new KrxMarketDataGateway(builder.build(), properties,
                new ObjectMapper(), CLOCK).fetchLatest(MarketIndicator.KOSPI_INDEX,
                LocalDate.of(2026, 8, 11));

        assertThat(observation.status()).isEqualTo(MarketDataStatus.UNAVAILABLE);
        assertThat(observation.issueCode()).isEqualTo(MarketDataIssueCode.PROVIDER_UNAVAILABLE);
        assertThat(observation.value()).isNull();
        server.verify();
    }

    private MarketDataProperties enabledProperties() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setEnabled(true);
        return properties;
    }
}
