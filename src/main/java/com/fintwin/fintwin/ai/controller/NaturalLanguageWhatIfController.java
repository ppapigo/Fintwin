package com.fintwin.fintwin.ai.controller;

import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfRequest;
import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfResponse;
import com.fintwin.fintwin.ai.service.NaturalLanguageWhatIfService;
import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public final class NaturalLanguageWhatIfController {
    private final NaturalLanguageWhatIfService service;
    private final CurrentUserIdProvider currentUserIdProvider;

    public NaturalLanguageWhatIfController(NaturalLanguageWhatIfService service,
                                           CurrentUserIdProvider currentUserIdProvider) {
        this.service = service;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping("/natural-language")
    public NaturalLanguageWhatIfResponse execute(@Valid @RequestBody NaturalLanguageWhatIfRequest request) {
        return service.execute(currentUserIdProvider.currentUserId(), request);
    }
}
