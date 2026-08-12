package com.fintwin.fintwin.marketstress.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.marketstress.dto.MarketContextResponse;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationRequest;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationResponse;
import com.fintwin.fintwin.marketstress.marketdata.MarketContextService;
import com.fintwin.fintwin.marketstress.service.MarketStressSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-stress")
public class MarketStressController {
    private final MarketContextService marketContextService;
    private final MarketStressSimulationService simulationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public MarketStressController(MarketContextService marketContextService,
                                  MarketStressSimulationService simulationService,
                                  CurrentUserIdProvider currentUserIdProvider) {
        this.marketContextService = marketContextService;
        this.simulationService = simulationService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @GetMapping("/context")
    public MarketContextResponse context() {
        return MarketContextResponse.from(marketContextService.currentContext());
    }

    @PostMapping("/simulate")
    public MarketStressSimulationResponse simulate(@Valid @RequestBody MarketStressSimulationRequest request) {
        return simulationService.simulate(currentUserIdProvider.currentUserId(), request);
    }
}
