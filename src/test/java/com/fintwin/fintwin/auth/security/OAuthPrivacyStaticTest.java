package com.fintwin.fintwin.auth.security;

import com.fintwin.fintwin.auth.dto.AuthenticationStatusResponse;
import com.fintwin.fintwin.user.domain.OAuthIdentity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthPrivacyStaticTest {
    @Test
    void persistedIdentityContainsNoProviderTokenEmailOrResponsePayloadFields() {
        assertThat(Arrays.stream(OAuthIdentity.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .contains("provider", "providerSubject", "createdAt", "lastLoginAt")
                .doesNotContain("accessToken", "refreshToken", "idToken", "email", "attributes",
                        "providerResponse", "rawResponse");
    }

    @Test
    void publicAuthenticationResponseCannotExposeInternalOrProviderIdentifiers() {
        assertThat(AuthenticationStatusResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("authenticated", "provider")
                .doesNotContain("userId", "providerSubject", "email", "accessToken", "sessionId");
    }

    @Test
    void principalKeepsOnlyProviderAsPublicOAuthAttribute() {
        FinTwinPrincipal principal = (FinTwinPrincipal) FinTwinSecurityTestSupport.authentication(11L)
                .getPrincipal();

        assertThat(principal.getAttributes()).containsOnlyKeys("provider");
        assertThat(principal.toString()).doesNotContain("11");
    }
}
