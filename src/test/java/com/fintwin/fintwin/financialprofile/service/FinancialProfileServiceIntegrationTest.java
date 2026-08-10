package com.fintwin.fintwin.financialprofile.service;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.global.error.ConflictException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FinancialProfileServiceIntegrationTest {
    @Autowired
    private FinancialProfileService service;
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
    void initialCreationStartsAtVersionOne() {
        Long userId = createUser();

        FinancialProfileResponse created = service.create(userId, createRequest("5000000.00"));

        assertThat(created.version()).isEqualTo(1);
        assertThat(created.previousProfileId()).isNull();
    }

    @Test
    void duplicateInitialCreationIsRejected() {
        Long userId = createUser();
        service.create(userId, createRequest("5000000.00"));

        assertThatThrownBy(() -> service.create(userId, createRequest("6000000.00")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Financial profile already exists");
    }

    @Test
    void updateCreatesNextVersionWithoutChangingPreviousSnapshot() {
        Long userId = createUser();
        FinancialProfileResponse versionOne = service.create(userId, createRequest("5000000.00"));

        FinancialProfileResponse versionTwo = service.updateCurrent(userId, updateRequest("6000000.00"));

        assertThat(versionTwo.version()).isEqualTo(2);
        assertThat(versionTwo.previousProfileId()).isEqualTo(versionOne.id());
        assertThat(versionTwo.monthlyIncome()).isEqualByComparingTo("6000000.00");

        FinancialProfileResponse unchangedVersionOne = service.getSnapshot(userId, versionOne.id());
        assertThat(unchangedVersionOne.version()).isEqualTo(1);
        assertThat(unchangedVersionOne.monthlyIncome()).isEqualByComparingTo("5000000.00");
    }

    @Test
    void currentAndHistoryUseDescendingVersionOrder() {
        Long userId = createUser();
        service.create(userId, createRequest("5000000.00"));
        FinancialProfileResponse versionTwo = service.updateCurrent(userId, updateRequest("6000000.00"));

        FinancialProfileResponse current = service.getCurrent(userId);
        List<FinancialProfileResponse> history = service.getHistory(userId);

        assertThat(current.id()).isEqualTo(versionTwo.id());
        assertThat(current.version()).isEqualTo(2);
        assertThat(history).extracting(FinancialProfileResponse::version).containsExactly(2, 1);
    }

    @Test
    void missingCurrentProfileIsRejected() {
        Long userId = createUser();

        assertThatThrownBy(() -> service.getCurrent(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial profile not found");
    }

    @Test
    void snapshotOwnedByAnotherUserIsHidden() {
        Long userOneId = createUser();
        Long userTwoId = createUser();
        FinancialProfileResponse userOneProfile = service.create(userOneId, createRequest("5000000.00"));

        assertThatThrownBy(() -> service.getSnapshot(userTwoId, userOneProfile.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial profile not found");
    }

    @Test
    void databaseRejectsDuplicateUserVersion() {
        User user = userRepository.save(User.create());
        financialProfileRepository.saveAndFlush(FinancialProfile.createInitial(user, createRequest("5000000.00")));

        assertThatThrownBy(() -> financialProfileRepository.saveAndFlush(
                FinancialProfile.createInitial(user, createRequest("6000000.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long createUser() {
        return userRepository.saveAndFlush(User.create()).getId();
    }

    private FinancialProfileCreateRequest createRequest(String monthlyIncome) {
        return new FinancialProfileCreateRequest(
                new BigDecimal(monthlyIncome), new BigDecimal("3000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("8000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("4.2500"),
                new BigDecimal("1500000.00"), new BigDecimal("800000.00"),
                new BigDecimal("700000.00"), new BigDecimal("500000.00"));
    }

    private FinancialProfileUpdateRequest updateRequest(String monthlyIncome) {
        return new FinancialProfileUpdateRequest(
                new BigDecimal(monthlyIncome), new BigDecimal("3000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("8000000.00"),
                new BigDecimal("10000000.00"), new BigDecimal("4.2500"),
                new BigDecimal("1500000.00"), new BigDecimal("800000.00"),
                new BigDecimal("700000.00"), new BigDecimal("500000.00"));
    }
}
