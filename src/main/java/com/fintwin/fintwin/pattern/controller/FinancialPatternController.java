package com.fintwin.fintwin.pattern.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.pattern.dto.FinancialPatternAnalysisResponse;
import com.fintwin.fintwin.pattern.service.FinancialPatternAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/patterns")
public class FinancialPatternController {
    private final FinancialPatternAnalysisService analysisService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public FinancialPatternController(FinancialPatternAnalysisService analysisService,
                                      CurrentUserIdProvider currentUserIdProvider) {
        this.analysisService = analysisService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping(value = "/analyze-csv", consumes = "multipart/form-data")
    public FinancialPatternAnalysisResponse analyzeCsv(@RequestPart("file") MultipartFile file) {
        return analysisService.analyze(currentUserIdProvider.currentUserId(), file);
    }
}
