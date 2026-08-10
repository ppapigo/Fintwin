package com.fintwin.fintwin.auth.security;

import com.fintwin.fintwin.auth.SecurityContextCurrentUserIdProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentUserIdProviderTest {
    private final SecurityContextCurrentUserIdProvider provider = new SecurityContextCurrentUserIdProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyInternalUserIdFromStrongPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(FinTwinSecurityTestSupport.authentication(73L));

        assertThat(provider.currentUserId()).isEqualTo(73L);
    }

    @Test
    void missingOrUnexpectedPrincipalHasNoTemporaryFallback() {
        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("forged", "ignored", java.util.List.of()));

        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
