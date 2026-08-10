package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.user.domain.OAuthProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthAttributeMapperTest {
    private final GoogleOAuthAttributesMapper google = new GoogleOAuthAttributesMapper();
    private final KakaoOAuthAttributesMapper kakao = new KakaoOAuthAttributesMapper();

    @Test
    void googleUsesOnlyRequiredSubAndRejectsInvalidSub() {
        MappedOAuthIdentity mapped = google.map(Map.of(
                "sub", "google-subject-1", "email", "same@example.test", "picture", "ignored"));

        assertThat(mapped.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(mapped.providerSubject()).isEqualTo("google-subject-1");
        assertThat(mapped.toString()).doesNotContain("google-subject-1", "same@example.test");
        assertThatThrownBy(() -> google.map(Map.of("email", "same@example.test")))
                .isInstanceOf(OAuthAttributeMappingException.class);
        assertThatThrownBy(() -> google.map(Map.of("sub", " ")))
                .isInstanceOf(OAuthAttributeMappingException.class);
    }

    @Test
    void kakaoUsesTopLevelIntegralIdAndIgnoresNestedProfileData() {
        MappedOAuthIdentity mapped = kakao.map(Map.of(
                "id", 9876543210L,
                "kakao_account", Map.of("email", "same@example.test",
                        "profile", Map.of("nickname", "ignored"))));

        assertThat(mapped.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(mapped.providerSubject()).isEqualTo("9876543210");
        assertThatThrownBy(() -> kakao.map(Map.of("kakao_account", Map.of("id", 1L))))
                .isInstanceOf(OAuthAttributeMappingException.class);
        assertThatThrownBy(() -> kakao.map(Map.of("id", new BigDecimal("1.5"))))
                .isInstanceOf(OAuthAttributeMappingException.class);
    }
}
