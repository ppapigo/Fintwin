package com.fintwin.fintwin.auth.controller;

import com.fintwin.fintwin.auth.dto.AuthenticationStatusResponse;
import com.fintwin.fintwin.auth.dto.CsrfTokenResponse;
import com.fintwin.fintwin.auth.security.FinTwinPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    @GetMapping("/me")
    public AuthenticationStatusResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FinTwinPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated FinTwin principal is required");
        }
        return new AuthenticationStatusResponse(true, principal.provider());
    }
}
