package com.fintwin.fintwin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fintwin-prod-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fintwin.oauth.enabled=false",
        "fintwin.ai.enabled=false"
})
@ActiveProfiles("prod")
class ProductionProfileContextTest {
    @Autowired
    private Environment environment;

    @Test
    void prodProfileContextLoadsWithSecureOperationalDefaults() {
        assertThat(environment.getActiveProfiles()).contains("prod");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }
}
