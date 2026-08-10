package com.fintwin.fintwin.ai.openai.error;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public final class AiAdapterException extends RuntimeException {
    private final AiErrorCode code;

    public AiAdapterException(AiErrorCode code) {
        super(safeMessage(code));
        this.code = Objects.requireNonNull(code);
    }

    public AiAdapterException(AiErrorCode code, Throwable cause) {
        super(safeMessage(code), cause);
        this.code = Objects.requireNonNull(code);
    }

    public AiErrorCode code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return switch (code) {
            case AI_PRIVACY_GUARD_REJECTED -> HttpStatus.BAD_REQUEST;
            case AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AI_DISABLED, AI_CONFIGURATION_INVALID, AI_RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_AUTHENTICATION_FAILED, AI_PROVIDER_UNAVAILABLE, AI_REFUSED, AI_INCOMPLETE_RESPONSE,
                    AI_EMPTY_RESPONSE, AI_RESPONSE_TOO_LARGE, AI_SCHEMA_VIOLATION, AI_UNKNOWN_ERROR ->
                    HttpStatus.BAD_GATEWAY;
        };
    }

    private static String safeMessage(AiErrorCode code) {
        return switch (code) {
            case AI_DISABLED -> "External AI scenario extraction is disabled";
            case AI_CONFIGURATION_INVALID -> "External AI configuration is invalid";
            case AI_TIMEOUT -> "External AI request timed out";
            case AI_AUTHENTICATION_FAILED -> "External AI authentication failed";
            case AI_RATE_LIMITED -> "External AI rate limit was reached";
            case AI_PROVIDER_UNAVAILABLE -> "External AI provider is unavailable";
            case AI_REFUSED -> "External AI refused the scenario extraction request";
            case AI_INCOMPLETE_RESPONSE -> "External AI returned an incomplete response";
            case AI_EMPTY_RESPONSE -> "External AI returned no structured output";
            case AI_RESPONSE_TOO_LARGE -> "External AI response exceeded the allowed size";
            case AI_SCHEMA_VIOLATION -> "External AI response did not match the required schema";
            case AI_PRIVACY_GUARD_REJECTED -> "Scenario was rejected by the privacy boundary";
            case AI_UNKNOWN_ERROR -> "External AI request failed";
        };
    }
}
