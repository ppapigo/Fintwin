package com.fintwin.fintwin;

import com.fintwin.fintwin.pattern.support.XlsxFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.fintwin.fintwin.auth.security.FinTwinSecurityTestSupport.fintwinUser;
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
	void agentApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/agent/execute")
					.contentType("application/json")
					.content("{\"intent\":\"BASELINE_SIMULATION\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void naturalLanguageAgentApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/agent/natural-language")
					.contentType("application/json")
					.content(validNaturalLanguageRequest()))
				.andExpect(status().isForbidden());
	}

	@Test
	void naturalLanguageAgentFailsClosedWhenAiIsDisabled() throws Exception {
		mockMvc.perform(post("/api/agent/natural-language")
					.with(fintwinUser(1L))
					.with(csrf())
					.contentType("application/json")
					.content(validNaturalLanguageRequest()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_DISABLED"));
	}

	@Test
	void agentReturnsNeedsInputAsAValidStructuredResponse() throws Exception {
		mockMvc.perform(post("/api/agent/execute")
					.with(fintwinUser(1L))
					.with(csrf())
					.contentType("application/json")
					.content("{\"intent\":\"BASELINE_SIMULATION\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
				.andExpect(jsonPath("$.selectedTool").value("BASELINE_SIMULATION_TOOL"))
				.andExpect(jsonPath("$.toolCallCount").value(0))
				.andExpect(jsonPath("$.typedResult").doesNotExist())
				.andExpect(jsonPath("$.missingInformation[0].code")
						.value("START_YEAR_MONTH_REQUIRED"));
	}

	@Test
	void malformedAgentRequestUsesCommonBadRequestResponse() throws Exception {
		mockMvc.perform(post("/api/agent/execute")
					.with(fintwinUser(1L))
					.with(csrf())
					.contentType("application/json")
					.content("{\"intent\":\"BASELINE_SIMULATION\",\"startYearMonth\":\"invalid\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void patternAnalysisApiRequiresAuthentication() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv",
				"synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file))
				.andExpect(status().isForbidden());
	}

	@Test
	void xlsxPatternAnalysisRequiresAuthenticationAndCsrf() throws Exception {
		MockMultipartFile file = validXlsxFile();

		mockMvc.perform(multipart("/api/patterns/analyze-xlsx").file(file))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/patterns/analyze-xlsx").file(validXlsxFile())
					.with(fintwinUser(1L)))
				.andExpect(status().isForbidden());
	}

	@Test
	void authenticatedUserCanAnalyzeCsvWithoutExistingProfile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv", """
				transactionDate,type,amount,category,description
				2026-01-01,INCOME,1000,SALARY,Synthetic Salary
				""".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file)
					.with(fintwinUser(1L)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.algorithmVersion").value("fintwin-pattern-v1"))
				.andExpect(jsonPath("$.transactionCount").value(1))
				.andExpect(jsonPath("$.privacyNotice.externalTransfer")
						.value("Transaction data is not sent to an external AI or external API."));
	}

	@Test
	void authenticatedUserCanAnalyzeXlsxWithoutExistingProfile() throws Exception {
		mockMvc.perform(multipart("/api/patterns/analyze-xlsx").file(validXlsxFile())
					.with(fintwinUser(1L)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.algorithmVersion").value("fintwin-pattern-v1"))
				.andExpect(jsonPath("$.transactionCount").value(1))
				.andExpect(jsonPath("$.privacyNotice.storage")
						.value("The original uploaded file, normalized transactions, and analysis result are not stored in a database or file system."));
	}

	@Test
	void privacyPreviewApiRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/privacy/scenario-payload-preview")
					.contentType("application/json")
					.content("{\"scenarioText\":\"내년에 3천만원을 쓰면?\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void authenticatedUserCanPreviewPrivacySafePayload() throws Exception {
		mockMvc.perform(post("/api/privacy/scenario-payload-preview")
					.with(fintwinUser(1L))
					.with(csrf())
					.contentType("application/json")
					.content("{\"scenarioText\":\"내년에 3천만원을 쓰면?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAFE"))
				.andExpect(jsonPath("$.externalPayload.sanitizedScenarioText")
						.value("내년에 [MONEY_1]을 쓰면?"));
	}

	private String validNaturalLanguageRequest() {
		return """
				{
				  "scenarioText": "내년에 3천만원 자동차를 사면?",
				  "startYearMonth": "2026-08",
				  "horizonMonths": 36,
				  "assumptions": {
				    "annualIncomeGrowthRate": 2.0,
				    "annualInflationRate": 2.0,
				    "annualDepositInterestRate": 2.5,
				    "annualInvestmentReturnRate": 4.0,
				    "monthlyDebtPayment": 300000
				  }
				}
				""";
	}

	private MockMultipartFile validXlsxFile() {
		return new MockMultipartFile("file", "synthetic.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				XlsxFixtures.workbook(List.of(List.of(
						"2026-01-01", "INCOME", "1000", "SALARY", "Synthetic Salary", "synthetic-1"))));
	}

}
