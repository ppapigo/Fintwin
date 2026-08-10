package com.fintwin.fintwin.pattern.dto;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.pattern.domain.AverageFinancialPattern;
import com.fintwin.fintwin.pattern.domain.CategorySpendingPattern;
import com.fintwin.fintwin.pattern.domain.ExpenseClassificationEstimate;
import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.FinancialProfileDraft;
import com.fintwin.fintwin.pattern.domain.FinancialVolatilityPattern;
import com.fintwin.fintwin.pattern.domain.MonthlyCashFlowPattern;
import com.fintwin.fintwin.pattern.domain.PatternWarning;
import com.fintwin.fintwin.pattern.domain.RecurringTransactionPattern;
import com.fintwin.fintwin.pattern.domain.SpendingTrendPattern;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record FinancialPatternAnalysisResponse(
        String algorithmVersion,
        AnalysisPeriodResponse analysisPeriod,
        int transactionCount,
        List<MonthlyCashFlowResponse> monthlyCashFlows,
        AveragePatternResponse averages,
        List<CategorySpendingResponse> categorySpending,
        List<RecurringTransactionResponse> recurringTransactions,
        ExpenseClassificationResponse expenseClassification,
        VolatilityResponse volatility,
        SpendingTrendResponse spendingTrend,
        List<YearMonth> deficitMonths,
        FinancialProfileDraftResponse profileDraft,
        CurrentProfileComparisonResponse currentProfileComparison,
        AnalysisRulesResponse analysisRules,
        List<WarningResponse> warnings,
        PrivacyNoticeResponse privacyNotice
) {
    public static FinancialPatternAnalysisResponse from(FinancialPatternReport report,
                                                        FinancialProfileResponse currentProfile) {
        CurrentProfileComparisonResponse comparison = CurrentProfileComparisonResponse.from(
                report.profileDraft(), currentProfile);
        return new FinancialPatternAnalysisResponse(report.analysisRules().algorithmVersion(),
                new AnalysisPeriodResponse(report.analysisStartYearMonth(), report.analysisEndYearMonth(),
                        report.includedMonthCount()), report.transactionCount(),
                report.monthlyCashFlows().stream().map(MonthlyCashFlowResponse::from).toList(),
                AveragePatternResponse.from(report.averages()),
                report.categorySpending().stream().map(CategorySpendingResponse::from).toList(),
                report.recurringTransactions().stream().map(RecurringTransactionResponse::from).toList(),
                ExpenseClassificationResponse.from(report.expenseClassification()),
                VolatilityResponse.from(report.volatility()), SpendingTrendResponse.from(report.spendingTrend()),
                report.deficitMonths(), FinancialProfileDraftResponse.from(report.profileDraft(), currentProfile),
                comparison, AnalysisRulesResponse.from(report.analysisRules()),
                report.warnings().stream().map(WarningResponse::from).toList(), PrivacyNoticeResponse.standard());
    }

    public record AnalysisPeriodResponse(YearMonth startYearMonth, YearMonth endYearMonth, int includedMonthCount) {
    }

    public record MonthlyCashFlowResponse(
            YearMonth yearMonth,
            int transactionCount,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savingTransfers,
            BigDecimal investmentTransfers,
            BigDecimal debtPayments,
            BigDecimal transfers,
            BigDecimal monthlySurplus,
            BigDecimal liquidityAfterAllocations
    ) {
        static MonthlyCashFlowResponse from(MonthlyCashFlowPattern pattern) {
            return new MonthlyCashFlowResponse(pattern.yearMonth(), pattern.transactionCount(), pattern.income(),
                    pattern.expenses(), pattern.savingTransfers(), pattern.investmentTransfers(),
                    pattern.debtPayments(), pattern.transfers(), pattern.monthlySurplus(),
                    pattern.liquidityAfterAllocations());
        }
    }

    public record AveragePatternResponse(
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            BigDecimal monthlySavingTransfers,
            BigDecimal monthlyInvestmentTransfers,
            BigDecimal monthlyDebtPayments,
            BigDecimal monthlySurplus,
            BigDecimal monthlyLiquidityAfterAllocations,
            BigDecimal savingsRatePercent,
            YearMonth highestExpenseMonth,
            BigDecimal highestExpenseAmount,
            YearMonth lowestExpenseMonth,
            BigDecimal lowestExpenseAmount,
            int deficitMonthCount,
            BigDecimal deficitMonthRatioPercent
    ) {
        static AveragePatternResponse from(AverageFinancialPattern pattern) {
            return new AveragePatternResponse(pattern.averageMonthlyIncome(), pattern.averageMonthlyExpenses(),
                    pattern.averageMonthlySavingTransfers(), pattern.averageMonthlyInvestmentTransfers(),
                    pattern.averageMonthlyDebtPayments(), pattern.averageMonthlySurplus(),
                    pattern.averageMonthlyLiquidityAfterAllocations(), pattern.savingsRatePercent(),
                    pattern.highestExpenseMonth(), pattern.highestExpenseAmount(), pattern.lowestExpenseMonth(),
                    pattern.lowestExpenseAmount(), pattern.deficitMonthCount(), pattern.deficitMonthRatioPercent());
        }
    }

    public record CategorySpendingResponse(
            String category,
            BigDecimal totalExpenses,
            BigDecimal averageMonthlyExpenses,
            BigDecimal spendingRatioPercent
    ) {
        static CategorySpendingResponse from(CategorySpendingPattern pattern) {
            return new CategorySpendingResponse(pattern.category().name(), pattern.totalExpenses(),
                    pattern.averageMonthlyExpenses(), pattern.spendingRatioPercent());
        }
    }

    public record RecurringTransactionResponse(
            String type,
            String category,
            String displayDescription,
            int detectedMonthCount,
            int totalOccurrenceCount,
            BigDecimal averageOccurrencesPerMonth,
            BigDecimal averageMonthlyAmount,
            BigDecimal amountVariationRatePercent,
            YearMonth lastOccurrenceYearMonth,
            boolean fixedExpenseCandidate
    ) {
        static RecurringTransactionResponse from(RecurringTransactionPattern pattern) {
            return new RecurringTransactionResponse(pattern.type().name(), pattern.category().name(),
                    pattern.displayDescription(), pattern.detectedMonthCount(), pattern.totalOccurrenceCount(),
                    pattern.averageOccurrencesPerMonth(), pattern.averageMonthlyAmount(),
                    pattern.amountVariationRatePercent(), pattern.lastOccurrenceYearMonth(),
                    pattern.fixedExpenseCandidate());
        }
    }

    public record ExpenseClassificationResponse(
            BigDecimal estimatedFixedExpenses,
            BigDecimal estimatedVariableExpenses,
            String calculationMethod,
            String dataConfidence,
            String reviewNotice
    ) {
        static ExpenseClassificationResponse from(ExpenseClassificationEstimate estimate) {
            return new ExpenseClassificationResponse(estimate.estimatedFixedExpenses(),
                    estimate.estimatedVariableExpenses(), estimate.calculationMethod(), estimate.dataConfidence(),
                    estimate.reviewNotice());
        }
    }

    public record VolatilityResponse(
            BigDecimal incomeMeanAbsoluteDeviation,
            BigDecimal expenseMeanAbsoluteDeviation,
            BigDecimal cashFlowMeanAbsoluteDeviation,
            BigDecimal incomeVolatilityRatioPercent,
            BigDecimal expenseVolatilityRatioPercent,
            BigDecimal cashFlowVolatilityRatioPercent
    ) {
        static VolatilityResponse from(FinancialVolatilityPattern pattern) {
            return new VolatilityResponse(pattern.incomeMeanAbsoluteDeviation(),
                    pattern.expenseMeanAbsoluteDeviation(), pattern.cashFlowMeanAbsoluteDeviation(),
                    pattern.incomeVolatilityRatioPercent(), pattern.expenseVolatilityRatioPercent(),
                    pattern.cashFlowVolatilityRatioPercent());
        }
    }

    public record SpendingTrendResponse(
            String classification,
            BigDecimal trendRatePercent,
            BigDecimal firstPeriodAverageExpenses,
            BigDecimal secondPeriodAverageExpenses
    ) {
        static SpendingTrendResponse from(SpendingTrendPattern pattern) {
            return new SpendingTrendResponse(pattern.trend().name(), pattern.trendRatePercent(),
                    pattern.firstPeriodAverageExpenses(), pattern.secondPeriodAverageExpenses());
        }
    }

    public record ProfileDraftValuesResponse(
            BigDecimal monthlyIncome,
            BigDecimal monthlyFixedExpenses,
            BigDecimal monthlyVariableExpenses,
            BigDecimal monthlySavings,
            BigDecimal monthlyInvestment
    ) {
        static ProfileDraftValuesResponse fromDraft(FinancialProfileDraft draft) {
            return new ProfileDraftValuesResponse(draft.monthlyIncome(), draft.fixedExpenses(),
                    draft.variableExpenses(), draft.monthlySavings(), draft.monthlyInvestment());
        }

        static ProfileDraftValuesResponse fromProfile(FinancialProfileResponse profile) {
            return new ProfileDraftValuesResponse(profile.monthlyIncome(), profile.monthlyFixedExpenses(),
                    profile.monthlyVariableExpenses(), profile.monthlySavings(), profile.monthlyInvestments());
        }

        static ProfileDraftValuesResponse delta(FinancialProfileDraft draft, FinancialProfileResponse profile) {
            return new ProfileDraftValuesResponse(draft.monthlyIncome().subtract(profile.monthlyIncome()),
                    draft.fixedExpenses().subtract(profile.monthlyFixedExpenses()),
                    draft.variableExpenses().subtract(profile.monthlyVariableExpenses()),
                    draft.monthlySavings().subtract(profile.monthlySavings()),
                    draft.monthlyInvestment().subtract(profile.monthlyInvestments()));
        }
    }

    public record PreservedProfileFieldsResponse(
            BigDecimal cashAssets,
            BigDecimal deposits,
            BigDecimal investmentAssets,
            BigDecimal totalLoanBalance,
            BigDecimal loanInterestRate
    ) {
        static PreservedProfileFieldsResponse from(FinancialProfileResponse profile) {
            return new PreservedProfileFieldsResponse(profile.cashAssets(), profile.deposits(),
                    profile.investmentAssets(), profile.totalLoanBalance(), profile.loanInterestRate());
        }
    }

    public record FinancialProfileDraftResponse(
            ProfileDraftValuesResponse estimatedValues,
            YearMonth analysisStartYearMonth,
            YearMonth analysisEndYearMonth,
            String dataSufficiency,
            List<String> calculationBasis,
            PreservedProfileFieldsResponse preservedExistingFields,
            List<String> notInferredFields,
            boolean userConfirmationRequired
    ) {
        static FinancialProfileDraftResponse from(FinancialProfileDraft draft,
                                                  FinancialProfileResponse currentProfile) {
            List<String> notInferred = currentProfile == null
                    ? List.of("cashAssets", "deposits", "investmentAssets", "totalLoanBalance",
                    "loanInterestRate", "financialGoals")
                    : List.of("financialGoals");
            PreservedProfileFieldsResponse preserved = currentProfile == null
                    ? null : PreservedProfileFieldsResponse.from(currentProfile);
            return new FinancialProfileDraftResponse(ProfileDraftValuesResponse.fromDraft(draft),
                    draft.analysisStartYearMonth(), draft.analysisEndYearMonth(), draft.dataSufficiency(),
                    draft.calculationBasis(), preserved, notInferred, draft.userConfirmationRequired());
        }
    }

    public record CurrentProfileComparisonResponse(
            Long financialProfileId,
            int financialProfileVersion,
            ProfileDraftValuesResponse currentValues,
            ProfileDraftValuesResponse draftValues,
            ProfileDraftValuesResponse deltas,
            String deltaDirection
    ) {
        static CurrentProfileComparisonResponse from(FinancialProfileDraft draft,
                                                     FinancialProfileResponse profile) {
            if (profile == null) {
                return null;
            }
            return new CurrentProfileComparisonResponse(profile.id(), profile.version(),
                    ProfileDraftValuesResponse.fromProfile(profile), ProfileDraftValuesResponse.fromDraft(draft),
                    ProfileDraftValuesResponse.delta(draft, profile), "draft - currentProfile");
        }
    }

    public record AnalysisRulesResponse(
            int maximumFileBytes,
            int maximumTransactionRows,
            int maximumAnalysisMonths,
            int minimumHistoryMonths,
            int recurringMinimumDistinctMonths,
            int highConfidenceMinimumMonths,
            BigDecimal recurringAmountTolerancePercent,
            BigDecimal trendThresholdPercent,
            BigDecimal highVolatilityThresholdPercent,
            BigDecimal lowSavingsRateThresholdPercent,
            BigDecimal manyUncategorizedThresholdPercent,
            int moneyScale,
            int percentageScale,
            String roundingMode,
            String recurringMonthlyRepresentative
    ) {
        static AnalysisRulesResponse from(FinancialPatternRules rules) {
            return new AnalysisRulesResponse(Math.toIntExact(rules.maximumFileBytes()),
                    rules.maximumTransactionRows(), rules.maximumAnalysisMonths(), rules.minimumHistoryMonths(),
                    rules.recurringMinimumDistinctMonths(), rules.highConfidenceMinimumMonths(),
                    rules.recurringAmountTolerancePercent(), rules.trendThresholdPercent(),
                    rules.highVolatilityThresholdPercent(), rules.lowSavingsRateThresholdPercent(),
                    rules.manyUncategorizedThresholdPercent(), rules.moneyScale(), rules.percentageScale(),
                    rules.roundingMode(), "Monthly total divided by occurrences; all representatives within median +/- tolerance");
        }
    }

    public record WarningResponse(String code, String message) {
        static WarningResponse from(PatternWarning warning) {
            return new WarningResponse(warning.code().name(), warning.message());
        }
    }

    public record PrivacyNoticeResponse(
            String processing,
            String storage,
            String externalTransfer,
            String profileUpdate
    ) {
        static PrivacyNoticeResponse standard() {
            return new PrivacyNoticeResponse(
                    "Uploaded transactions are used only in memory while this analysis request is processed.",
                    "The original CSV, normalized transactions, and analysis result are not stored in a database or file system.",
                    "Transaction data is not sent to an external AI or external API.",
                    "The financial profile is not created or changed; the user must review and explicitly apply the draft.");
        }
    }
}
