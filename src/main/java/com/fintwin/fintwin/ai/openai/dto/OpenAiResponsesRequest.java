package com.fintwin.fintwin.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record OpenAiResponsesRequest(
        String model,
        String instructions,
        String input,
        boolean store,
        @JsonProperty("max_output_tokens") int maxOutputTokens,
        TextConfiguration text
) {
    public record TextConfiguration(StructuredOutputFormat format) {
    }

    public record StructuredOutputFormat(
            String type,
            String name,
            boolean strict,
            @JsonRawValue String schema
    ) {
    }
}
