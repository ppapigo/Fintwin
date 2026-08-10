package com.fintwin.fintwin.financialprofile.domain;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Immutable
@Table(name = "financial_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_financial_profiles_user_version", columnNames = {"user_id", "profile_version"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "profile_version", nullable = false, updatable = false)
    private int version;

    @Column(name = "previous_profile_id", updatable = false)
    private Long previousProfileId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal monthlyIncome;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal cashAssets;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal deposits;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal investmentAssets;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalLoanBalance;
    @Column(nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal loanInterestRate;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal monthlyFixedExpenses;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal monthlyVariableExpenses;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal monthlySavings;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal monthlyInvestments;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private FinancialProfile(User user, int version, Long previousProfileId,
                             BigDecimal monthlyIncome, BigDecimal cashAssets, BigDecimal deposits,
                             BigDecimal investmentAssets, BigDecimal totalLoanBalance, BigDecimal loanInterestRate,
                             BigDecimal monthlyFixedExpenses, BigDecimal monthlyVariableExpenses,
                             BigDecimal monthlySavings, BigDecimal monthlyInvestments) {
        this.user = user;
        this.version = version;
        this.previousProfileId = previousProfileId;
        this.monthlyIncome = monthlyIncome;
        this.cashAssets = cashAssets;
        this.deposits = deposits;
        this.investmentAssets = investmentAssets;
        this.totalLoanBalance = totalLoanBalance;
        this.loanInterestRate = loanInterestRate;
        this.monthlyFixedExpenses = monthlyFixedExpenses;
        this.monthlyVariableExpenses = monthlyVariableExpenses;
        this.monthlySavings = monthlySavings;
        this.monthlyInvestments = monthlyInvestments;
    }

    public static FinancialProfile createInitial(User user, FinancialProfileCreateRequest request) {
        return new FinancialProfile(user, 1, null, request.monthlyIncome(), request.cashAssets(), request.deposits(),
                request.investmentAssets(), request.totalLoanBalance(), request.loanInterestRate(),
                request.monthlyFixedExpenses(), request.monthlyVariableExpenses(), request.monthlySavings(),
                request.monthlyInvestments());
    }

    public static FinancialProfile createNext(FinancialProfile previous, FinancialProfileUpdateRequest request) {
        if (previous.id == null) {
            throw new IllegalArgumentException("Previous financial profile must be persisted");
        }
        return new FinancialProfile(previous.user, Math.addExact(previous.version, 1), previous.id,
                request.monthlyIncome(), request.cashAssets(), request.deposits(), request.investmentAssets(),
                request.totalLoanBalance(), request.loanInterestRate(), request.monthlyFixedExpenses(),
                request.monthlyVariableExpenses(), request.monthlySavings(), request.monthlyInvestments());
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
