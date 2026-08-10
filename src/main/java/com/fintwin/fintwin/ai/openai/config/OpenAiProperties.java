package com.fintwin.fintwin.ai.openai.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "fintwin.ai")
public class OpenAiProperties {
    private boolean enabled;

    @NotBlank
    @Pattern(regexp = "openai", message = "provider must be openai")
    private String provider = "openai";

    @NotNull
    private URI baseUrl = URI.create("https://api.openai.com");

    private String apiKey = "";

    @NotBlank
    private String model = "gpt-5.6-luna";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(15);

    @Min(1)
    @Max(100_000)
    private int maxOutputTokens = 1_200;

    @Min(1_024)
    @Max(1_048_576)
    private int maxResponseBytes = 65_536;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    @AssertTrue(message = "OPENAI_API_KEY is required when FINTWIN_AI_ENABLED is true")
    public boolean isEnabledConfigurationValid() {
        return !enabled || apiKey != null && !apiKey.isBlank();
    }

    @AssertTrue(message = "OpenAI timeout values must be positive")
    public boolean isTimeoutConfigurationValid() {
        return connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative()
                && readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative();
    }

    @Override
    public String toString() {
        return "OpenAiProperties[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
                + ", apiKey=[REDACTED], model=" + model + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", maxOutputTokens=" + maxOutputTokens
                + ", maxResponseBytes=" + maxResponseBytes + "]";
    }
}
