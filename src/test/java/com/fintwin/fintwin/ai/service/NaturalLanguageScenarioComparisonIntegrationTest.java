package com.fintwin.fintwin.ai.service;

import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import com.fintwin.fintwin.agent.domain.ScenarioComparisonDetails;
import com.fintwin.fintwin.agent.explanation.RuleBasedExplanationComposer;
import com.fintwin.fintwin.agent.gap.InformationGapChecker;
import com.fintwin.fintwin.agent.orchestration.FinTwinAgentOrchestrator;
import com.fintwin.fintwin.agent.risk.DeterministicRiskChecker;
import com.fintwin.fintwin.agent.routing.DeterministicIntentRouter;
import com.fintwin.fintwin.agent.tool.BaselineSimulationTool;
import com.fintwin.fintwin.agent.tool.GoalReverseSimulationTool;
import com.fintwin.fintwin.agent.tool.ScenarioComparisonTool;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfRequest;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfResponse;
import com.fintwin.fintwin.ai.openai.config.OpenAiProperties;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.privacy.bridge.ValidatedScenarioAgentCommandFactory;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;
import com.fintwin.fintwin.scenario.service.ScenarioSimulationService;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class NaturalLanguageScenarioComparisonIntegrationTest {
    private static final YearMonth START = YearMonth.of(2026, 8);

    @Autowired
    private ScenarioSimulationService scenarioSimulationService;
    @Autowired
    private FinancialProfileService financialProfileService;
    @Autowired
    private FinancialProfileRepository financialProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InformationGapChecker gapChecker;
    @Autowired
    private BaselineSimulationTool baselineTool;
    @Autowired
    private GoalReverseSimulationTool goalTool;
    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void naturalLanguageAndDirectInputProduceExactlyTheSameFullComparison(GoldenCase golden) throws Exception {
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();
        Long userId = userRepository.saveAndFlush(User.create()).getId();
        financialProfileService.create(userId, profile());

        ExternalAiGateway gateway = mock(ExternalAiGateway.class);
        when(gateway.extractScenarioEvents(any())).thenReturn(golden.draft());
        ScenarioSimulationService scenarioSpy = mock(ScenarioSimulationService.class,
                delegatesTo(scenarioSimulationService));
        ServiceHarness harness = service(gateway, scenarioSpy);

        NaturalLanguageWhatIfResponse naturalResponse = harness.service().execute(userId,
                naturalRequest(golden));

        assertThat(naturalResponse.agentStatus()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(naturalResponse.toolCallCount()).isEqualTo(1);
        assertThat(naturalResponse.typedResult()).isInstanceOf(ScenarioAgentToolResult.class);
        ScenarioAgentToolResult toolResult = (ScenarioAgentToolResult) naturalResponse.typedResult();
        ScenarioComparisonDetails naturalDetails = toolResult.comparisonDetails();
        assertThat(naturalDetails.baseline().monthlyResults()).hasSize(golden.horizonMonths());
        assertThat(naturalDetails.whatIf().monthlyResults()).hasSize(golden.horizonMonths());
        assertThat(naturalDetails.checkpointComparisons()).hasSize(golden.expectedCheckpoints());
        assertThat(naturalDetails.finalComparison().netWorthDelta())
                .isEqualByComparingTo(golden.expectedNetWorthDelta());

        verify(gateway, times(1)).extractScenarioEvents(any());
        verify(harness.scenarioTool(), times(1)).execute(anyLong(), any());
        verify(scenarioSpy, times(1)).compare(anyLong(), any());
        var order = inOrder(gateway, scenarioSpy);
        order.verify(gateway).extractScenarioEvents(any());
        order.verify(scenarioSpy).compare(anyLong(), any());

        ScenarioComparisonResponse directResponse = scenarioSimulationService.compare(userId,
                directRequest(golden, naturalDetails));
        ScenarioComparisonDetails directDetails = ScenarioComparisonDetails.from(directResponse);
        assertThat(naturalDetails).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(directDetails);

        ExternalAiScenarioRequest outbound = captureOutbound(gateway);
        assertThat(Arrays.stream(ExternalAiScenarioRequest.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .containsExactlyInAnyOrder("schemaVersion", "purpose", "locale", "currentYearMonth",
                        "sanitizedScenarioText", "supportedEventTypes", "supportedReferenceTypes",
                        "outputContractVersion");
        assertThat(outbound.sanitizedScenarioText())
                .contains(golden.expectedReferences().toArray(String[]::new))
                .doesNotContain(golden.rawFinancialValue());

        String serialized = objectMapper.writeValueAsString(naturalResponse);
        assertThat(serialized).contains("\"comparisonDetails\"", "\"financialProfileVersion\"")
                .doesNotContain("\"financialProfileId\"", "\"profileId\"", "\"userId\"",
                        "\"scenarioText\"");
        assertThat(serialized.getBytes(StandardCharsets.UTF_8).length).isLessThan(2_000_000);
    }

    private ServiceHarness service(ExternalAiGateway gateway,
                                   ScenarioSimulationService scenarioSpy) {
        PersonalIdentifierDetector detector = new PersonalIdentifierDetector();
        FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
        ExternalAiDraftValidator validator = new ExternalAiDraftValidator(detector, tokenizer);
        PrivacyBoundaryService privacyBoundaryService = new PrivacyBoundaryService(detector, tokenizer,
                new OutboundPayloadGuard(detector, tokenizer),
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        ValidatedScenarioAgentCommandFactory commandFactory = new ValidatedScenarioAgentCommandFactory(
                new ReferenceRehydrator(validator, new FinancialEventMapper()));
        ScenarioComparisonTool scenarioTool = mock(ScenarioComparisonTool.class,
                delegatesTo(new ScenarioComparisonTool(scenarioSpy)));
        FinTwinAgentOrchestrator orchestrator = new FinTwinAgentOrchestrator(
                new DeterministicIntentRouter(), gapChecker, baselineTool,
                scenarioTool, goalTool, new DeterministicRiskChecker(),
                new RuleBasedExplanationComposer());

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("externalAiGateway", gateway);
        ObjectProvider<ExternalAiGateway> provider = beanFactory.getBeanProvider(ExternalAiGateway.class);
        OpenAiProperties properties = new OpenAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-only");
        return new ServiceHarness(new NaturalLanguageWhatIfService(properties, provider, privacyBoundaryService,
                validator, commandFactory, orchestrator), scenarioTool);
    }

    private NaturalLanguageWhatIfRequest naturalRequest(GoldenCase golden) {
        return new NaturalLanguageWhatIfRequest(golden.scenarioText(), START, golden.horizonMonths(),
                new NaturalLanguageWhatIfRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, decimal("300000")));
    }

    private ScenarioComparisonRequest directRequest(GoldenCase golden, ScenarioComparisonDetails details) {
        List<FinancialEventRequest> events = details.normalizedEvents().stream().map(event ->
                new FinancialEventRequest(event.eventId(), event.eventType(), event.effectiveYearMonth(),
                        event.startYearMonth(), event.endYearMonth(), event.amount(), event.monthlyDelta(),
                        event.description())).toList();
        return new ScenarioComparisonRequest("Direct equivalent", START, golden.horizonMonths(),
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, decimal("300000")), events);
    }

    private ExternalAiScenarioRequest captureOutbound(ExternalAiGateway gateway) {
        var captor = org.mockito.ArgumentCaptor.forClass(ExternalAiScenarioRequest.class);
        verify(gateway).extractScenarioEvents(captor.capture());
        return captor.getValue();
    }

    private FinancialProfileCreateRequest profile() {
        return new FinancialProfileCreateRequest(decimal("3000000"), decimal("2000000"), decimal("3000000"),
                decimal("1000000"), decimal("10000000"), BigDecimal.ZERO, decimal("800000"),
                decimal("700000"), decimal("300000"), decimal("200000"));
    }

    private static Stream<GoldenCase> goldenCases() {
        return Stream.of(
                new GoldenCase("one-time expense / 60 months", "내년 8월에 100만원을 쓰면?", 60, 3,
                        decimal("-1000000"), "100만원", List.of("[MONEY_1]"),
                        new ExternalAiScenarioDraft("PURCHASE", List.of(new ExternalAiScenarioDraft.EventDraft(
                                "event-one-time", "ONE_TIME_EXPENSE", null, "내년 8월", null,
                                null, null, null, null, null, "MONEY_1", null, "자동차 구매")), List.of())),
                new GoldenCase("recurring expense decrease / 36 months", "6개월 동안 월 생활비를 10만원 줄이면?",
                        36, 2, decimal("600000"), "10만원", List.of("[DURATION_1]", "[MONEY_1]"),
                        new ExternalAiScenarioDraft("EXPENSE_CHANGE", List.of(
                                new ExternalAiScenarioDraft.EventDraft("event-recurring",
                                        "RECURRING_EXPENSE_CHANGE", "DECREASE", null, null,
                                        "다음 달", null, null, null, "DURATION_1", null,
                                        "MONEY_1", "생활비 절감")), List.of())),
                new GoldenCase("income pause / 12 months", "3개월 동안 소득이 중단되면?", 12, 1,
                        decimal("-9000000"), "3개월", List.of("[DURATION_1]"),
                        new ExternalAiScenarioDraft("INCOME_PAUSE", List.of(
                                new ExternalAiScenarioDraft.EventDraft("event-income-pause", "INCOME_PAUSE",
                                        null, null, null, "다음 달", null, null, null,
                                        "DURATION_1", null, null, "소득 중단")), List.of()))
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record GoldenCase(
            String label,
            String scenarioText,
            int horizonMonths,
            int expectedCheckpoints,
            BigDecimal expectedNetWorthDelta,
            String rawFinancialValue,
            List<String> expectedReferences,
            ExternalAiScenarioDraft draft
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ServiceHarness(
            NaturalLanguageWhatIfService service,
            ScenarioComparisonTool scenarioTool
    ) {
    }
}
