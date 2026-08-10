package com.fintwin.fintwin.auth.integration;

import com.fintwin.fintwin.auth.security.FinTwinSecurityTestSupport;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.OAuthIdentityRepository;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedUserBoundaryIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OAuthIdentityRepository identityRepository;
    @Autowired
    private FinancialProfileRepository profileRepository;
    @Autowired
    private FinancialProfileService profileService;

    private Long userA;
    private Long userB;
    private Long profileA;
    private Long profileB;

    @BeforeEach
    void setUpUsers() {
        identityRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
        userA = userRepository.saveAndFlush(User.create()).getId();
        userB = userRepository.saveAndFlush(User.create()).getId();
        profileA = profileService.create(userA, profile("3000.00")).id();
        profileService.updateCurrent(userA, update("3200.00"));
        profileB = profileService.create(userB, profile("1000.00")).id();
    }

    @Test
    void principalSeparatesCurrentProfileHistoryAndSnapshotOwnership() throws Exception {
        mockMvc.perform(get("/api/financial-profiles/current")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(3200.00));
        mockMvc.perform(get("/api/financial-profiles/current")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(1000.00));
        mockMvc.perform(get("/api/financial-profiles/history")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/financial-profiles/history")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/financial-profiles/{id}", profileA)
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/financial-profiles/{id}", profileB)
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void principalSeparatesSimulationGoalAndAgentExecutionWithoutExposingIdsInAgentResult() throws Exception {
        mockMvc.perform(post("/api/simulations/baseline")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(simulationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialProfileId").value(
                        profileService.getCurrent(userA).id()));
        mockMvc.perform(post("/api/simulations/baseline")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(simulationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialProfileId").value(profileB));

        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(goalRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialProfileId").value(
                        profileService.getCurrent(userA).id()));
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(goalRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialProfileId").value(profileB));

        String agentA = mockMvc.perform(post("/api/agent/execute")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(agentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCallCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String agentB = mockMvc.perform(post("/api/agent/execute")
                        .with(FinTwinSecurityTestSupport.fintwinUser(userB)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(agentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCallCount").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(agentA).isNotEqualTo(agentB).doesNotContain("userId", "profileId");
        assertThat(agentB).doesNotContain("userId", "profileId");
    }

    private String simulationRequest() {
        return """
                {"startYearMonth":"2026-08","horizonMonths":12,"assumptions":{
                  "annualIncomeGrowthRate":0,"annualInflationRate":0,
                  "annualDepositInterestRate":0,"annualInvestmentReturnRate":0,
                  "monthlyDebtPayment":0}}
                """;
    }

    private String goalRequest() {
        return """
                {"goalType":"TARGET_NET_WORTH","targetAmount":100000,
                 "startYearMonth":"2026-08","horizonMonths":12,"assumptions":{
                   "annualIncomeGrowthRate":0,"annualInflationRate":0,
                   "annualDepositInterestRate":0,"annualInvestmentReturnRate":0,
                   "monthlyDebtPayment":0}}
                """;
    }

    private String agentRequest() {
        return """
                {"intent":"BASELINE_SIMULATION","startYearMonth":"2026-08","horizonMonths":12,
                 "assumptions":{"annualIncomeGrowthRate":0,"annualInflationRate":0,
                   "annualDepositInterestRate":0,"annualInvestmentReturnRate":0,
                   "monthlyDebtPayment":0}}
                """;
    }

    private FinancialProfileCreateRequest profile(String income) {
        return new FinancialProfileCreateRequest(money(income), money("1000"), money("500"), money("200"),
                BigDecimal.ZERO, BigDecimal.ZERO, money("100"), money("50"), money("20"), money("30"));
    }

    private FinancialProfileUpdateRequest update(String income) {
        return new FinancialProfileUpdateRequest(money(income), money("1000"), money("500"), money("200"),
                BigDecimal.ZERO, BigDecimal.ZERO, money("100"), money("50"), money("20"), money("30"));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
