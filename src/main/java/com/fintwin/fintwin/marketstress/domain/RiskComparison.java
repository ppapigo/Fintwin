package com.fintwin.fintwin.marketstress.domain;

public record RiskComparison(RiskSnapshot baseline, RiskSnapshot stressed,
                             boolean newCashShortfall, boolean newNegativeAmortization) {
}
