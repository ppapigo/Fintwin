package com.fintwin.fintwin.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

import static org.assertj.core.api.Assertions.assertThat;

class TransientOAuth2AuthorizedClientRepositoryTest {
    @Test
    void neverLoadsOrPersistsProviderTokens() {
        var repository = new TransientOAuth2AuthorizedClientRepository();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var authentication = FinTwinSecurityTestSupport.authentication(1L);

        repository.saveAuthorizedClient(null, authentication, request, response);
        repository.removeAuthorizedClient("google", authentication, request, response);

        OAuth2AuthorizedClient loaded = repository.loadAuthorizedClient("google", authentication, request);
        assertThat(loaded).isNull();
    }
}
