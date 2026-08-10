package com.fintwin.fintwin.auth.security;

import com.fintwin.fintwin.user.domain.OAuthProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class FinTwinSecurityTestSupport {
    private FinTwinSecurityTestSupport() {
    }

    public static Authentication authentication(Long userId) {
        return authentication(userId, OAuthProvider.GOOGLE);
    }

    public static Authentication authentication(Long userId, OAuthProvider provider) {
        FinTwinPrincipal principal = new FinTwinPrincipal(userId, provider,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(),
                provider.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static RequestPostProcessor fintwinUser(Long userId) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(authentication(userId));
    }

    public static RequestPostProcessor fintwinUser(Long userId, OAuthProvider provider) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(authentication(userId, provider));
    }
}
