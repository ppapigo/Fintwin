package com.fintwin.fintwin.global.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDeploymentConfigurationTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void productionComposeKeepsBackendAndDatabaseOffHostPorts() throws IOException {
        String compose = read("compose.prod.yaml");

        assertThat(compose)
                .contains("internal: true", "condition: service_healthy", "mysql_data:")
                .contains("${MYSQL_PASSWORD:?", "${MYSQL_ROOT_PASSWORD:?", "${GOOGLE_CLIENT_SECRET:?")
                .doesNotContain("container_name:", "8080:8080", "3306:3306");
    }

    @Test
    void imagesAreMultiStageAndDoNotBakeSecretsIntoFrontend() throws IOException {
        String backend = read("Dockerfile");
        String frontend = read("frontend/Dockerfile");

        assertThat(backend)
                .contains("AS build", "AS runtime", "USER fintwin", "SPRING_PROFILES_ACTIVE=prod")
                .doesNotContain("DB_PASSWORD", "OPENAI_API_KEY", "GOOGLE_CLIENT_SECRET");
        assertThat(frontend)
                .contains("npm ci --ignore-scripts", "npm run build", "FROM nginx:")
                .doesNotContain("VITE_API_BASE_URL", "OPENAI_API_KEY", "GOOGLE_CLIENT_SECRET");
    }

    @Test
    void nginxHasSpaProxyUploadAndSecurityBoundaries() throws IOException {
        String routes = read("frontend/nginx/snippets/proxy-routes.conf");
        String proxySettings = read("frontend/nginx/snippets/proxy-settings.conf");
        String http = read("frontend/nginx/templates/default-http.conf.template");
        String https = read("frontend/nginx/templates/default-https.conf.template");
        String main = read("frontend/nginx/nginx.conf");

        assertThat(routes).contains("/api/", "/oauth2/", "/login/", "/logout", "/actuator/")
                .contains("Cache-Control \"no-store\"")
                .doesNotContainIgnoringCase("websocket", "upgrade");
        assertThat(proxySettings).contains("proxy_intercept_errors off", "X-Forwarded-Proto")
                .doesNotContainIgnoringCase("websocket", "upgrade");
        assertThat(http).contains("try_files $uri $uri/ /index.html");
        assertThat(https).contains("return 301 https://$host$request_uri", "ssl_certificate")
                .contains("try_files $uri $uri/ /index.html");
        assertThat(main).contains("server_tokens off", "client_max_body_size 3m");
    }

    @Test
    void productionProfileUsesValidationAndSafeErrorSettings() throws IOException {
        String config = read("src/main/resources/application-prod.yaml");

        assertThat(config)
                .contains("ddl-auto: validate", "open-in-view: false", "show-sql: false")
                .contains("include-stacktrace: never", "include-message: never")
                .contains("forward-headers-strategy: native", "secure: true", "shutdown: graceful")
                .doesNotContain("ddl-auto: update", "ddl-auto: create");
    }

    @Test
    void initialSchemaMatchesPersistedEntitiesWithoutDestructiveStatements() throws IOException {
        String schema = read("deploy/mysql/schema_init_mysql8.sql");

        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS users", "CREATE TABLE IF NOT EXISTS oauth_identities")
                .contains("CREATE TABLE IF NOT EXISTS financial_profiles")
                .contains("DECIMAL(19,2)", "DECIMAL(7,4)", "utf8mb4_0900_bin")
                .contains("uk_financial_profiles_user_version", "idx_oauth_identity_user")
                .doesNotContainIgnoringCase("DROP ", "TRUNCATE ", "DELETE ");
    }

    @Test
    void productionEnvironmentExampleContainsOnlyBlankAssignments() throws IOException {
        List<String> assignments = Files.readAllLines(ROOT.resolve(".env.prod.example"), StandardCharsets.UTF_8)
                .stream()
                .map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();

        assertThat(assignments).isNotEmpty().allMatch(line -> line.matches("[A-Z0-9_]+="));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
