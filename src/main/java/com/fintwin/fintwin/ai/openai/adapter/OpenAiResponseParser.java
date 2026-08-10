package com.fintwin.fintwin.ai.openai.adapter;

import com.fintwin.fintwin.ai.openai.dto.OpenAiResponsesResponse;
import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

import java.util.List;

public final class OpenAiResponseParser {
    private final ObjectReader providerResponseReader;
    private final ObjectReader strictDraftReader;

    public OpenAiResponseParser(ObjectMapper objectMapper) {
        providerResponseReader = objectMapper.readerFor(OpenAiResponsesResponse.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        ObjectMapper strictMapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, config -> {
                    config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                })
                .build();
        strictDraftReader = strictMapper.readerFor(ExternalAiScenarioDraft.class);
    }

    public ExternalAiScenarioDraft parse(byte[] responseBody) {
        OpenAiResponsesResponse response = readProviderResponse(responseBody);
        if ("incomplete".equals(response.status())) {
            throw new AiAdapterException(AiErrorCode.AI_INCOMPLETE_RESPONSE);
        }
        if (!"completed".equals(response.status())) {
            throw new AiAdapterException(AiErrorCode.AI_INCOMPLETE_RESPONSE);
        }
        String outputText = extractSingleOutputText(response.output());
        try {
            return strictDraftReader.readValue(outputText);
        } catch (JacksonException exception) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION, exception);
        }
    }

    private OpenAiResponsesResponse readProviderResponse(byte[] responseBody) {
        try {
            OpenAiResponsesResponse response = providerResponseReader.readValue(responseBody);
            if (response == null) {
                throw new AiAdapterException(AiErrorCode.AI_EMPTY_RESPONSE);
            }
            return response;
        } catch (AiAdapterException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION, exception);
        }
    }

    private String extractSingleOutputText(List<OpenAiResponsesResponse.OutputItem> output) {
        if (output == null || output.isEmpty()) {
            throw new AiAdapterException(AiErrorCode.AI_EMPTY_RESPONSE);
        }
        List<OpenAiResponsesResponse.OutputItem> messages = output.stream()
                .filter(item -> "message".equals(item.type()))
                .toList();
        boolean containsUnexpectedOutput = output.stream()
                .anyMatch(item -> !"message".equals(item.type()) && !"reasoning".equals(item.type()));
        if (containsUnexpectedOutput || messages.size() != 1) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION);
        }
        List<OpenAiResponsesResponse.ContentPart> content = messages.getFirst().content();
        if (content == null || content.isEmpty()) {
            throw new AiAdapterException(AiErrorCode.AI_EMPTY_RESPONSE);
        }
        if (content.stream().anyMatch(part -> "refusal".equals(part.type()))) {
            throw new AiAdapterException(AiErrorCode.AI_REFUSED);
        }
        List<OpenAiResponsesResponse.ContentPart> outputTexts = content.stream()
                .filter(part -> "output_text".equals(part.type()))
                .toList();
        if (outputTexts.size() != 1 || content.size() != 1) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION);
        }
        String text = outputTexts.getFirst().text();
        if (text == null || text.isBlank()) {
            throw new AiAdapterException(AiErrorCode.AI_EMPTY_RESPONSE);
        }
        return text;
    }
}
