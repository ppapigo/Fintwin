package com.fintwin.fintwin.financialprofile.service;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialProfileServiceTest {
    @Mock
    private FinancialProfileRepository financialProfileRepository;
    @Mock
    private UserRepository userRepository;
    private FinancialProfileService service;

    @BeforeEach
    void setUp() {
        service = new FinancialProfileService(financialProfileRepository, userRepository);
    }

    @Test
    void createsFinancialProfile() {
        User user = new User(1L);
        FinancialProfileCreateRequest request = validRequest();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(financialProfileRepository.existsByUserId(1L)).thenReturn(false);
        when(financialProfileRepository.saveAndFlush(any(FinancialProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FinancialProfileResponse response = service.create(1L, request);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.previousProfileId()).isNull();
        assertThat(response.monthlyIncome()).isEqualByComparingTo("5000000.00");
        assertThat(response.totalLoanBalance()).isEqualByComparingTo("10000000.00");
    }

    @Test
    void rejectsMissingProfile() {
        when(financialProfileRepository.findFirstByUserIdOrderByVersionDesc(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial profile not found");
    }

    static FinancialProfileCreateRequest validRequest() {
        return new FinancialProfileCreateRequest(
                new BigDecimal("5000000.00"), new BigDecimal("3000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("8000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("4.2500"),
                new BigDecimal("1500000.00"), new BigDecimal("800000.00"),
                new BigDecimal("700000.00"), new BigDecimal("500000.00"));
    }
}
