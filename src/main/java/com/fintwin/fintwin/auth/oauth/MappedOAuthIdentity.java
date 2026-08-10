package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.user.domain.OAuthProvider;

import java.util.Objects;

public final class MappedOAuthIdentity {
    private final OAuthProvider provider;
    private final String providerSubject;

    public MappedOAuthIdentity(OAuthProvider provider, String providerSubject) {
        this.provider = Objects.requireNonNull(provider);
        this.providerSubject = Objects.requireNonNull(providerSubject);
    }

    public OAuthProvider provider() {
        return provider;
    }

    public String providerSubject() {
        return providerSubject;
    }

    @Override
    public String toString() {
        return "MappedOAuthIdentity[provider=" + provider + ", providerSubject=[REDACTED]]";
    }
}
