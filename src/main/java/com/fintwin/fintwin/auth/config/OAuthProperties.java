package com.fintwin.fintwin.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "fintwin.oauth")
public class OAuthProperties {
    private boolean enabled;
    @NotNull
    private URI frontendBaseUrl = URI.create("http://localhost:5173");
    private final ClientCredentials google = new ClientCredentials();
    private final ClientCredentials kakao = new ClientCredentials();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(URI frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public ClientCredentials getGoogle() {
        return google;
    }

    public ClientCredentials getKakao() {
        return kakao;
    }

    @AssertTrue(message = "Google and Kakao client credentials are required when FINTWIN_OAUTH_ENABLED is true")
    public boolean isEnabledConfigurationValid() {
        return !enabled || google.isComplete() && kakao.isComplete();
    }

    @AssertTrue(message = "FRONTEND_BASE_URL must be an absolute HTTP(S) origin without path, query, or fragment")
    public boolean isFrontendBaseUrlValid() {
        if (frontendBaseUrl == null || frontendBaseUrl.getHost() == null || frontendBaseUrl.getUserInfo() != null
                || frontendBaseUrl.getQuery() != null || frontendBaseUrl.getFragment() != null) {
            return false;
        }
        String scheme = frontendBaseUrl.getScheme();
        String path = frontendBaseUrl.getPath();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && (path == null || path.isBlank() || "/".equals(path));
    }

    @Override
    public String toString() {
        return "OAuthProperties[enabled=" + enabled + ", frontendBaseUrl=" + frontendBaseUrl
                + ", google=" + google + ", kakao=" + kakao + "]";
    }

    public static final class ClientCredentials {
        private String clientId = "";
        private String clientSecret = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        boolean isComplete() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "ClientCredentials[clientId=[REDACTED], clientSecret=[REDACTED]]";
        }
    }
}
