package com.fintwin.fintwin.auth.security;

import com.fintwin.fintwin.user.domain.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class FinTwinOidcPrincipal extends FinTwinPrincipal implements OidcUser {
    @Serial
    private static final long serialVersionUID = 1L;

    private final OidcIdToken idToken;

    public FinTwinOidcPrincipal(Long userId, OAuthProvider provider,
                                Collection<? extends GrantedAuthority> authorities,
                                OidcIdToken idToken) {
        super(userId, provider, authorities);
        this.idToken = Objects.requireNonNull(idToken);
    }

    @Override
    public Map<String, Object> getClaims() {
        return getAttributes();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return null;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }
}
