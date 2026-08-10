package com.fintwin.fintwin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

}
