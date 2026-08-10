package com.fintwin.fintwin.ai.service;

import com.fintwin.fintwin.agent.domain.AgentEvidence;
import com.fintwin.fintwin.agent.domain.AgentExecutionInput;
import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.AgentResultType;
import com.fintwin.fintwin.agent.domain.AgentState;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.MissingInformationCode;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import com.fintwin.fintwin.agent.orchestration.FinTwinAgentOrchestrator;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfRequest;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfResponse;
import com.fintwin.fintwin.ai.openai.config.OpenAiProperties;
import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.bridge.ValidatedScenarioAgentCommandFactory;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.domain.PrivacyMode;
import com.fintwin.fintwin.privacy.domain.PrivacyStatus;
import com.fintwin.fintwin.privacy.domain.ReferenceType;
import com.fintwin.fintwin.privacy.domain.ScenarioPrivacyEnvelope;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import com.fintwin.fintwin.privacy.token.FinancialTokenizationResult;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import com.fintwin.fintwin.scenario.domain.FinancialEventType;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NaturalLanguageWhatIfServiceTest {
    private final OpenAiProperties properties = enabledProperties();
    private final ExternalAiGateway gateway = mock(ExternalAiGateway.class);
    private final PrivacyBoundaryService privacyBoundaryService = mock(PrivacyBoundaryService.class);
    private final FinTwinAgentOrchestrator orchestrator = mock(FinTwinAgentOrchestrator.class);
    private final FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
    private final ExternalAiDraftValidator validator = new ExternalAiDraftValidator(
            new PersonalIdentifierDetector(), tokenizer);
    private NaturalLanguageWhatIfService service;

    @BeforeEach
    void gatewayAvailable() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("externalAiGateway", gateway);
        ObjectProvider<ExternalAiGateway> gatewayProvider = beanFactory.getBeanProvider(ExternalAiGateway.class);
        service = new NaturalLanguageWhatIfService(properties, gatewayProvider, privacyBoundaryService, validator,
                new ValidatedScenarioAgentCommandFactory(new ReferenceRehydrator(validator,
                        new FinancialEventMapper())), orchestrator);
    }

    @Test
    void completeDraftIsRehydratedAndExecutedByExistingAgentOnce() {
        ScenarioPrivacyEnvelope envelope = moneyEnvelope();
        when(privacyBoundaryService.createPreview(any())).thenReturn(envelope);
        when(gateway.extractScenarioEvents(envelope.externalRequest())).thenReturn(completeOneTimeDraft());
        when(orchestrator.execute(anyLong(), any())).thenReturn(completedAgentResult());

        NaturalLanguageWhatIfResponse response = service.execute(7L, request());

        assertThat(response.aiUsed()).isTrue();
        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.model()).isEqualTo("gpt-5.6-luna");
        assertThat(response.privacyMode()).isEqualTo(PrivacyMode.STRICT);
        assertThat(response.financialValuesTokenized()).isTrue();
        assertThat(response.agentStatus()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(response.toolCallCount()).isEqualTo(1);
        ArgumentCaptor<AgentExecutionInput> input = ArgumentCaptor.forClass(AgentExecutionInput.class);
        verify(gateway, times(1)).extractScenarioEvents(envelope.externalRequest());
        verify(orchestrator, times(1)).execute(org.mockito.ArgumentMatchers.eq(7L), input.capture());
        assertThat(input.getValue().requestedIntent()).isEqualTo("WHAT_IF_SIMULATION");
        assertThat(input.getValue().events().getFirst().amount()).isEqualByComparingTo("30000000.00");
    }

    @Test
    void providerDeclaredMissingFieldsReturnNeedsInputWithoutAgentToolExecution() {
        ScenarioPrivacyEnvelope envelope = moneyEnvelope();
        when(privacyBoundaryService.createPreview(any())).thenReturn(envelope);
        List<ExternalAiScenarioDraft> missingDrafts = List.of(
                missingAmountDraft(), missingEffectiveDateDraft(), missingRecurringEndDraft());
        when(gateway.extractScenarioEvents(envelope.externalRequest()))
                .thenReturn(missingDrafts.get(0), missingDrafts.get(1), missingDrafts.get(2));

        NaturalLanguageWhatIfResponse amount = service.execute(7L, request());
        NaturalLanguageWhatIfResponse date = service.execute(7L, request());
        NaturalLanguageWhatIfResponse period = service.execute(7L, request());

        assertThat(amount.missingInformation()).extracting(info -> info.code())
                .containsExactly(MissingInformationCode.EVENT_AMOUNT_REQUIRED);
        assertThat(date.missingInformation()).extracting(info -> info.code())
                .containsExactly(MissingInformationCode.EVENT_DATE_REQUIRED);
        assertThat(period.missingInformation()).extracting(info -> info.code())
                .containsExactly(MissingInformationCode.EVENT_PERIOD_REQUIRED);
        assertThat(amount.toolCallCount()).isZero();
        assertThat(date.toolCallCount()).isZero();
        assertThat(period.toolCallCount()).isZero();
        verify(gateway, times(3)).extractScenarioEvents(envelope.externalRequest());
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    @Test
    void piiOrOutboundGuardFailureMakesZeroProviderAndToolCalls() {
        ScenarioPrivacyEnvelope blocked = new ScenarioPrivacyEnvelope(PrivacyMode.STRICT, PrivacyStatus.BLOCKED,
                null, List.of(), List.of(), com.fintwin.fintwin.privacy.token.FinancialReferenceVault.empty());
        when(privacyBoundaryService.createPreview("blocked")).thenReturn(blocked);
        when(privacyBoundaryService.createPreview("guard-failure"))
                .thenThrow(new InvalidRequestException("sensitive raw value"));

        assertCode(() -> service.execute(7L, request("blocked")), AiErrorCode.AI_PRIVACY_GUARD_REJECTED);
        assertCode(() -> service.execute(7L, request("guard-failure")), AiErrorCode.AI_PRIVACY_GUARD_REJECTED);
        verify(gateway, never()).extractScenarioEvents(any());
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    @Test
    void untrustedDraftViolationsFailClosedBeforeAgentExecution() {
        ScenarioPrivacyEnvelope envelope = moneyEnvelope();
        when(privacyBoundaryService.createPreview(any())).thenReturn(envelope);
        ExternalAiScenarioDraft unsupported = new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("NEW_LOAN", "내년", null, "MONEY_1", "대출")), List.of());
        ExternalAiScenarioDraft directValue = new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", "내년", null, "MONEY_1", "3000만원 자동차")), List.of());
        ExternalAiScenarioDraft unknownReference = new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", "내년", null, "MONEY_99", "자동차")), List.of());
        ExternalAiScenarioDraft wrongCase = new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("one_time_expense", "내년", null, "MONEY_1", "자동차")), List.of());
        when(gateway.extractScenarioEvents(envelope.externalRequest()))
                .thenReturn(unsupported, directValue, unknownReference, wrongCase);

        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_SCHEMA_VIOLATION);
        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_SCHEMA_VIOLATION);
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    @Test
    void referenceTypeMismatchFailsClosedBeforeAgentExecution() {
        FinancialTokenizationResult tokenized = tokenizer.tokenize("10%");
        ExternalAiScenarioRequest outbound = new ExternalAiScenarioRequest(
                ExternalAiScenarioRequest.SCHEMA_VERSION, ExternalAiScenarioRequest.PURPOSE,
                ExternalAiScenarioRequest.LOCALE, YearMonth.of(2026, 8), "내년에 [PERCENT_1] 자동차 구매",
                List.of(FinancialEventType.values()), List.of(ReferenceType.values()),
                ExternalAiScenarioRequest.OUTPUT_CONTRACT_VERSION);
        ScenarioPrivacyEnvelope envelope = new ScenarioPrivacyEnvelope(PrivacyMode.STRICT, PrivacyStatus.SAFE,
                outbound, tokenized.references(), List.of(), tokenized.referenceVault());
        when(privacyBoundaryService.createPreview(any())).thenReturn(envelope);
        when(gateway.extractScenarioEvents(outbound)).thenReturn(new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", "내년", null, "PERCENT_1", "자동차 구매")), List.of()));

        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_SCHEMA_VIOLATION);

        verify(gateway, times(1)).extractScenarioEvents(outbound);
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    @Test
    void providerFailuresAreNotRetriedAndNeverExecuteAnAgentTool() {
        ScenarioPrivacyEnvelope envelope = moneyEnvelope();
        when(privacyBoundaryService.createPreview(any())).thenReturn(envelope);
        List<AiErrorCode> failures = List.of(AiErrorCode.AI_TIMEOUT, AiErrorCode.AI_RATE_LIMITED,
                AiErrorCode.AI_REFUSED, AiErrorCode.AI_INCOMPLETE_RESPONSE,
                AiErrorCode.AI_SCHEMA_VIOLATION);

        for (AiErrorCode failure : failures) {
            doThrow(new AiAdapterException(failure))
                    .when(gateway).extractScenarioEvents(envelope.externalRequest());
            assertCode(() -> service.execute(7L, request()), failure);
        }

        verify(gateway, times(failures.size())).extractScenarioEvents(envelope.externalRequest());
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    @Test
    void disabledConfigurationStopsBeforePrivacyAndProvider() {
        properties.setEnabled(false);

        assertCode(() -> service.execute(7L, request()), AiErrorCode.AI_DISABLED);

        verify(privacyBoundaryService, never()).createPreview(any());
        verify(gateway, never()).extractScenarioEvents(any());
        verify(orchestrator, never()).execute(anyLong(), any());
    }

    private ScenarioPrivacyEnvelope moneyEnvelope() {
        FinancialTokenizationResult tokenized = tokenizer.tokenize("3천만원");
        ExternalAiScenarioRequest request = new ExternalAiScenarioRequest(
                ExternalAiScenarioRequest.SCHEMA_VERSION, ExternalAiScenarioRequest.PURPOSE,
                ExternalAiScenarioRequest.LOCALE, YearMonth.of(2026, 8),
                "내년에 [MONEY_1] 자동차 구매", List.of(FinancialEventType.values()),
                List.of(ReferenceType.values()), ExternalAiScenarioRequest.OUTPUT_CONTRACT_VERSION);
        return new ScenarioPrivacyEnvelope(PrivacyMode.STRICT, PrivacyStatus.SAFE, request,
                tokenized.references(), List.of(), tokenized.referenceVault());
    }

    private ExternalAiScenarioDraft completeOneTimeDraft() {
        return new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", "내년", null, "MONEY_1", "자동차 구매")), List.of());
    }

    private ExternalAiScenarioDraft missingAmountDraft() {
        return new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", "내년", null, null, "자동차 구매")), List.of("AMOUNT"));
    }

    private ExternalAiScenarioDraft missingEffectiveDateDraft() {
        return new ExternalAiScenarioDraft("PURCHASE", List.of(
                event("ONE_TIME_EXPENSE", null, null, "MONEY_1", "자동차 구매")),
                List.of("EFFECTIVE_DATE"));
    }

    private ExternalAiScenarioDraft missingRecurringEndDraft() {
        ExternalAiScenarioDraft.EventDraft event = new ExternalAiScenarioDraft.EventDraft(
                "event-1", "RECURRING_EXPENSE_CHANGE", "DECREASE", null, null,
                "다음 달", null, null, null, null, null, "MONEY_1", "생활비 조정");
        return new ExternalAiScenarioDraft("EXPENSE_CHANGE", List.of(event), List.of("END_DATE"));
    }

    private ExternalAiScenarioDraft.EventDraft event(String type, String dateExpression,
                                                       String dateReference, String amountReference,
                                                       String description) {
        return new ExternalAiScenarioDraft.EventDraft("event-1", type, null,
                dateExpression, dateReference, null, null, null, null, null,
                amountReference, null, description);
    }

    private NaturalLanguageWhatIfRequest request() {
        return request("내년에 3천만원 자동차를 사면?");
    }

    private NaturalLanguageWhatIfRequest request(String scenarioText) {
        return new NaturalLanguageWhatIfRequest(scenarioText, YearMonth.of(2026, 8), 36,
                new NaturalLanguageWhatIfRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private AgentExecutionResult completedAgentResult() {
        ScenarioAgentToolResult toolResult = new ScenarioAgentToolResult(YearMonth.of(2026, 8), 36,
                YearMonth.of(2029, 7), new BigDecimal("100"), new BigDecimal("80"),
                new BigDecimal("-20"), new BigDecimal("-20"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("20"), List.of(), List.of(), List.of());
        AgentExplanation explanation = new AgentExplanation("비교", "차이", List.of(
                new AgentEvidence("typedResult.netWorthDelta", "-20")), "가정", "면책");
        return new AgentExecutionResult(AgentStatus.COMPLETED, "WHAT_IF_SIMULATION",
                AgentToolName.SCENARIO_COMPARISON_TOOL, null, null, List.of(), List.of(),
                AgentResultType.SCENARIO_COMPARISON, toolResult, List.of(), explanation,
                List.of(new AgentTraceStep(1, AgentState.RECEIVED, "orchestrator", "accepted")),
                1, "privacy", "disclaimer");
    }

    private OpenAiProperties enabledProperties() {
        OpenAiProperties value = new OpenAiProperties();
        value.setEnabled(true);
        value.setApiKey("test-only");
        return value;
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, AiErrorCode code) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(AiAdapterException.class,
                exception -> {
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.getMessage()).doesNotContain("sensitive raw value", "3000만원");
                });
    }
}
