package com.fintwin.fintwin.financialprofile.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/financial-profiles")
public class FinancialProfileController {
    private final FinancialProfileService financialProfileService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public FinancialProfileController(FinancialProfileService financialProfileService,
                                      CurrentUserIdProvider currentUserIdProvider) {
        this.financialProfileService = financialProfileService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialProfileResponse create(@Valid @RequestBody FinancialProfileCreateRequest request) {
        return financialProfileService.create(currentUserIdProvider.currentUserId(), request);
    }

    @GetMapping({"/current", "/me"})
    public FinancialProfileResponse getCurrent() {
        return financialProfileService.getCurrent(currentUserIdProvider.currentUserId());
    }

    @PutMapping("/current")
    public FinancialProfileResponse updateCurrent(@Valid @RequestBody FinancialProfileUpdateRequest request) {
        return financialProfileService.updateCurrent(currentUserIdProvider.currentUserId(), request);
    }

    @GetMapping("/history")
    public List<FinancialProfileResponse> getHistory() {
        return financialProfileService.getHistory(currentUserIdProvider.currentUserId());
    }

    @GetMapping("/{profileId}")
    public FinancialProfileResponse getSnapshot(@PathVariable Long profileId) {
        return financialProfileService.getSnapshot(currentUserIdProvider.currentUserId(), profileId);
    }
}
