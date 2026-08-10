package com.fintwin.fintwin.financialprofile.dto;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;

import java.math.BigDecimal;
import java.time.Instant;

public record FinancialProfileResponse(Long id, Long userId, int version, Long previousProfileId,
        BigDecimal monthlyIncome, BigDecimal cashAssets,
        BigDecimal deposits, BigDecimal investmentAssets, BigDecimal totalLoanBalance, BigDecimal loanInterestRate,
        BigDecimal monthlyFixedExpenses, BigDecimal monthlyVariableExpenses, BigDecimal monthlySavings,
        BigDecimal monthlyInvestments, Instant createdAt) {
    public static FinancialProfileResponse from(FinancialProfile profile) {
        return new FinancialProfileResponse(profile.getId(), profile.getUser().getId(), profile.getVersion(),
                profile.getPreviousProfileId(), profile.getMonthlyIncome(), profile.getCashAssets(),
                profile.getDeposits(), profile.getInvestmentAssets(),
                profile.getTotalLoanBalance(), profile.getLoanInterestRate(), profile.getMonthlyFixedExpenses(),
                profile.getMonthlyVariableExpenses(), profile.getMonthlySavings(), profile.getMonthlyInvestments(),
                profile.getCreatedAt());
    }
}
