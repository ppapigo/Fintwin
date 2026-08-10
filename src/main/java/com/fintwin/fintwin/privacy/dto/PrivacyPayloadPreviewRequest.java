package com.fintwin.fintwin.privacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrivacyPayloadPreviewRequest(
        @NotNull @NotBlank @Size(max = 500) String scenarioText
) {
}
