package com.fintwin.fintwin.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public final class OAuthAttributeMappingException extends OAuth2AuthenticationException {
    public OAuthAttributeMappingException(String errorCode) {
        super(new OAuth2Error(errorCode), "OAuth provider identity is invalid");
    }
}
