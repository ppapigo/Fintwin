package com.fintwin.fintwin.simulation.config;

import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationConfig {
    @Bean
    MonthlyFinancialSimulationEngine monthlyFinancialSimulationEngine() {
        return new MonthlyFinancialSimulationEngine();
    }
}
