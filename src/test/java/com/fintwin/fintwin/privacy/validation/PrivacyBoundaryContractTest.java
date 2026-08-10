package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.domain.ReferenceType;
import com.fintwin.fintwin.privacy.dto.PrivacyPayloadPreviewResponse;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyBoundaryContractTest {
    private final PersonalIdentifierDetector detector = new PersonalIdentifierDetector();
    private final FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
    private final OutboundPayloadGuard guard = new OutboundPayloadGuard(detector, tokenizer);

    @Test
    void outboundRequestHasExactlyTheAllowlistedRecordComponents() {
        assertThat(Arrays.stream(ExternalAiScenarioRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList()).containsExactlyInAnyOrder(
                "schemaVersion", "purpose", "locale", "currentYearMonth", "sanitizedScenarioText",
                "supportedEventTypes", "supportedReferenceTypes", "outputContractVersion");
        assertThat(Arrays.stream(ExternalAiScenarioRequest.class.getRecordComponents())
                .map(component -> component.getType()).toList())
                .noneMatch(type -> Map.class.isAssignableFrom(type) || type == FinancialProfile.class
                        || type == FinancialPatternReport.class || type == SimulationSummary.class
                        || type == FinancialReferenceVault.class);
    }

    @Test
    void externalGatewayAcceptsOnlyPrivacySafeRequest() throws Exception {
        var method = ExternalAiGateway.class.getDeclaredMethod("extractScenarioEvents",
                ExternalAiScenarioRequest.class);

        assertThat(method.getParameterTypes()).containsExactly(ExternalAiScenarioRequest.class);
        assertThat(method.getReturnType()).isEqualTo(ExternalAiScenarioDraft.class);
        assertThat(ExternalAiGateway.class.getDeclaredMethods()).hasSize(1);
    }

    @Test
    void guardAcceptsOnlyStronglyTypedAllowlistedPayload() {
        ExternalAiScenarioRequest request = safeRequest("내년에 [MONEY_1]짜리 자동차를 사면?");

        guard.validate(request);

        assertThatThrownBy(() -> guard.validate(new UnsafePayload("1", 42L)))
                .hasMessage("Outbound AI payload type is not allowed");
    }

    @Test
    void guardRejectsUntokenizedFinancialValueAndPii() {
        assertThatThrownBy(() -> guard.validate(safeRequest("내년에 3천만원짜리 자동차를 사면?")))
                .hasMessage("Outbound AI payload contains an untokenized financial value");
        assertThatThrownBy(() -> guard.validate(safeRequest("연락처는 010-1234-5678")))
                .hasMessage("Outbound AI payload contains a blocked identifier type");
    }

    @Test
    void requestAndVaultToStringNeverContainExactMoney() {
        var tokenized = tokenizer.tokenize("3천만원");
        ExternalAiScenarioRequest request = safeRequest(tokenized.sanitizedText());

        assertThat(request.toString()).doesNotContain("30000000", "3천만원");
        assertThat(tokenized.referenceVault().toString()).doesNotContain("30000000", "3천만원");
        assertThat(tokenized.referenceVault().toString()).contains("referenceCount=1");
    }

    @Test
    void vaultHasNoWholeMapAccessorOrStaticStorage() {
        assertThat(Arrays.stream(FinancialReferenceVault.class.getDeclaredMethods())
                .map(method -> method.getReturnType()).toList())
                .noneMatch(Map.class::isAssignableFrom);
        assertThat(Arrays.stream(FinancialReferenceVault.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers())).toList()).isEmpty();
    }

    @Test
    void previewResponseCannotExposeVaultOrOriginalTextField() {
        assertThat(Arrays.stream(PrivacyPayloadPreviewResponse.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("status", "externalPayload", "references",
                        "blockedIdentifierTypes", "privacyNotice")
                .doesNotContain("referenceVault", "scenarioText", "originalText");
    }

    @Test
    void strictServiceConstructionDoesNotRequireGateway() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);

        PrivacyBoundaryService service = new PrivacyBoundaryService(detector, tokenizer, guard, fixedClock);

        assertThat(service.createPreview("내년에 3천만원짜리 자동차를 사면?").externalRequest())
                .isNotNull();
    }

    private ExternalAiScenarioRequest safeRequest(String sanitizedText) {
        return new ExternalAiScenarioRequest("1", "SCENARIO_EVENT_EXTRACTION", "ko-KR",
                YearMonth.of(2026, 8), sanitizedText, List.of(FinancialEventType.values()),
                List.of(ReferenceType.values()), "1");
    }

    private record UnsafePayload(String schemaVersion, Long userId) {
    }
}
