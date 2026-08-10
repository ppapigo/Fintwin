package com.fintwin.fintwin.ai.service;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentExecutionInput;
import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.agent.domain.AgentState;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.MissingInformationCode;
import com.fintwin.fintwin.agent.orchestration.FinTwinAgentOrchestrator;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfRequest;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfResponse;
import com.fintwin.fintwin.ai.openai.config.OpenAiProperties;
import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.bridge.ValidatedScenarioAgentCommandFactory;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.PrivacyStatus;
import com.fintwin.fintwin.privacy.domain.ScenarioPrivacyEnvelope;
import com.fintwin.fintwin.privacy.gateway.ExternalAiGateway;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class NaturalLanguageWhatIfService {
    private final OpenAiProperties properties;
    private final ObjectProvider<ExternalAiGateway> gatewayProvider;
    private final PrivacyBoundaryService privacyBoundaryService;
    private final ExternalAiDraftValidator draftValidator;
    private final ValidatedScenarioAgentCommandFactory commandFactory;
    private final FinTwinAgentOrchestrator agentOrchestrator;

    public NaturalLanguageWhatIfService(OpenAiProperties properties,
                                        ObjectProvider<ExternalAiGateway> gatewayProvider,
                                        PrivacyBoundaryService privacyBoundaryService,
                                        ExternalAiDraftValidator draftValidator,
                                        ValidatedScenarioAgentCommandFactory commandFactory,
                                        FinTwinAgentOrchestrator agentOrchestrator) {
        this.properties = properties;
        this.gatewayProvider = gatewayProvider;
        this.privacyBoundaryService = privacyBoundaryService;
        this.draftValidator = draftValidator;
        this.commandFactory = commandFactory;
        this.agentOrchestrator = agentOrchestrator;
    }

    public NaturalLanguageWhatIfResponse execute(Long currentUserId, NaturalLanguageWhatIfRequest request) {
        if (!properties.isEnabled()) {
            throw new AiAdapterException(AiErrorCode.AI_DISABLED);
        }
        ExternalAiGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new AiAdapterException(AiErrorCode.AI_CONFIGURATION_INVALID);
        }

        ScenarioPrivacyEnvelope envelope = createSafeEnvelope(request.scenarioText());
        ExternalAiScenarioDraft draft = gateway.extractScenarioEvents(envelope.externalRequest());
        validateProviderDraft(draft, envelope);
        boolean tokenized = !envelope.references().isEmpty();
        if (!draft.missingFields().isEmpty()) {
            List<MissingInformation> missingInformation = mapMissingInformation(draft.missingFields());
            return NaturalLanguageWhatIfResponse.needsInput(properties.getProvider(), properties.getModel(),
                    tokenized, missingInformation, missingTrace());
        }

        AgentCommand command = createValidatedCommand(request, envelope, draft);
        AgentExecutionInput input = new AgentExecutionInput(command.intent().name(), command.startYearMonth(),
                command.horizonMonths(), command.assumptions(), command.events(), command.goalType(),
                command.targetAmount());
        AgentExecutionResult result = agentOrchestrator.execute(currentUserId, input);
        return NaturalLanguageWhatIfResponse.completed(properties.getProvider(), properties.getModel(),
                tokenized, result);
    }

    private ScenarioPrivacyEnvelope createSafeEnvelope(String scenarioText) {
        try {
            ScenarioPrivacyEnvelope envelope = privacyBoundaryService.createPreview(scenarioText);
            if (envelope.status() != PrivacyStatus.SAFE) {
                throw new AiAdapterException(AiErrorCode.AI_PRIVACY_GUARD_REJECTED);
            }
            return envelope;
        } catch (AiAdapterException exception) {
            throw exception;
        } catch (InvalidRequestException exception) {
            throw new AiAdapterException(AiErrorCode.AI_PRIVACY_GUARD_REJECTED, exception);
        }
    }

    private void validateProviderDraft(ExternalAiScenarioDraft draft, ScenarioPrivacyEnvelope envelope) {
        try {
            draftValidator.validateProviderDraft(draft, envelope.referenceVault());
        } catch (InvalidRequestException | IllegalArgumentException exception) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION, exception);
        }
    }

    private AgentCommand createValidatedCommand(NaturalLanguageWhatIfRequest request,
                                                ScenarioPrivacyEnvelope envelope,
                                                ExternalAiScenarioDraft draft) {
        try {
            return commandFactory.createWhatIfCommand(draft, envelope.referenceVault(),
                    envelope.externalRequest().currentYearMonth(), request.startYearMonth(),
                    request.horizonMonths(), request.toSimulationAssumptions());
        } catch (InvalidRequestException | IllegalArgumentException exception) {
            throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION, exception);
        }
    }

    private List<MissingInformation> mapMissingInformation(List<String> missingFields) {
        return missingFields.stream().map(this::mapMissingInformation).toList();
    }

    private MissingInformation mapMissingInformation(String missingField) {
        return switch (missingField) {
            case "EVENT_TYPE" -> missing(MissingInformationCode.EVENT_TYPE_REQUIRED, "events.eventType",
                    "금융 이벤트의 유형을 구체적으로 입력해주세요.");
            case "EFFECTIVE_DATE" -> missing(MissingInformationCode.EVENT_DATE_REQUIRED,
                    "events.effectiveYearMonth", "금융 이벤트가 발생할 시점을 입력해주세요.");
            case "START_DATE" -> missing(MissingInformationCode.EVENT_PERIOD_REQUIRED,
                    "events.startYearMonth", "금융 이벤트의 시작 시점을 입력해주세요.");
            case "END_DATE", "DURATION" -> missing(MissingInformationCode.EVENT_PERIOD_REQUIRED,
                    "events.endYearMonth", "금융 이벤트의 종료 시점 또는 기간을 입력해주세요.");
            case "AMOUNT" -> missing(MissingInformationCode.EVENT_AMOUNT_REQUIRED, "events.amount",
                    "금융 이벤트의 금액을 입력해주세요.");
            case "MONTHLY_DELTA" -> missing(MissingInformationCode.EVENT_AMOUNT_REQUIRED,
                    "events.monthlyDelta", "금융 이벤트의 월 증감 금액을 입력해주세요.");
            case "DESCRIPTION" -> missing(MissingInformationCode.EVENT_DESCRIPTION_REQUIRED,
                    "events.description", "금융 이벤트의 내용을 입력해주세요.");
            default -> throw new AiAdapterException(AiErrorCode.AI_SCHEMA_VIOLATION);
        };
    }

    private MissingInformation missing(MissingInformationCode code, String field, String question) {
        return new MissingInformation(code, field, question, AgentIntent.WHAT_IF_SIMULATION);
    }

    private List<AgentTraceStep> missingTrace() {
        return List.of(
                new AgentTraceStep(1, AgentState.RECEIVED, "NaturalLanguageWhatIfService", "REQUEST_ACCEPTED"),
                new AgentTraceStep(2, AgentState.ROUTED, "OpenAiExternalAiGateway", "WHAT_IF_DRAFT_EXTRACTED"),
                new AgentTraceStep(3, AgentState.GAP_CHECKED, "ExternalAiDraftValidator",
                        "REQUIRED_INFORMATION_MISSING"),
                new AgentTraceStep(4, AgentState.NEEDS_INPUT, "NaturalLanguageWhatIfService",
                        "CLARIFICATION_REQUIRED"));
    }
}
