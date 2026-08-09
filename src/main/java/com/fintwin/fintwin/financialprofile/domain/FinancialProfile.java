package com.fintwin.fintwin.financialprofile.domain;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "financial_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncome;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashAssets;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deposits;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal investmentAssets;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalLoanBalance;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal loanInterestRate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyFixedExpenses;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyVariableExpenses;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlySavings;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyInvestments;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private FinancialProfile(User user, FinancialProfileCreateRequest request) {
        this.user = user;
        this.monthlyIncome = request.monthlyIncome();
        this.cashAssets = request.cashAssets();
        this.deposits = request.deposits();
        this.investmentAssets = request.investmentAssets();
        this.totalLoanBalance = request.totalLoanBalance();
        this.loanInterestRate = request.loanInterestRate();
        this.monthlyFixedExpenses = request.monthlyFixedExpenses();
        this.monthlyVariableExpenses = request.monthlyVariableExpenses();
        this.monthlySavings = request.monthlySavings();
        this.monthlyInvestments = request.monthlyInvestments();
    }

    public static FinancialProfile create(User user, FinancialProfileCreateRequest request) {
        return new FinancialProfile(user, request);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
