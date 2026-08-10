package com.fintwin.fintwin.goal.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.goal.domain.FinancialGoal;
import com.fintwin.fintwin.goal.domain.GoalSolverResult;
import com.fintwin.fintwin.goal.domain.GoalType;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationRequest;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationResponse;
import com.fintwin.fintwin.goal.solver.GoalReverseSolver;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.service.SimulationDomainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class GoalReverseSimulationService {
    private final FinancialProfileService financialProfileService;
    private final SimulationDomainMapper domainMapper;
    private final GoalReverseSolver goalReverseSolver;

    public GoalReverseSimulationService(FinancialProfileService financialProfileService,
                                        SimulationDomainMapper domainMapper,
                                        GoalReverseSolver goalReverseSolver) {
        this.financialProfileService = financialProfileService;
        this.domainMapper = domainMapper;
        this.goalReverseSolver = goalReverseSolver;
    }

    public GoalReverseSimulationResponse reverseSimulate(Long userId, GoalReverseSimulationRequest request) {
        GoalType goalType = parseGoalType(request.goalType());
        validateRequest(request);
        FinancialProfileResponse profile = financialProfileService.getCurrent(userId);
        SimulationInput input = domainMapper.inputFrom(profile);
        SimulationAssumptions assumptions = domainMapper.assumptionsFrom(profile, request.assumptions());
        FinancialGoal goal = new FinancialGoal(goalType, request.targetAmount(), request.startYearMonth(),
                request.horizonMonths());
        GoalSolverResult result = goalReverseSolver.solve(input, assumptions, goal);
        return GoalReverseSimulationResponse.from(profile.id(), profile.version(), goal, assumptions, result);
    }

    private GoalType parseGoalType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new InvalidRequestException("goalType is required");
        }
        try {
            return GoalType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("unsupported goalType: " + rawType);
        }
    }

    private void validateRequest(GoalReverseSimulationRequest request) {
        if (request.targetAmount() == null || request.targetAmount().signum() <= 0) {
            throw new InvalidRequestException("targetAmount must be greater than zero");
        }
        if (request.startYearMonth() == null) {
            throw new InvalidRequestException("startYearMonth is required");
        }
        if (request.horizonMonths() == null || !request.isSupportedHorizon()) {
            throw new InvalidRequestException("horizonMonths must be one of 12, 36, or 60");
        }
        if (request.assumptions() == null) {
            throw new InvalidRequestException("assumptions are required");
        }
    }
}
