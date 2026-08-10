package com.fintwin.fintwin.goal.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationRequest;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationResponse;
import com.fintwin.fintwin.goal.service.GoalReverseSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalReverseSimulationController {
    private final GoalReverseSimulationService goalReverseSimulationService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public GoalReverseSimulationController(GoalReverseSimulationService goalReverseSimulationService,
                                           CurrentUserIdProvider currentUserIdProvider) {
        this.goalReverseSimulationService = goalReverseSimulationService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping("/reverse-simulate")
    public GoalReverseSimulationResponse reverseSimulate(
            @Valid @RequestBody GoalReverseSimulationRequest request) {
        return goalReverseSimulationService.reverseSimulate(currentUserIdProvider.currentUserId(), request);
    }
}
