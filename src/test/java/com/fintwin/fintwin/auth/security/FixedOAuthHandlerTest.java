package com.fintwin.fintwin.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class FixedOAuthHandlerTest {
    @Test
    void successRedirectIgnoresUntrustedReturnUrlAndContainsNoIdentifiersOrTokens() throws Exception {
        var request = new MockHttpServletRequest();
        request.setParameter("redirect", "https://attacker.example/steal");
        request.setParameter("returnUrl", "https://attacker.example/steal");
        var response = new MockHttpServletResponse();
        var handler = new FixedOAuthAuthenticationSuccessHandler(
                "http://localhost:5173/auth/callback?status=success");

        handler.onAuthenticationSuccess(request, response,
                FinTwinSecurityTestSupport.authentication(42L));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/auth/callback?status=success")
                .doesNotContain("attacker", "42", "token", "session");
    }

    @Test
    void failureRedirectDoesNotExposeProviderErrorOrSensitiveValues() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var handler = new FixedOAuthAuthenticationFailureHandler(
                "http://localhost:5173/auth/callback?status=failed&code=OAUTH_LOGIN_FAILED");

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("provider subject secret-token raw-response"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/auth/callback?status=failed&code=OAUTH_LOGIN_FAILED")
                .doesNotContain("subject", "secret-token", "raw-response");
    }
}
