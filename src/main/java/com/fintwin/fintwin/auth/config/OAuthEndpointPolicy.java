package com.fintwin.fintwin.auth.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Set;

public final class OAuthEndpointPolicy {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public URI validateFrontendBaseUrl(URI frontendBaseUrl, Environment environment) {
        String host = frontendBaseUrl.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean secure = "https".equalsIgnoreCase(frontendBaseUrl.getScheme());
        boolean localHttp = "http".equalsIgnoreCase(frontendBaseUrl.getScheme())
                && LOOPBACK_HOSTS.contains(host)
                && !environment.acceptsProfiles(Profiles.of("prod"));
        if (!secure && !localHttp) {
            throw new IllegalStateException("OAuth frontend URL policy is invalid");
        }
        return frontendBaseUrl;
    }
}
