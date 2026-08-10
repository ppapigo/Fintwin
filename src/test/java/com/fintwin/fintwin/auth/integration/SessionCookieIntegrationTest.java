package com.fintwin.fintwin.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieIntegrationTest {
    @LocalServerPort
    private int port;

    @Test
    void csrfSessionCookieIsHttpOnlyPathRootAndSameSiteLax() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/auth/csrf"))
                .GET()
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie")).hasValueSatisfying(cookie ->
                assertThat(cookie).contains("JSESSIONID=", "Path=/", "HttpOnly", "SameSite=Lax")
                        .doesNotContain("Secure"));
    }
}
