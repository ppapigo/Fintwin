package com.fintwin.fintwin.ai.openai.config;

import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Set;

public final class OpenAiEndpointPolicy {
    private static final Set<String> TEST_LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public URI validate(URI baseUrl, Environment environment) {
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || baseUrl.getPath() != null && !baseUrl.getPath().isBlank() && !"/".equals(baseUrl.getPath())) {
            throw configurationInvalid();
        }
        boolean testProfile = environment.acceptsProfiles(Profiles.of("test"));
        boolean loopbackTestHttp = testProfile && "http".equalsIgnoreCase(baseUrl.getScheme())
                && TEST_LOOPBACK_HOSTS.contains(baseUrl.getHost().toLowerCase());
        if (!"https".equalsIgnoreCase(baseUrl.getScheme()) && !loopbackTestHttp) {
            throw configurationInvalid();
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !"api.openai.com".equalsIgnoreCase(baseUrl.getHost())) {
            throw configurationInvalid();
        }
        return baseUrl;
    }

    private AiAdapterException configurationInvalid() {
        return new AiAdapterException(AiErrorCode.AI_CONFIGURATION_INVALID);
    }
}
