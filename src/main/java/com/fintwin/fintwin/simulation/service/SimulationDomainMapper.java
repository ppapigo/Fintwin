package com.fintwin.fintwin.simulation.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SimulationDomainMapper {
    public SimulationInput inputFrom(FinancialProfileResponse profile) {
        BigDecimal liquidAssets = profile.cashAssets().add(profile.deposits());
        return new SimulationInput(liquidAssets, profile.investmentAssets(), profile.totalLoanBalance(),
                profile.loanInterestRate(), profile.monthlyIncome(), profile.monthlyFixedExpenses(),
                profile.monthlyVariableExpenses(), profile.monthlySavings(), profile.monthlyInvestments());
    }

    public SimulationAssumptions assumptionsFrom(FinancialProfileResponse profile,
                                                 BaselineSimulationRequest.Assumptions assumptions) {
        boolean hasDebt = profile.totalLoanBalance().signum() > 0;
        if (hasDebt && assumptions.monthlyDebtPayment() == null) {
            throw new InvalidRequestException("monthlyDebtPayment is required when debt exists");
        }
        return assumptions.toDomain(hasDebt);
    }
}
