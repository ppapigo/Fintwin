package com.fintwin.fintwin.ai.openai.adapter;

import com.fintwin.fintwin.ai.openai.config.OpenAiProperties;
import com.fintwin.fintwin.ai.openai.dto.OpenAiResponsesRequest;
import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.ai.openai.prompt.OpenAiPromptResources;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;

public final class OpenAiExternalAiGateway implements ExternalAiGateway {
    private static final String RESPONSES_PATH = "/v1/responses";

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final OpenAiPromptResources promptResources;
    private final OutboundPayloadGuard outboundPayloadGuard;
    private final OpenAiResponseParser responseParser;
    private final ObjectMapper objectMapper;

    public OpenAiExternalAiGateway(RestClient restClient,
                                   OpenAiProperties properties,
                                   OpenAiPromptResources promptResources,
                                   OutboundPayloadGuard outboundPayloadGuard,
                                   OpenAiResponseParser responseParser,
                                   ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptResources = promptResources;
        this.outboundPayloadGuard = outboundPayloadGuard;
        this.responseParser = responseParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalAiScenarioDraft extractScenarioEvents(ExternalAiScenarioRequest request) {
        validateOutboundRequest(request);
        OpenAiResponsesRequest providerRequest = buildProviderRequest(request);
        byte[] responseBody = executeOnce(providerRequest);
        return responseParser.parse(responseBody);
    }

    private void validateOutboundRequest(ExternalAiScenarioRequest request) {
        try {
            outboundPayloadGuard.validate(request);
        } catch (InvalidRequestException exception) {
            throw new AiAdapterException(AiErrorCode.AI_PRIVACY_GUARD_REJECTED, exception);
        }
    }

    private OpenAiResponsesRequest buildProviderRequest(ExternalAiScenarioRequest request) {
        String input;
        try {
            input = objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new AiAdapterException(AiErrorCode.AI_UNKNOWN_ERROR, exception);
        }
        OpenAiResponsesRequest.StructuredOutputFormat format =
                new OpenAiResponsesRequest.StructuredOutputFormat("json_schema", "fintwin_scenario_draft",
                        true, promptResources.schema());
        return new OpenAiResponsesRequest(properties.getModel(), promptResources.instructions(), input, false,
                properties.getMaxOutputTokens(), new OpenAiResponsesRequest.TextConfiguration(format));
    }

    private byte[] executeOnce(OpenAiResponsesRequest request) {
        try {
            return restClient.post()
                    .uri(RESPONSES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        byte[] body = readLimitedBody(clientResponse.getBody());
                        HttpStatusCode status = clientResponse.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            throw forHttpStatus(status.value());
                        }
                        return body;
                    });
        } catch (AiAdapterException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, HttpTimeoutException.class)
                    || hasCause(exception, SocketTimeoutException.class)) {
                throw new AiAdapterException(AiErrorCode.AI_TIMEOUT, exception);
            }
            if (hasCause(exception, ConnectException.class)) {
                throw new AiAdapterException(AiErrorCode.AI_PROVIDER_UNAVAILABLE, exception);
            }
            throw new AiAdapterException(AiErrorCode.AI_PROVIDER_UNAVAILABLE, exception);
        } catch (RestClientException | UncheckedIOException exception) {
            throw new AiAdapterException(AiErrorCode.AI_PROVIDER_UNAVAILABLE, exception);
        } catch (RuntimeException exception) {
            throw new AiAdapterException(AiErrorCode.AI_UNKNOWN_ERROR, exception);
        }
    }

    private byte[] readLimitedBody(java.io.InputStream inputStream) {
        try (inputStream) {
            byte[] body = inputStream.readNBytes(properties.getMaxResponseBytes() + 1);
            if (body.length > properties.getMaxResponseBytes()) {
                throw new AiAdapterException(AiErrorCode.AI_RESPONSE_TOO_LARGE);
            }
            return body;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private AiAdapterException forHttpStatus(int status) {
        return switch (status) {
            case 400 -> new AiAdapterException(AiErrorCode.AI_CONFIGURATION_INVALID);
            case 401, 403 -> new AiAdapterException(AiErrorCode.AI_AUTHENTICATION_FAILED);
            case 429 -> new AiAdapterException(AiErrorCode.AI_RATE_LIMITED);
            default -> status >= 500
                    ? new AiAdapterException(AiErrorCode.AI_PROVIDER_UNAVAILABLE)
                    : new AiAdapterException(AiErrorCode.AI_UNKNOWN_ERROR);
        };
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
