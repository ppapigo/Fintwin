package com.fintwin.fintwin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FintwinApplicationTests {

	private final MockMvc mockMvc;

	@Autowired
	FintwinApplicationTests(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void contextLoads() {
	}

	@Test
	void actuatorHealthIsAvailable() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void financialApiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/financial-profiles/current"))
				.andExpect(status().isForbidden());
	}

	@Test
	void simulationApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/simulations/baseline")
					.contentType("application/json")
					.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void scenarioComparisonApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/simulations/compare")
					.contentType("application/json")
					.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void goalReverseSimulationApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/goals/reverse-simulate")
					.contentType("application/json")
					.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void patternAnalysisApiRequiresAuthentication() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv",
				"synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void authenticatedUserCanAnalyzeCsvWithoutExistingProfile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv", """
				transactionDate,type,amount,category,description
				2026-01-01,INCOME,1000,SALARY,Synthetic Salary
				""".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.algorithmVersion").value("fintwin-pattern-v1"))
				.andExpect(jsonPath("$.transactionCount").value(1))
				.andExpect(jsonPath("$.privacyNotice.externalTransfer")
						.value("Transaction data is not sent to an external AI or external API."));
	}

	@Test
	void privacyPreviewApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/privacy/scenario-payload-preview")
					.contentType("application/json")
					.content("{\"scenarioText\":\"내년에 3천만원을 쓰면?\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void authenticatedUserCanPreviewPrivacySafePayload() throws Exception {
		mockMvc.perform(post("/api/privacy/scenario-payload-preview")
					.with(csrf())
					.contentType("application/json")
					.content("{\"scenarioText\":\"내년에 3천만원을 쓰면?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAFE"))
				.andExpect(jsonPath("$.externalPayload.sanitizedScenarioText")
						.value("내년에 [MONEY_1]을 쓰면?"));
	}

}
