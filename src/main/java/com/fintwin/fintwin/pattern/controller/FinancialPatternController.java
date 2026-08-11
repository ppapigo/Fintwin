package com.fintwin.fintwin.pattern.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.pattern.dto.FinancialPatternAnalysisResponse;
import com.fintwin.fintwin.pattern.service.FinancialPatternAnalysisService;
import com.fintwin.fintwin.pattern.service.TransactionXlsxTemplateService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final TransactionXlsxTemplateService templateService;

    public FinancialPatternController(FinancialPatternAnalysisService analysisService,
                                      CurrentUserIdProvider currentUserIdProvider,
                                      TransactionXlsxTemplateService templateService) {
        this.analysisService = analysisService;
        this.currentUserIdProvider = currentUserIdProvider;
        this.templateService = templateService;
    }

    @PostMapping(value = "/analyze-csv", consumes = "multipart/form-data")
    public FinancialPatternAnalysisResponse analyzeCsv(@RequestPart("file") MultipartFile file) {
        return analysisService.analyze(currentUserIdProvider.currentUserId(), file);
    }

    @PostMapping(value = "/analyze-xlsx", consumes = "multipart/form-data")
    public FinancialPatternAnalysisResponse analyzeXlsx(@RequestPart("file") MultipartFile file) {
        return analysisService.analyzeXlsx(currentUserIdProvider.currentUserId(), file);
    }

    @GetMapping("/xlsx-template")
    public ResponseEntity<byte[]> downloadXlsxTemplate() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("fintwin-transactions-template.xlsx")
                .build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(templateService.createTemplate());
    }
}
