package com.fintwin.fintwin.privacy.controller;

import com.fintwin.fintwin.privacy.dto.PrivacyPayloadPreviewRequest;
import com.fintwin.fintwin.privacy.dto.PrivacyPayloadPreviewResponse;
import com.fintwin.fintwin.privacy.service.PrivacyBoundaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyBoundaryController {
    private final PrivacyBoundaryService privacyBoundaryService;

    public PrivacyBoundaryController(PrivacyBoundaryService privacyBoundaryService) {
        this.privacyBoundaryService = privacyBoundaryService;
    }

    @PostMapping("/scenario-payload-preview")
    public PrivacyPayloadPreviewResponse preview(@Valid @RequestBody PrivacyPayloadPreviewRequest request) {
        return PrivacyPayloadPreviewResponse.from(privacyBoundaryService.createPreview(request.scenarioText()));
    }
}
