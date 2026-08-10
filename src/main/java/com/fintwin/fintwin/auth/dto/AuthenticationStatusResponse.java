package com.fintwin.fintwin.auth.dto;

import com.fintwin.fintwin.user.domain.OAuthProvider;

public record AuthenticationStatusResponse(boolean authenticated, OAuthProvider provider) {
}
