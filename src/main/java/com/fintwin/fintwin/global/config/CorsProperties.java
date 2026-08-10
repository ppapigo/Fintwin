package com.fintwin.fintwin.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;

import java.net.URI;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "fintwin.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    @AssertTrue(message = "CORS origins must be explicit HTTP(S) origins without wildcard, path, query, or fragment")
    public boolean isOriginConfigurationValid() {
        return !allowedOrigins.isEmpty() && allowedOrigins.stream().allMatch(this::isValidOrigin);
    }

    private boolean isValidOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            return !value.contains("*") && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && (path == null || path.isBlank() || "/".equals(path));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
