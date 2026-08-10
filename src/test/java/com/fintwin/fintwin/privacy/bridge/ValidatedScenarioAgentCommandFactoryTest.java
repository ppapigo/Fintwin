package com.fintwin.fintwin.privacy.bridge;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.privacy.validation.ExternalAiDraftValidator;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import com.fintwin.fintwin.scenario.service.FinancialEventMapper;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatedScenarioAgentCommandFactoryTest {
    private final FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
    private final ExternalAiDraftValidator validator = new ExternalAiDraftValidator(
            new PersonalIdentifierDetector(), tokenizer);
    private final ValidatedScenarioAgentCommandFactory factory = new ValidatedScenarioAgentCommandFactory(
            new ReferenceRehydrator(validator, new FinancialEventMapper()));

    @Test
    void onlyValidatedAndRehydratedDraftBecomesWhatIfCommand() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();
        ExternalAiScenarioDraft draft = draft("MONEY_1");

        AgentCommand command = factory.createWhatIfCommand(draft, vault, YearMonth.of(2026, 8),
                YearMonth.of(2026, 8), 36, assumptions());

        assertThat(command.intent()).isEqualTo(AgentIntent.WHAT_IF_SIMULATION);
        assertThat(command.events()).hasSize(1);
        assertThat(command.events().getFirst().amount()).isEqualByComparingTo("30000000.00");
        assertThat(command.goalType()).isNull();
    }

    @Test
    void invalidDraftCannotCreateAgentCommand() {
        FinancialReferenceVault vault = tokenizer.tokenize("3천만원").referenceVault();

        assertThatThrownBy(() -> factory.createWhatIfCommand(draft("MONEY_99"), vault,
                YearMonth.of(2026, 8), YearMonth.of(2026, 8), 36, assumptions()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("External AI draft contains an unknown reference ID");
    }

    private ExternalAiScenarioDraft draft(String amountReference) {
        ExternalAiScenarioDraft.EventDraft event = new ExternalAiScenarioDraft.EventDraft(
                "event-1", "ONE_TIME_EXPENSE", null, "내년", null,
                null, null, null, null, null, amountReference, null, "자동차 구매");
        return new ExternalAiScenarioDraft("PURCHASE", List.of(event), List.of());
    }

    private BaselineSimulationRequest.Assumptions assumptions() {
        return new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
