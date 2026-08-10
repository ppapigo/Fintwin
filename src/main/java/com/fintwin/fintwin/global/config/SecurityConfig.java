package com.fintwin.fintwin.global.config;

import com.fintwin.fintwin.auth.config.OAuthProperties;
import com.fintwin.fintwin.auth.oauth.FinTwinOAuth2UserService;
import com.fintwin.fintwin.auth.oauth.FinTwinOidcUserService;
import com.fintwin.fintwin.auth.security.FixedOAuthAuthenticationFailureHandler;
import com.fintwin.fintwin.auth.security.FixedOAuthAuthenticationSuccessHandler;
import com.fintwin.fintwin.auth.security.TransientOAuth2AuthorizedClientRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, OAuthProperties.class})
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuthProperties oauthProperties,
            ObjectProvider<FinTwinOAuth2UserService> oauth2UserService,
            ObjectProvider<FinTwinOidcUserService> oidcUserService,
            ObjectProvider<FixedOAuthAuthenticationSuccessHandler> successHandler,
            ObjectProvider<FixedOAuthAuthenticationFailureHandler> failureHandler,
            TransientOAuth2AuthorizedClientRepository authorizedClientRepository) throws Exception {
        http
                .cors(cors -> { })
                .csrf(csrf -> { })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new Http403ForbiddenEntryPoint(), PathPatternRequestMatcher.pathPattern("/api/**")))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

        if (oauthProperties.isEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizedClientRepository(authorizedClientRepository)
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(oauth2UserService.getObject())
                            .oidcUserService(oidcUserService.getObject()))
                    .successHandler(successHandler.getObject())
                    .failureHandler(failureHandler.getObject()));
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
