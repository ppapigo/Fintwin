package com.fintwin.fintwin.pattern.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.pattern.dto.FinancialPatternAnalysisResponse;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FinancialPatternAnalysisServiceIntegrationTest {
    @Autowired
    private FinancialPatternAnalysisService analysisService;
    @Autowired
    private FinancialProfileService financialProfileService;
    @Autowired
    private FinancialProfileRepository financialProfileRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void returnsDraftWithoutCreatingProfileWhenCurrentProfileDoesNotExist() {
        FinancialPatternAnalysisResponse response = analysisService.analyze(1L, validCsv());

        assertThat(response.currentProfileComparison()).isNull();
        assertThat(response.profileDraft().preservedExistingFields()).isNull();
        assertThat(response.profileDraft().notInferredFields()).contains(
                "cashAssets", "deposits", "investmentAssets", "totalLoanBalance",
                "loanInterestRate", "financialGoals");
        assertThat(response.profileDraft().userConfirmationRequired()).isTrue();
        assertThat(response.profileDraft().estimatedValues().monthlyIncome()).isEqualByComparingTo("3000.00");
        assertThat(response.profileDraft().estimatedValues().monthlyFixedExpenses())
                .isEqualByComparingTo("1000.00");
        assertThat(response.profileDraft().estimatedValues().monthlyVariableExpenses())
                .isEqualByComparingTo("0.00");
        assertThat(response.profileDraft().estimatedValues().monthlySavings()).isEqualByComparingTo("500.00");
        assertThat(response.profileDraft().estimatedValues().monthlyInvestment()).isEqualByComparingTo("300.00");
        assertThat(financialProfileRepository.count()).isZero();
    }

    @Test
    void comparesDraftWithLatestProfileVersionWithoutMutatingSnapshots() {
        Long userId = userRepository.saveAndFlush(User.create()).getId();
        financialProfileService.create(userId, createRequest());
        FinancialProfileResponse latest = financialProfileService.updateCurrent(userId, updateRequest());
        long profileCountBefore = financialProfileRepository.count();

        FinancialPatternAnalysisResponse response = analysisService.analyze(userId, validCsv());

        var comparison = response.currentProfileComparison();
        assertThat(comparison.financialProfileId()).isEqualTo(latest.id());
        assertThat(comparison.financialProfileVersion()).isEqualTo(2);
        assertThat(comparison.deltaDirection()).isEqualTo("draft - currentProfile");
        assertThat(comparison.deltas().monthlyIncome()).isEqualByComparingTo("200.00");
        assertThat(comparison.deltas().monthlyFixedExpenses()).isEqualByComparingTo("200.00");
        assertThat(comparison.deltas().monthlyVariableExpenses()).isEqualByComparingTo("-200.00");
        assertThat(comparison.deltas().monthlySavings()).isEqualByComparingTo("50.00");
        assertThat(comparison.deltas().monthlyInvestment()).isEqualByComparingTo("50.00");
        assertThat(response.profileDraft().preservedExistingFields().cashAssets())
                .isEqualByComparingTo("3100.00");
        assertThat(financialProfileRepository.count()).isEqualTo(profileCountBefore);
        assertThat(financialProfileService.getCurrent(userId).id()).isEqualTo(latest.id());
        assertThat(financialProfileService.getHistory(userId)).hasSize(2);
    }

    @Test
    void validatesEmptyFileAndExtensionBeforeParsing() {
        MockMultipartFile empty = new MockMultipartFile("file", "synthetic.csv", "text/csv", new byte[0]);
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "synthetic.txt", "text/csv",
                validCsvText().getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> analysisService.analyze(1L, empty))
                .isInstanceOfSatisfying(CsvValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CSV_EMPTY_FILE"));
        assertThatThrownBy(() -> analysisService.analyze(1L, wrongExtension))
                .isInstanceOfSatisfying(CsvValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CSV_INVALID_EXTENSION"));
    }

    private MockMultipartFile validCsv() {
        return new MockMultipartFile("file", "synthetic.csv", "application/octet-stream",
                validCsvText().getBytes(StandardCharsets.UTF_8));
    }

    private String validCsvText() {
        return """
                transactionDate,type,amount,category,description,transactionId
                2026-01-01,INCOME,3000,SALARY,Synthetic Salary,synthetic-001
                2026-01-02,EXPENSE,1000,HOUSING,Synthetic Rent,synthetic-002
                2026-01-03,SAVING_TRANSFER,500,SAVINGS,Synthetic Saving,synthetic-003
                2026-01-04,INVESTMENT_TRANSFER,300,INVESTMENT,Synthetic Investment,synthetic-004
                2026-02-01,INCOME,3000,SALARY,Synthetic Salary,synthetic-005
                2026-02-02,EXPENSE,1000,HOUSING,Synthetic Rent,synthetic-006
                2026-02-03,SAVING_TRANSFER,500,SAVINGS,Synthetic Saving,synthetic-007
                2026-02-04,INVESTMENT_TRANSFER,300,INVESTMENT,Synthetic Investment,synthetic-008
                2026-03-01,INCOME,3000,SALARY,Synthetic Salary,synthetic-009
                2026-03-02,EXPENSE,1000,HOUSING,Synthetic Rent,synthetic-010
                2026-03-03,SAVING_TRANSFER,500,SAVINGS,Synthetic Saving,synthetic-011
                2026-03-04,INVESTMENT_TRANSFER,300,INVESTMENT,Synthetic Investment,synthetic-012
                """;
    }

    private FinancialProfileCreateRequest createRequest() {
        return new FinancialProfileCreateRequest(
                money("2500.00"), money("3000.00"), money("10000.00"), money("8000.00"),
                money("10000.00"), new BigDecimal("4.2500"), money("900.00"), money("100.00"),
                money("400.00"), money("200.00"));
    }

    private FinancialProfileUpdateRequest updateRequest() {
        return new FinancialProfileUpdateRequest(
                money("2800.00"), money("3100.00"), money("11000.00"), money("8500.00"),
                money("9000.00"), new BigDecimal("4.0000"), money("800.00"), money("200.00"),
                money("450.00"), money("250.00"));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
