package com.fintwin.fintwin.goal.config;

import com.fintwin.fintwin.goal.solver.GoalReverseSolver;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoalConfig {
    @Bean
    GoalReverseSolver goalReverseSolver(MonthlyFinancialSimulationEngine simulationEngine) {
        return new GoalReverseSolver(simulationEngine);
    }
}
