package com.fintwin.fintwin.privacy.bridge;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.token.FinancialReferenceVault;
import com.fintwin.fintwin.privacy.token.ReferenceRehydrator;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public final class ValidatedScenarioAgentCommandFactory {
    private static final String VALIDATED_SCENARIO_NAME = "Validated Privacy Draft";

    private final ReferenceRehydrator referenceRehydrator;

    public ValidatedScenarioAgentCommandFactory(ReferenceRehydrator referenceRehydrator) {
        this.referenceRehydrator = referenceRehydrator;
    }

    public AgentCommand createWhatIfCommand(ExternalAiScenarioDraft draft,
                                             FinancialReferenceVault referenceVault,
                                             YearMonth currentYearMonth,
                                             YearMonth simulationStart,
                                             int horizonMonths,
                                             BaselineSimulationRequest.Assumptions assumptions) {
        ScenarioDefinition scenario = referenceRehydrator.rehydrate(VALIDATED_SCENARIO_NAME, draft,
                referenceVault, currentYearMonth, simulationStart, horizonMonths);
        return new AgentCommand(AgentIntent.WHAT_IF_SIMULATION, simulationStart, horizonMonths, assumptions,
                scenario.events().stream().map(this::toRequest).toList(), null, null);
    }

    private FinancialEventRequest toRequest(FinancialEvent event) {
        return switch (event) {
            case FinancialEvent.OneTimeExpense oneTime -> new FinancialEventRequest(oneTime.eventId(),
                    oneTime.eventType().name(), oneTime.effectiveYearMonth(), null, null, oneTime.amount(), null,
                    oneTime.description());
            case FinancialEvent.ExtraDebtRepayment repayment -> new FinancialEventRequest(repayment.eventId(),
                    repayment.eventType().name(), repayment.effectiveYearMonth(), null, null, repayment.amount(),
                    null, repayment.description());
            case FinancialEvent.RecurringExpenseChange recurring -> period(recurring.eventId(),
                    recurring.eventType().name(), recurring.startYearMonth(), recurring.endYearMonth(),
                    recurring.monthlyDelta(), recurring.description());
            case FinancialEvent.IncomeChange income -> period(income.eventId(), income.eventType().name(),
                    income.startYearMonth(), income.endYearMonth(), income.monthlyDelta(), income.description());
            case FinancialEvent.IncomePause pause -> period(pause.eventId(), pause.eventType().name(),
                    pause.startYearMonth(), pause.endYearMonth(), null, pause.description());
            case FinancialEvent.InvestmentContributionChange investment -> period(investment.eventId(),
                    investment.eventType().name(), investment.startYearMonth(), investment.endYearMonth(),
                    investment.monthlyDelta(), investment.description());
        };
    }

    private FinancialEventRequest period(String eventId, String eventType, YearMonth start, YearMonth end,
                                         java.math.BigDecimal monthlyDelta, String description) {
        return new FinancialEventRequest(eventId, eventType, null, start, end, null, monthlyDelta, description);
    }
}
