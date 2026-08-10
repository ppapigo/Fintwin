package com.fintwin.fintwin.ai.openai.config;

import com.fintwin.fintwin.ai.openai.adapter.OpenAiExternalAiGateway;
import com.fintwin.fintwin.ai.openai.adapter.OpenAiResponseParser;
import com.fintwin.fintwin.ai.openai.prompt.OpenAiPromptResources;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "fintwin.ai", name = "enabled", havingValue = "true")
    public OpenAiPromptResources openAiPromptResources() {
        return OpenAiPromptResources.load();
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.ai", name = "enabled", havingValue = "true")
    public RestClient openAiRestClient(OpenAiProperties properties, Environment environment) {
        var endpointPolicy = new OpenAiEndpointPolicy();
        var baseUrl = endpointPolicy.validate(properties.getBaseUrl(), environment);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.ai", name = "enabled", havingValue = "true")
    public OpenAiResponseParser openAiResponseParser(ObjectMapper objectMapper) {
        return new OpenAiResponseParser(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.ai", name = "enabled", havingValue = "true")
    public ExternalAiGateway openAiExternalAiGateway(RestClient openAiRestClient,
                                              OpenAiProperties properties,
                                              OpenAiPromptResources promptResources,
                                              OutboundPayloadGuard outboundPayloadGuard,
                                              OpenAiResponseParser responseParser,
                                              ObjectMapper objectMapper) {
        return new OpenAiExternalAiGateway(openAiRestClient, properties, promptResources,
                outboundPayloadGuard, responseParser, objectMapper);
    }
}
