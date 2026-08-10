package com.fintwin.fintwin.agent.domain;

import com.fintwin.fintwin.agent.dto.AgentExecutionRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStaticArchitectureTest {
    private static final List<String> FORBIDDEN_SOURCE_FRAGMENTS = List.of(
            "ExternalAiGateway", "FinancialReferenceVault", "java.net.", "HttpClient", "WebClient",
            "RestClient", "RestTemplate", "java.lang.reflect", "Class.forName", "ScriptEngine",
            " Repository", " Entity", "Logger", "System.out", "simulation.engine", "goal.solver");
    private static final Pattern FREE_PAYLOAD_TYPES = Pattern.compile(
            "\\bObject\\b|\\bJsonNode\\b|Map\\s*<\\s*String\\s*,");
    private static final Pattern FLOATING_FINANCE_TYPES = Pattern.compile("\\bdouble\\b|\\bfloat\\b");

    @Test
    void commandAndApiRequestCannotCarryUserOrProfileIdentifiers() {
        assertThat(AgentCommand.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("intent", "startYearMonth", "horizonMonths", "assumptions", "events",
                        "goalType", "targetAmount")
                .doesNotContain("userId", "profileId", "profileVersion");
        assertThat(AgentExecutionRequest.class.getRecordComponents()).extracting(component -> component.getName())
                .doesNotContain("userId", "profileId", "profileVersion", "toolName", "url");
    }

    @Test
    void agentPackageContainsNoDynamicExecutionExternalAiPersistenceOrFreePayload() throws IOException {
        Path agentRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "fintwin",
                "fintwin", "agent");
        List<Path> javaFiles;
        try (var paths = Files.walk(agentRoot)) {
            javaFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertThat(javaFiles).isNotEmpty();
        for (Path path : javaFiles) {
            String source = Files.readString(path);
            assertThat(FORBIDDEN_SOURCE_FRAGMENTS).allSatisfy(fragment ->
                    assertThat(source).as("%s must not contain %s", path, fragment).doesNotContain(fragment));
            assertThat(FREE_PAYLOAD_TYPES.matcher(source).find()).as("free payload in %s", path).isFalse();
            assertThat(FLOATING_FINANCE_TYPES.matcher(source).find()).as("floating type in %s", path).isFalse();
        }
    }

    @Test
    void traceShapeCannotCarrySensitiveValues() {
        assertThat(AgentTraceStep.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("sequence", "state", "component", "outcomeCode")
                .doesNotContain("amount", "userId", "profileId", "transaction", "vault", "details");
    }
}
