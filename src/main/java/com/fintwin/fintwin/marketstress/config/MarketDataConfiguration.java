package com.fintwin.fintwin.marketstress.config;

import com.fintwin.fintwin.marketstress.engine.MarketStressSimulationEngine;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfiguration {
    @Bean("krxMarketDataRestClient")
    public RestClient krxMarketDataRestClient(MarketDataProperties properties) {
        requireOfficialEndpoint(properties.getKrxBaseUrl(), "data-dbg.krx.co.kr");
        return restClient(properties.getKrxBaseUrl().toString(), properties);
    }

    @Bean("bokMarketDataRestClient")
    public RestClient bokMarketDataRestClient(MarketDataProperties properties) {
        requireOfficialEndpoint(properties.getBokEcosBaseUrl(), "ecos.bok.or.kr");
        return restClient(properties.getBokEcosBaseUrl().toString(), properties);
    }

    @Bean
    public MarketStressSimulationEngine marketStressSimulationEngine(
            MonthlyFinancialSimulationEngine monthlyFinancialSimulationEngine) {
        return new MarketStressSimulationEngine(monthlyFinancialSimulationEngine);
    }

    private RestClient restClient(String baseUrl, MarketDataProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private void requireOfficialEndpoint(java.net.URI uri, String expectedHost) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !expectedHost.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Market data endpoint must use the configured official HTTPS host");
        }
    }
}
