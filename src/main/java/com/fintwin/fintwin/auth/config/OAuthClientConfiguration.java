package com.fintwin.fintwin.auth.config;

import com.fintwin.fintwin.auth.oauth.FinTwinOAuth2UserService;
import com.fintwin.fintwin.auth.oauth.FinTwinOidcUserService;
import com.fintwin.fintwin.auth.oauth.GoogleOAuthAttributesMapper;
import com.fintwin.fintwin.auth.oauth.KakaoOAuthAttributesMapper;
import com.fintwin.fintwin.auth.security.FixedOAuthAuthenticationFailureHandler;
import com.fintwin.fintwin.auth.security.FixedOAuthAuthenticationSuccessHandler;
import com.fintwin.fintwin.auth.security.TransientOAuth2AuthorizedClientRepository;
import com.fintwin.fintwin.user.service.OAuthIdentityProvisioningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthClientConfiguration {
    @Bean
    public TransientOAuth2AuthorizedClientRepository transientOAuth2AuthorizedClientRepository() {
        return new TransientOAuth2AuthorizedClientRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.oauth", name = "enabled", havingValue = "true")
    public ClientRegistrationRepository clientRegistrationRepository(OAuthProperties properties,
                                                                     Environment environment) {
        new OAuthEndpointPolicy().validateFrontendBaseUrl(properties.getFrontendBaseUrl(), environment);
        return new InMemoryClientRegistrationRepository(google(properties), kakao(properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.oauth", name = "enabled", havingValue = "true")
    public FinTwinOAuth2UserService finTwinOAuth2UserService(OAuthIdentityProvisioningService provisioningService) {
        return new FinTwinOAuth2UserService(new DefaultOAuth2UserService(), new KakaoOAuthAttributesMapper(),
                provisioningService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.oauth", name = "enabled", havingValue = "true")
    public FinTwinOidcUserService finTwinOidcUserService(OAuthIdentityProvisioningService provisioningService) {
        return new FinTwinOidcUserService(new OidcUserService(), new GoogleOAuthAttributesMapper(),
                provisioningService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.oauth", name = "enabled", havingValue = "true")
    public FixedOAuthAuthenticationSuccessHandler oauthSuccessHandler(OAuthProperties properties,
                                                                      Environment environment) {
        URI base = new OAuthEndpointPolicy().validateFrontendBaseUrl(properties.getFrontendBaseUrl(), environment);
        return new FixedOAuthAuthenticationSuccessHandler(callback(base, "success", null));
    }

    @Bean
    @ConditionalOnProperty(prefix = "fintwin.oauth", name = "enabled", havingValue = "true")
    public FixedOAuthAuthenticationFailureHandler oauthFailureHandler(OAuthProperties properties,
                                                                      Environment environment) {
        URI base = new OAuthEndpointPolicy().validateFrontendBaseUrl(properties.getFrontendBaseUrl(), environment);
        return new FixedOAuthAuthenticationFailureHandler(callback(base, "failed", "OAUTH_LOGIN_FAILED"));
    }

    private ClientRegistration google(OAuthProperties properties) {
        return ClientRegistration.withRegistrationId("google")
                .clientId(properties.getGoogle().getClientId())
                .clientSecret(properties.getGoogle().getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }

    private ClientRegistration kakao(OAuthProperties properties) {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(properties.getKakao().getClientId())
                .clientSecret(properties.getKakao().getClientSecret())
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

    private String callback(URI base, String status, String code) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(base)
                .replacePath("/auth/callback")
                .queryParam("status", status);
        if (code != null) {
            builder.queryParam("code", code);
        }
        return builder.build(true).toUriString();
    }
}
