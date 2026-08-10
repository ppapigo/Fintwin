package com.fintwin.fintwin.auth.config;

import com.fintwin.fintwin.auth.oauth.FinTwinOAuth2UserService;
import com.fintwin.fintwin.user.service.OAuthIdentityProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OAuthClientConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OAuthClientConfiguration.class)
            .withBean(OAuthIdentityProvisioningService.class,
                    () -> mock(OAuthIdentityProvisioningService.class));

    @Test
    void disabledByDefaultStartsWithoutClientSecretsOrRegistrations() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OAuthProperties.class).isEnabled()).isFalse();
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            assertThat(context).doesNotHaveBean(FinTwinOAuth2UserService.class);
        });
    }

    @Test
    void enabledWithoutBothProviderCredentialsFailsConfigurationBinding() {
        contextRunner.withPropertyValues("fintwin.oauth.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(allCauseMessages(context.getStartupFailure()))
                            .contains("Google and Kakao client credentials are required");
                });
    }

    @Test
    void enabledConfigurationUsesMinimalOfficialProviderContracts() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var repository = context.getBean(InMemoryClientRegistrationRepository.class);
                    var google = repository.findByRegistrationId("google");
                    var kakao = repository.findByRegistrationId("kakao");

                    assertThat(google.getScopes()).containsExactly("openid");
                    assertThat(google.getProviderDetails().getAuthorizationUri())
                            .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
                    assertThat(google.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                            .isEqualTo("sub");
                    assertThat(kakao.getClientAuthenticationMethod())
                            .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
                    assertThat(kakao.getProviderDetails().getAuthorizationUri())
                            .isEqualTo("https://kauth.kakao.com/oauth/authorize");
                    assertThat(kakao.getProviderDetails().getTokenUri())
                            .isEqualTo("https://kauth.kakao.com/oauth/token");
                    assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUri())
                            .isEqualTo("https://kapi.kakao.com/v2/user/me");
                    assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                            .isEqualTo("id");
                    assertThat(kakao.getRedirectUri())
                            .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
                });
    }

    @Test
    void secretsAreRedactedAndProductionFrontendRequiresHttps() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGoogle().setClientId("google-client-value");
        properties.getGoogle().setClientSecret("google-secret-value");
        properties.getKakao().setClientId("kakao-client-value");
        properties.getKakao().setClientSecret("kakao-secret-value");

        assertThat(properties.toString()).contains("[REDACTED]")
                .doesNotContain("google-client-value", "google-secret-value",
                        "kakao-client-value", "kakao-secret-value");

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new OAuthEndpointPolicy()
                .validateFrontendBaseUrl(URI.create("http://localhost:5173"), prod))
                .isInstanceOf(IllegalStateException.class);
    }

    private String[] enabledProperties() {
        return new String[]{
                "fintwin.oauth.enabled=true",
                "fintwin.oauth.frontend-base-url=http://localhost:5173",
                "fintwin.oauth.google.client-id=test-google-client",
                "fintwin.oauth.google.client-secret=test-google-secret",
                "fintwin.oauth.kakao.client-id=test-kakao-client",
                "fintwin.oauth.kakao.client-secret=test-kakao-secret"
        };
    }

    private String allCauseMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}
