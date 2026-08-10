package com.fintwin.fintwin.ai.openai.config;

import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiConfiguration.class);

    @Test
    void disabledByDefaultStartsWithoutApiKeyAndDoesNotCreateGateway() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            OpenAiProperties properties = context.getBean(OpenAiProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getModel()).isEqualTo("gpt-5.6-luna");
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(context).doesNotHaveBean(ExternalAiGateway.class);
        });
    }

    @Test
    void enabledWithoutApiKeyFailsConfigurationBinding() {
        contextRunner.withPropertyValues("fintwin.ai.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(allCauseMessages(context.getStartupFailure()))
                            .contains("OPENAI_API_KEY is required");
                });
    }

    @Test
    void propertiesNeverExposeApiKeyFromToString() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("sk-sensitive-test-value");

        assertThat(properties.toString()).contains("[REDACTED]")
                .doesNotContain("sk-sensitive-test-value");
    }

    @Test
    void endpointPolicyRequiresHttpsAndRestrictsProductionHost() {
        OpenAiEndpointPolicy policy = new OpenAiEndpointPolicy();
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(policy.validate(URI.create("https://api.openai.com"), local))
                .isEqualTo(URI.create("https://api.openai.com"));
        assertThat(policy.validate(URI.create("http://127.0.0.1:8080"), test))
                .isEqualTo(URI.create("http://127.0.0.1:8080"));
        assertThatThrownBy(() -> policy.validate(URI.create("http://api.openai.com"), local))
                .isInstanceOf(com.fintwin.fintwin.ai.openai.error.AiAdapterException.class);
        assertThatThrownBy(() -> policy.validate(URI.create("https://example.com"), prod))
                .isInstanceOf(com.fintwin.fintwin.ai.openai.error.AiAdapterException.class);
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
