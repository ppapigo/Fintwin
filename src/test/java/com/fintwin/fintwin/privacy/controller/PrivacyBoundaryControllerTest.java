package com.fintwin.fintwin.privacy.controller;

import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import com.fintwin.fintwin.privacy.token.FinancialValueTokenizer;
import com.fintwin.fintwin.privacy.token.KoreanMoneyParser;
import com.fintwin.fintwin.privacy.validation.OutboundPayloadGuard;
import com.fintwin.fintwin.privacy.validation.PersonalIdentifierDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrivacyBoundaryControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PersonalIdentifierDetector detector = new PersonalIdentifierDetector();
        FinancialValueTokenizer tokenizer = new FinancialValueTokenizer(new KoreanMoneyParser());
        PrivacyBoundaryService service = new PrivacyBoundaryService(detector, tokenizer,
                new OutboundPayloadGuard(detector, tokenizer),
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new PrivacyBoundaryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsSafePreviewWithoutExactValueVaultOrOriginalEcho() throws Exception {
        String original = "내년에 3천만원짜리 자동차를 사면?";

        MvcResult result = mockMvc.perform(post("/api/privacy/scenario-payload-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"scenarioText\":\"" + original + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAFE"))
                .andExpect(jsonPath("$.externalPayload.schemaVersion").value("1"))
                .andExpect(jsonPath("$.externalPayload.currentYearMonth").value("2026-08"))
                .andExpect(jsonPath("$.externalPayload.sanitizedScenarioText")
                        .value("내년에 [MONEY_1]짜리 자동차를 사면?"))
                .andExpect(jsonPath("$.references[0].referenceId").value("MONEY_1"))
                .andExpect(jsonPath("$.references[0].referenceType").value("MONEY"))
                .andExpect(jsonPath("$.blockedIdentifierTypes").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(original, "3천만원", "30000000", "referenceVault");
    }

    @Test
    void returnsBlockedPreviewWithoutPayloadOrSensitiveEcho() throws Exception {
        String phone = "010-1234-5678";

        MvcResult result = mockMvc.perform(post("/api/privacy/scenario-payload-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"scenarioText\":\"연락처 " + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.externalPayload").doesNotExist())
                .andExpect(jsonPath("$.references").isEmpty())
                .andExpect(jsonPath("$.blockedIdentifierTypes[0]").value("PHONE_NUMBER"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(phone, "연락처");
    }

    @Test
    void rejectsBlankInputWithCommonValidationResponse() throws Exception {
        mockMvc.perform(post("/api/privacy/scenario-payload-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scenarioText"));
    }
}
