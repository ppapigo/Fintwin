package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.auth.security.FinTwinPrincipal;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import com.fintwin.fintwin.user.service.OAuthIdentityProvisioningService;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class FinTwinOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final KakaoOAuthAttributesMapper kakaoMapper;
    private final OAuthIdentityProvisioningService provisioningService;

    public FinTwinOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
                                    KakaoOAuthAttributesMapper kakaoMapper,
                                    OAuthIdentityProvisioningService provisioningService) {
        this.delegate = delegate;
        this.kakaoMapper = kakaoMapper;
        this.provisioningService = provisioningService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        if (!"kakao".equals(userRequest.getClientRegistration().getRegistrationId())) {
            throw failure("unsupported_oauth_provider");
        }
        OAuth2User providerUser = delegate.loadUser(userRequest);
        MappedOAuthIdentity identity = kakaoMapper.map(providerUser.getAttributes());
        Long userId = provision(identity);
        return new FinTwinPrincipal(userId, OAuthProvider.KAKAO,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
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
