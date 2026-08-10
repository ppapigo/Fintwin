package com.fintwin.fintwin.simulation.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BaselineSimulationService {
    private final FinancialProfileService financialProfileService;
    private final MonthlyFinancialSimulationEngine simulationEngine;
    private final SimulationDomainMapper domainMapper;

    public BaselineSimulationService(FinancialProfileService financialProfileService,
                                     MonthlyFinancialSimulationEngine simulationEngine,
                                     SimulationDomainMapper domainMapper) {
        this.financialProfileService = financialProfileService;
        this.simulationEngine = simulationEngine;
        this.domainMapper = domainMapper;
    }

    public BaselineSimulationResponse simulate(Long userId, BaselineSimulationRequest request) {
        FinancialProfileResponse profile = financialProfileService.getCurrent(userId);
        SimulationInput input = domainMapper.inputFrom(profile);
        SimulationAssumptions assumptions = domainMapper.assumptionsFrom(profile, request.assumptions());
        SimulationSummary summary = simulationEngine.simulate(input, assumptions, request.startYearMonth(),
                request.horizonMonths());
        return BaselineSimulationResponse.from(profile.id(), profile.version(), request.startYearMonth(),
                request.horizonMonths(), assumptions, summary);
    }

}
