package com.fintwin.fintwin.privacy.token;

import com.fintwin.fintwin.privacy.domain.ReferenceType;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialValueTokenizerTest {
    private final FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());

    @Test
    void tokenizesKoreanTenThousandMoneyWithoutConsumingSuffix() {
        FinancialTokenizationResult result = tokenizer.tokenize("내년에 3천만원짜리 자동차를 사면?");

        assertThat(result.sanitizedText()).isEqualTo("내년에 [MONEY_1]짜리 자동차를 사면?");
        assertThat(result.references()).extracting(reference -> reference.referenceType())
                .containsExactly(ReferenceType.MONEY);
        assertThat(result.referenceVault().requireMoney("MONEY_1")).isEqualByComparingTo("30000000.00");
    }

    @Test
    void normalizesCombinedEokAndManExpression() {
        FinancialTokenizationResult result = tokenizer.tokenize("1억 5천만원을 사용하면?");

        assertThat(result.sanitizedText()).isEqualTo("[MONEY_1]을 사용하면?");
        assertThat(result.referenceVault().requireMoney("MONEY_1")).isEqualByComparingTo("150000000.00");
    }

    @Test
    void distinguishesFiftyAndFiveHundredManWon() {
        FinancialTokenizationResult result = tokenizer.tokenize("50만원을 줄이고 500만원을 투자하면?");

        assertThat(result.sanitizedText()).isEqualTo("[MONEY_1]을 줄이고 [MONEY_2]을 투자하면?");
        assertThat(result.referenceVault().requireMoney("MONEY_1")).isEqualByComparingTo("500000.00");
        assertThat(result.referenceVault().requireMoney("MONEY_2")).isEqualByComparingTo("5000000.00");
    }

    @Test
    void handlesCommaSeparatedAndPlainWonAmounts() {
        FinancialTokenizationResult result = tokenizer.tokenize("30,000,000원과 1000원");

        assertThat(result.sanitizedText()).isEqualTo("[MONEY_1]과 [MONEY_2]");
        assertThat(result.referenceVault().requireMoney("MONEY_1")).isEqualByComparingTo("30000000.00");
        assertThat(result.referenceVault().requireMoney("MONEY_2")).isEqualByComparingTo("1000.00");
    }

    @Test
    void tokenizesDecimalPercentages() {
        FinancialTokenizationResult result = tokenizer.tokenize("금리가 2%, 2.5%, 약 5% 오르면?");

        assertThat(result.sanitizedText())
                .isEqualTo("금리가 [PERCENT_1], [PERCENT_2], 약 [PERCENT_3] 오르면?");
        assertThat(result.referenceVault().requirePercent("PERCENT_1")).isEqualByComparingTo("2.0000");
        assertThat(result.referenceVault().requirePercent("PERCENT_2")).isEqualByComparingTo("2.5000");
        assertThat(result.referenceVault().requirePercent("PERCENT_3")).isEqualByComparingTo("5.0000");
    }

    @Test
    void normalizesMonthAndYearDurations() {
        FinancialTokenizationResult result = tokenizer.tokenize("6개월 동안 줄이고 3년 동안 유지하면?");

        assertThat(result.sanitizedText())
                .isEqualTo("[DURATION_1] 동안 줄이고 [DURATION_2] 동안 유지하면?");
        assertThat(result.referenceVault().requireDurationMonths("DURATION_1")).isEqualTo(6);
        assertThat(result.referenceVault().requireDurationMonths("DURATION_2")).isEqualTo(36);
    }

    @Test
    void tokenizesKoreanAndIsoAbsoluteYearMonths() {
        FinancialTokenizationResult result = tokenizer.tokenize("2027년 3월부터 2027-04까지");

        assertThat(result.sanitizedText()).isEqualTo("[DATE_1]부터 [DATE_2]까지");
        assertThat(result.referenceVault().requireDate("DATE_1")).isEqualTo(YearMonth.of(2027, 3));
        assertThat(result.referenceVault().requireDate("DATE_2")).isEqualTo(YearMonth.of(2027, 4));
    }

    @Test
    void leavesRelativeDateExpressionsForIntentStructuring() {
        String source = "내년 또는 다음 달부터 3개월 뒤에 시작하면?";

        FinancialTokenizationResult result = tokenizer.tokenize(source);

        assertThat(result.sanitizedText()).isEqualTo(source);
        assertThat(result.references()).isEmpty();
        assertThat(result.referenceVault().size()).isZero();
    }

    @Test
    void absoluteDateWinsOverDurationOverlap() {
        FinancialTokenizationResult result = tokenizer.tokenize("2027년 3월");

        assertThat(result.sanitizedText()).isEqualTo("[DATE_1]");
        assertThat(result.references()).singleElement()
                .satisfies(reference -> assertThat(reference.referenceType()).isEqualTo(ReferenceType.DATE));
    }

    @Test
    void doesNotRetokenizeExistingReferenceText() {
        FinancialTokenizationResult result = tokenizer.tokenize("[MONEY_1]을 사용한다");

        assertThat(result.sanitizedText()).isEqualTo("[MONEY_1]을 사용한다");
        assertThat(result.references()).isEmpty();
    }

    @Test
    void assignsUniqueReferencesInStableSourceOrder() {
        String source = "20만원, 50만원, 2%, 6개월, 2027-03";

        FinancialTokenizationResult first = tokenizer.tokenize(source);
        FinancialTokenizationResult second = tokenizer.tokenize(source);

        assertThat(first.sanitizedText()).isEqualTo(
                "[MONEY_1], [MONEY_2], [PERCENT_1], [DURATION_1], [DATE_1]");
        assertThat(first.references()).isEqualTo(second.references());
        assertThat(first.sanitizedText()).isEqualTo(second.sanitizedText());
        assertThat(first.referenceVault().requireMoney("MONEY_1")).isEqualByComparingTo("200000.00");
        assertThat(first.referenceVault().requireMoney("MONEY_2")).isEqualByComparingTo("500000.00");
    }
}
