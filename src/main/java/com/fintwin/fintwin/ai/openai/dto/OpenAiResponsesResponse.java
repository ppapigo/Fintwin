package com.fintwin.fintwin.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponsesResponse(
        String status,
        @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
        List<OutputItem> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(String type, String role, String status, List<ContentPart> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(String type, String text, String refusal) {
    }
}
