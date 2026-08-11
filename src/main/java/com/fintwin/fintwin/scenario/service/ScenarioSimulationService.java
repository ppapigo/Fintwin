package com.fintwin.fintwin.scenario.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.domain.ScenarioRiskFinding;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.engine.ScenarioMonthlyAdjustmentProvider;
import com.fintwin.fintwin.scenario.risk.ScenarioResultRiskChecker;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.service.SimulationDomainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ScenarioSimulationService {
    private final FinancialProfileService financialProfileService;
    private final FinancialEventMapper eventMapper;
    private final SimulationDomainMapper domainMapper;
    private final MonthlyFinancialSimulationEngine simulationEngine;
    private final ScenarioResultRiskChecker riskChecker;

    public ScenarioSimulationService(FinancialProfileService financialProfileService,
                                     FinancialEventMapper eventMapper,
                                     SimulationDomainMapper domainMapper,
                                     MonthlyFinancialSimulationEngine simulationEngine) {
        this.financialProfileService = financialProfileService;
        this.eventMapper = eventMapper;
        this.domainMapper = domainMapper;
        this.simulationEngine = simulationEngine;
        this.riskChecker = new ScenarioResultRiskChecker();
    }

    public ScenarioComparisonResponse compare(Long userId, ScenarioComparisonRequest request) {
        FinancialProfileResponse profile = financialProfileService.getCurrent(userId);
        SimulationInput input = domainMapper.inputFrom(profile);
        SimulationAssumptions assumptions = domainMapper.assumptionsFrom(profile, request.assumptions());
        ScenarioDefinition scenario = eventMapper.map(request.scenarioName(), request.events(),
                request.startYearMonth(), request.horizonMonths());
        ScenarioMonthlyAdjustmentProvider adjustmentProvider =
                new ScenarioMonthlyAdjustmentProvider(scenario.events());

        SimulationSummary baseline = simulationEngine.simulate(input, assumptions, request.startYearMonth(),
                request.horizonMonths());
        SimulationSummary whatIf = simulationEngine.simulate(input, assumptions, request.startYearMonth(),
                request.horizonMonths(), adjustmentProvider);

        List<String> warnings = buildWarnings(scenario, adjustmentProvider, whatIf);
        return ScenarioComparisonResponse.from(profile.id(), profile.version(), request.startYearMonth(),
                request.horizonMonths(), assumptions, scenario, baseline, whatIf, warnings);
    }

    public MultiScenarioComparisonResponse compareMultiple(Long userId, MultiScenarioComparisonRequest request) {
        FinancialProfileResponse profile = financialProfileService.getCurrent(userId);
        SimulationInput input = domainMapper.inputFrom(profile);
        SimulationAssumptions assumptions = domainMapper.assumptionsFrom(profile, request.assumptions());
        SimulationSummary baselineSummary = simulationEngine.simulate(input, assumptions, request.startYearMonth(),
                request.horizonMonths());
        BaselineSimulationResponse baseline = BaselineSimulationResponse.from(profile.id(), profile.version(),
                request.startYearMonth(), request.horizonMonths(), assumptions, baselineSummary);

        List<MultiScenarioComparisonResponse.ScenarioResult> scenarioResults = new ArrayList<>();
        for (MultiScenarioComparisonRequest.ScenarioRequest requestedScenario : request.scenarios()) {
            ScenarioDefinition scenario = eventMapper.mapAllowingEmpty(requestedScenario.label(),
                    requestedScenario.events(), request.startYearMonth(), request.horizonMonths());
            ScenarioMonthlyAdjustmentProvider adjustmentProvider =
                    new ScenarioMonthlyAdjustmentProvider(scenario.events());
            SimulationSummary scenarioSummary = simulationEngine.simulate(input, assumptions,
                    request.startYearMonth(), request.horizonMonths(), adjustmentProvider);
            BaselineSimulationResponse simulated = BaselineSimulationResponse.from(profile.id(), profile.version(),
                    request.startYearMonth(), request.horizonMonths(), assumptions, scenarioSummary);
            List<ScenarioRiskFinding> risks = riskChecker.scenarioRisks(input, scenario, adjustmentProvider,
                    baselineSummary, scenarioSummary);
            scenarioResults.add(MultiScenarioComparisonResponse.ScenarioResult.from(
                    requestedScenario.scenarioKey(), requestedScenario.label(), scenario, baseline, simulated, risks));
        }

        return MultiScenarioComparisonResponse.from(baseline, scenarioResults,
                riskChecker.baselineRisks(baselineSummary));
    }

    private List<String> buildWarnings(ScenarioDefinition scenario,
                                       ScenarioMonthlyAdjustmentProvider adjustmentProvider,
                                       SimulationSummary whatIf) {
        List<String> warnings = new ArrayList<>(scenario.warnings());
        if (whatIf.monthlyResults().stream().anyMatch(MonthlySimulationResult::cashShortfall)) {
            warnings.add("The what-if scenario contains at least one month with a cash shortfall.");
        }
        if (whatIf.monthlyResults().stream().anyMatch(MonthlySimulationResult::negativeAmortization)) {
            warnings.add("The what-if scenario contains at least one month with negative amortization.");
        }
        boolean repaymentClamped = whatIf.monthlyResults().stream().anyMatch(result -> {
            BigDecimal requested = adjustmentProvider.adjustmentsFor(result.yearMonth()).extraDebtRepayment();
            return requested.compareTo(result.extraDebtRepayment()) > 0;
        });
        if (repaymentClamped) {
            warnings.add("At least one extra debt repayment was limited by remaining debt or available cash.");
        }
        return warnings;
    }
}
