package com.fintwin.fintwin.auth;

import com.fintwin.fintwin.auth.security.FinTwinPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SecurityContextCurrentUserIdProvider implements CurrentUserIdProvider {
    @Override
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FinTwinPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated FinTwin principal is required");
        }
        return principal.internalUserId();
    }
}
