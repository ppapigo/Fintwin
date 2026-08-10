package com.fintwin.fintwin.user.repository;

import com.fintwin.fintwin.user.domain.OAuthIdentity;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {
    Optional<OAuthIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
