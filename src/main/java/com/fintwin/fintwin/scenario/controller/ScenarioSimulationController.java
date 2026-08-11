package com.fintwin.fintwin.scenario.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.service.ScenarioSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class ScenarioSimulationController {
    private final ScenarioSimulationService scenarioSimulationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public ScenarioSimulationController(ScenarioSimulationService scenarioSimulationService,
                                        CurrentUserIdProvider currentUserIdProvider) {
        this.scenarioSimulationService = scenarioSimulationService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping("/compare")
    public ScenarioComparisonResponse compare(@Valid @RequestBody ScenarioComparisonRequest request) {
        return scenarioSimulationService.compare(currentUserIdProvider.currentUserId(), request);
    }

    @PostMapping("/compare-multiple")
    public MultiScenarioComparisonResponse compareMultiple(
            @Valid @RequestBody MultiScenarioComparisonRequest request) {
        return scenarioSimulationService.compareMultiple(currentUserIdProvider.currentUserId(), request);
    }
}
