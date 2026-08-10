package com.fintwin.fintwin.simulation.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.service.BaselineSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class BaselineSimulationController {
    private final BaselineSimulationService simulationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public BaselineSimulationController(BaselineSimulationService simulationService,
                                        CurrentUserIdProvider currentUserIdProvider) {
        this.simulationService = simulationService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping("/baseline")
    public BaselineSimulationResponse simulateBaseline(@Valid @RequestBody BaselineSimulationRequest request) {
        return simulationService.simulate(currentUserIdProvider.currentUserId(), request);
    }
}
