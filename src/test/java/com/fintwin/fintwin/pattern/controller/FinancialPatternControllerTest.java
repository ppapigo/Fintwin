package com.fintwin.fintwin.pattern.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.pattern.service.FinancialPatternAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialPatternControllerTest {
    private FinancialPatternAnalysisService analysisService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analysisService = mock(FinancialPatternAnalysisService.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(42L);
        FinancialPatternController controller = new FinancialPatternController(analysisService,
                currentUserIdProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsMultipartFileAndUsesCurrentUserId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv",
                "header".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file))
                .andExpect(status().isOk());

        verify(analysisService).analyze(eq(42L), any());
    }

    @Test
    void returnsStructuredCsvErrorWithoutRawValues() throws Exception {
        String rawValue = "PRIVATE_SYNTHETIC_DESCRIPTION";
        MockMultipartFile file = new MockMultipartFile("file", "synthetic.csv", "text/csv",
                rawValue.getBytes(StandardCharsets.UTF_8));
        when(analysisService.analyze(eq(42L), any())).thenThrow(new CsvValidationException(
                "CSV_INVALID_AMOUNT", 7, "amount", "Transaction amount must be a decimal number"));

        MvcResult result = mockMvc.perform(multipart("/api/patterns/analyze-csv").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CSV_INVALID_AMOUNT"))
                .andExpect(jsonPath("$.message").value("CSV validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))
                .andExpect(jsonPath("$.fieldErrors[0].rowNumber").value(7))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Transaction amount must be a decimal number"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawValue);
    }
}
