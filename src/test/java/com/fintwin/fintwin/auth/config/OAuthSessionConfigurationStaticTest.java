package com.fintwin.fintwin.auth.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSessionConfigurationStaticTest {
    @Test
    void csrfSessionFixationAndLogoutProtectionsRemainExplicit() throws Exception {
        String source = Files.readString(Path.of("src", "main", "java", "com", "fintwin", "fintwin",
                "global", "config", "SecurityConfig.java"));

        assertThat(source).contains(".csrf(", ".sessionFixation(", ".migrateSession()",
                        ".invalidateHttpSession(true)", ".clearAuthentication(true)",
                        ".deleteCookies(\"JSESSIONID\")")
                .doesNotContain("csrf.disable", "allowedOriginPatterns", "setAllowedOrigins(List.of(\"*\"))");
    }

    @Test
    void cookieProfilesEnforceHttpOnlySameSiteAndProductionSecure() throws Exception {
        String common = Files.readString(Path.of("src", "main", "resources", "application.yaml"));
        String local = Files.readString(Path.of("src", "main", "resources", "application-local.yaml"));
        String test = Files.readString(Path.of("src", "main", "resources", "application-test.yaml"));
        String prod = Files.readString(Path.of("src", "main", "resources", "application-prod.yaml"));

        assertThat(common).contains("timeout: 30m", "http-only: true", "path: /", "same-site: lax");
        assertThat(local).contains("secure: false");
        assertThat(test).contains("secure: false");
        assertThat(prod).contains("secure: true", "forward-headers-strategy: native");
    }
}
