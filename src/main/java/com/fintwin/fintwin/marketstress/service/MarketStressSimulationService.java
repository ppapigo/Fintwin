package com.fintwin.fintwin.marketstress.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.marketstress.domain.MarketExposure;
import com.fintwin.fintwin.marketstress.domain.MarketStressResult;
import com.fintwin.fintwin.marketstress.domain.MarketStressScenario;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationRequest;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationResponse;
import com.fintwin.fintwin.marketstress.engine.MarketStressSimulationEngine;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.service.SimulationDomainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketStressSimulationService {
    private final FinancialProfileService financialProfileService;
    private final SimulationDomainMapper domainMapper;
    private final MarketStressSimulationEngine marketStressEngine;

    public MarketStressSimulationService(FinancialProfileService financialProfileService,
                                         SimulationDomainMapper domainMapper,
                                         MarketStressSimulationEngine marketStressEngine) {
        this.financialProfileService = financialProfileService;
        this.domainMapper = domainMapper;
        this.marketStressEngine = marketStressEngine;
    }

    public MarketStressSimulationResponse simulate(Long userId, MarketStressSimulationRequest request) {
        FinancialProfileResponse profile = financialProfileService.getCurrent(userId);
        SimulationInput input = domainMapper.inputFrom(profile);
        SimulationAssumptions assumptions = domainMapper.assumptionsFrom(profile, request.assumptions());
        MarketExposure exposure = request.exposure().toDomain();
        if (exposure.totalStockExposure().compareTo(profile.investmentAssets()) > 0) {
            throw new InvalidRequestException("Stock exposure cannot exceed current investment assets");
        }
        MarketStressScenario scenario = request.stressScenario().toDomain();
        MarketStressResult result = marketStressEngine.simulate(input, assumptions, request.startYearMonth(),
                request.horizonMonths(), exposure, scenario, request.targetNetWorth());
        return MarketStressSimulationResponse.from(profile.version(), profile.investmentAssets(),
                request.startYearMonth(), request.horizonMonths(), assumptions, exposure, scenario, result);
    }
}
