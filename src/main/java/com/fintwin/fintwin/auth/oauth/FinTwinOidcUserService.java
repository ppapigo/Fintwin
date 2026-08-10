package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.auth.security.FinTwinOidcPrincipal;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import com.fintwin.fintwin.user.service.OAuthIdentityProvisioningService;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class FinTwinOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final GoogleOAuthAttributesMapper googleMapper;
    private final OAuthIdentityProvisioningService provisioningService;

    public FinTwinOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
                                  GoogleOAuthAttributesMapper googleMapper,
                                  OAuthIdentityProvisioningService provisioningService) {
        this.delegate = delegate;
        this.googleMapper = googleMapper;
        this.provisioningService = provisioningService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        if (!"google".equals(userRequest.getClientRegistration().getRegistrationId())) {
            throw failure("unsupported_oidc_provider");
        }
        OidcUser providerUser = delegate.loadUser(userRequest);
        MappedOAuthIdentity identity = googleMapper.map(providerUser.getClaims());
        Long userId = provision(identity);
        return new FinTwinOidcPrincipal(userId, OAuthProvider.GOOGLE,
                AuthorityUtils.createAuthorityList("ROLE_USER"), providerUser.getIdToken());
    }

    private Long provision(MappedOAuthIdentity identity) {
        try {
            return provisioningService.resolveOrCreate(identity.provider(), identity.providerSubject());
        } catch (DataAccessException | IllegalStateException exception) {
            throw failure("oauth_user_provisioning_failed");
        }
    }

    private OAuth2AuthenticationException failure(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), "OAuth login failed");
    }
}
