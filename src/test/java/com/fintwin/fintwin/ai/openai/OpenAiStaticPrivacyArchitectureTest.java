package com.fintwin.fintwin.ai.openai;

import com.fintwin.fintwin.ai.openai.dto.OpenAiResponsesRequest;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStaticPrivacyArchitectureTest {
    private static final List<String> FORBIDDEN_PROJECT_IMPORTS = List.of(
            ".financialprofile.", ".transaction.", ".pattern.", ".simulation.", ".goal.",
            ".repository.", "jakarta.persistence");
    private static final List<String> FORBIDDEN_TECHNOLOGY_FRAGMENTS = List.of(
            "com.openai", "springframework.ai", "WebClient", "RestTemplate", "Logger", "System.out");
    private static final Pattern FREE_FORM_JSON = Pattern.compile(
            "\\bJsonNode\\b|Map\\s*<\\s*String\\s*,\\s*Object\\s*>");
    private static final Pattern FLOATING_POINT_TYPES = Pattern.compile("\\bdouble\\b|\\bfloat\\b");

    @Test
    void externalGatewayContractAcceptsOnlyThePrivacySafeRequest() throws NoSuchMethodException {
        var method = ExternalAiGateway.class.getMethod("extractScenarioEvents", ExternalAiScenarioRequest.class);

        assertThat(method.getParameterTypes()).containsExactly(ExternalAiScenarioRequest.class);
        assertThat(ExternalAiScenarioRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("schemaVersion", "purpose", "locale", "currentYearMonth",
                        "sanitizedScenarioText", "supportedEventTypes", "supportedReferenceTypes",
                        "outputContractVersion")
                .doesNotContain("userId", "profileId", "financialProfile", "transactions", "patterns",
                        "simulationResult", "goal", "vault", "rawScenarioText");
    }

    @Test
    void providerRequestShapeCannotEnableStorageToolsOrConversationState() {
        assertThat(OpenAiResponsesRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("model", "instructions", "input", "store", "maxOutputTokens", "text")
                .doesNotContain("tools", "toolChoice", "previousResponseId", "conversation", "stream",
                        "metadata", "user");
    }

    @Test
    void openAiAdapterHasNoPersistenceEngineSdkOrFreeFormPayloadDependency() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "fintwin",
                "fintwin", "ai", "openai");
        List<Path> javaFiles;
        try (var paths = Files.walk(root)) {
            javaFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        assertThat(javaFiles).isNotEmpty();
        for (Path path : javaFiles) {
            String source = Files.readString(path);
            assertThat(FORBIDDEN_PROJECT_IMPORTS).allSatisfy(fragment ->
                    assertThat(source).as("%s must not contain %s", path, fragment).doesNotContain(fragment));
            assertThat(FORBIDDEN_TECHNOLOGY_FRAGMENTS).allSatisfy(fragment ->
                    assertThat(source).as("%s must not contain %s", path, fragment).doesNotContain(fragment));
            assertThat(FREE_FORM_JSON.matcher(source).find()).as("free-form JSON in %s", path).isFalse();
            assertThat(FLOATING_POINT_TYPES.matcher(source).find()).as("floating point type in %s", path).isFalse();
        }
    }
}
