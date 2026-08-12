package com.fintwin.fintwin.marketstress.domain;

import java.math.BigDecimal;

public record GoalMarginComparison(
        GoalMarginStatus status,
        BigDecimal targetNetWorth,
        BigDecimal baselineFinalNetWorth,
        BigDecimal stressedFinalNetWorth,
        BigDecimal baselineMargin,
        BigDecimal stressedMargin,
        BigDecimal marginDelta
) {
    public static GoalMarginComparison notProvided(BigDecimal baselineFinalNetWorth,
                                                   BigDecimal stressedFinalNetWorth) {
        return new GoalMarginComparison(GoalMarginStatus.NOT_PROVIDED, null, baselineFinalNetWorth,
                stressedFinalNetWorth, null, null, null);
    }
}
