package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.privacy.domain.PersonalIdentifierType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalIdentifierDetectorTest {
    private final PersonalIdentifierDetector detector = new PersonalIdentifierDetector();

    @Test
    void detectsResidentRegistrationNumberBeforeCardHeuristic() {
        assertThat(detector.detect("식별번호 900101-1234567 포함"))
                .containsExactly(PersonalIdentifierType.RESIDENT_REGISTRATION_NUMBER);
    }

    @Test
    void detectsEmail() {
        assertThat(detector.detect("연락처 synthetic.user@example.com"))
                .containsExactly(PersonalIdentifierType.EMAIL);
    }

    @Test
    void detectsKoreanPhoneNumberBeforeAccountHeuristic() {
        assertThat(detector.detect("연락처 010-1234-5678"))
                .containsExactly(PersonalIdentifierType.PHONE_NUMBER);
    }

    @Test
    void detectsCardNumber() {
        assertThat(detector.detect("카드 4111-1111-1111-1111"))
                .containsExactly(PersonalIdentifierType.CARD_NUMBER);
    }

    @Test
    void detectsAccountLikeNumber() {
        assertThat(detector.detect("계좌 123-456789-01-234"))
                .containsExactly(PersonalIdentifierType.ACCOUNT_NUMBER);
    }

    @Test
    void detectsApiKeyAndSecretPatterns() {
        assertThat(detector.detect("api_key=syntheticSecretValue12345"))
                .containsExactly(PersonalIdentifierType.API_KEY_OR_SECRET);
        assertThat(detector.detect("sk-synthetic0123456789ABCDEFG"))
                .containsExactly(PersonalIdentifierType.API_KEY_OR_SECRET);
    }

    @Test
    void detectsControlAndLongNumericSequence() {
        assertThat(detector.detect("safe\u0000text"))
                .containsExactly(PersonalIdentifierType.CONTROL_CHARACTER);
        assertThat(detector.detect("1234567890123456789012345"))
                .containsExactly(PersonalIdentifierType.LONG_NUMERIC_SEQUENCE);
    }

    @Test
    void acceptsSyntheticNarrativeWithFinancialValues() {
        assertThat(detector.detect("내년에 3천만원짜리 차를 사고 금리가 2% 오르면?"))
                .isEmpty();
    }
}
