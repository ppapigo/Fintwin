package com.fintwin.fintwin.auth.security;

import com.fintwin.fintwin.user.domain.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FinTwinPrincipal implements OAuth2User, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final OAuthProvider provider;
    private final List<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public FinTwinPrincipal(Long userId, OAuthProvider provider,
                            Collection<? extends GrantedAuthority> authorities) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authenticated internal user ID is invalid");
        }
        this.userId = userId;
        this.provider = Objects.requireNonNull(provider);
        this.authorities = List.copyOf(authorities);
        this.attributes = Map.of("provider", provider.name());
    }

    public Long internalUserId() {
        return userId;
    }

    public OAuthProvider provider() {
        return provider;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return "fintwin-authenticated-user";
    }

    @Override
    public String toString() {
        return "FinTwinPrincipal[authenticated=true, provider=" + provider + "]";
    }
}
