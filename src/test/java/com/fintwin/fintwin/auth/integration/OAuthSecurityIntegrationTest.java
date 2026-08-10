package com.fintwin.fintwin.auth.integration;

import com.fintwin.fintwin.auth.security.FinTwinSecurityTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "fintwin.oauth.enabled=true",
        "fintwin.oauth.frontend-base-url=http://localhost:5173",
        "fintwin.oauth.google.client-id=test-google-client",
        "fintwin.oauth.google.client-secret=test-google-secret",
        "fintwin.oauth.kakao.client-id=test-kakao-client",
        "fintwin.oauth.kakao.client-secret=test-kakao-secret",
        "fintwin.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void googleAndKakaoAuthorizationEndpointsUseConfiguredProviderUrisWithoutCallingThem() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=openid")));

        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("https://kauth.kakao.com/oauth/authorize")));
    }

    @Test
    void csrfEndpointIsPublicAndMeReturnsOnlyProviderForStrongPrincipal() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(get("/api/auth/me").with(FinTwinSecurityTestSupport.fintwinUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.providerSubject").doesNotExist());
    }

    @Test
    void unauthenticatedUnexpectedAndForgedUserIdentityAreRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").with(user("unexpected-principal")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/financial-profiles/current")
                        .header("X-User-Id", "1")
                        .param("userId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsOnlyConfiguredCredentialedOriginAndCsrfHeader() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("X-CSRF-TOKEN")));

        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void providerCancellationUsesFixedFailureRedirectWithoutProviderDetails() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String location = mockMvc.perform(get("/oauth2/authorization/google").session(session))
                .andReturn().getResponse().getRedirectedUrl();
        String state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");

        mockMvc.perform(get("/login/oauth2/code/google")
                        .session(session)
                        .param("error", "access_denied")
                        .param("error_description", "provider raw private detail")
                        .param("state", state))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "http://localhost:5173/auth/callback?status=failed&code=OAUTH_LOGIN_FAILED"))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }

    @Test
    void logoutRequiresCsrfInvalidatesSessionAndDeletesCookie() throws Exception {
        MockHttpSession session = authenticatedSession(31L);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/financial-profiles/current"))
                .andExpect(status().isForbidden());
    }

    @Test
    void oauthLoginFilterUsesSessionFixationProtectionStrategy() {
        OAuth2LoginAuthenticationFilter filter = securityFilterChain.getFilters().stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();
        Object strategy = ReflectionTestUtils.getField(filter, "sessionStrategy");

        assertThat(strategy).isInstanceOf(CompositeSessionAuthenticationStrategy.class);
        Object delegates = ReflectionTestUtils.getField(strategy, "delegateStrategies");
        assertThat((java.util.List<?>) delegates)
                .anyMatch(SessionFixationProtectionStrategy.class::isInstance);
    }

    private MockHttpSession authenticatedSession(Long userId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(FinTwinSecurityTestSupport.authentication(userId));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
