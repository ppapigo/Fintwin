package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.auth.security.FinTwinOidcPrincipal;
import com.fintwin.fintwin.auth.security.FinTwinPrincipal;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import com.fintwin.fintwin.user.service.OAuthIdentityProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinTwinOAuthUserServiceTest {
    @Test
    void kakaoFlowDiscardsFullProviderAttributesAndReturnsStrongPrincipal() {
        @SuppressWarnings("unchecked")
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuthIdentityProvisioningService provisioning = mock(OAuthIdentityProvisioningService.class);
        OAuth2UserRequest request = new OAuth2UserRequest(kakaoRegistration(), accessToken());
        OAuth2User providerUser = new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES,
                Map.of("id", 123456L, "kakao_account", Map.of("email", "private@example.test")), "id");
        when(delegate.loadUser(request)).thenReturn(providerUser);
        when(provisioning.resolveOrCreate(OAuthProvider.KAKAO, "123456")).thenReturn(81L);

        OAuth2User result = new FinTwinOAuth2UserService(delegate, new KakaoOAuthAttributesMapper(), provisioning)
                .loadUser(request);

        assertThat(result).isInstanceOf(FinTwinPrincipal.class);
        assertThat(((FinTwinPrincipal) result).internalUserId()).isEqualTo(81L);
        assertThat(result.getAttributes()).containsOnlyKeys("provider")
                .doesNotContainValue("private@example.test");
        verify(provisioning).resolveOrCreate(OAuthProvider.KAKAO, "123456");
    }

    @Test
    void googleOidcFlowUsesSubAndReturnsStrongPrincipalWithoutFullClaimsAsAttributes() {
        @SuppressWarnings("unchecked")
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        OAuthIdentityProvisioningService provisioning = mock(OAuthIdentityProvisioningService.class);
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(300),
                Map.of("sub", "google-subject", "email", "private@example.test"));
        OidcUserRequest request = new OidcUserRequest(googleRegistration(), accessToken(), idToken, Map.of());
        OidcUser providerUser = new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken);
        when(delegate.loadUser(request)).thenReturn(providerUser);
        when(provisioning.resolveOrCreate(OAuthProvider.GOOGLE, "google-subject")).thenReturn(91L);

        OidcUser result = new FinTwinOidcUserService(delegate, new GoogleOAuthAttributesMapper(), provisioning)
                .loadUser(request);

        assertThat(result).isInstanceOf(FinTwinOidcPrincipal.class);
        assertThat(((FinTwinPrincipal) result).internalUserId()).isEqualTo(91L);
        assertThat(result.getAttributes()).containsOnlyKeys("provider")
                .doesNotContainValue("private@example.test");
        verify(provisioning).resolveOrCreate(OAuthProvider.GOOGLE, "google-subject");
    }

    private ClientRegistration kakaoRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    private ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(Set.of("openid"))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }

    private OAuth2AccessToken accessToken() {
        Instant now = Instant.now();
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-access-token",
                now, now.plusSeconds(60));
    }
}
